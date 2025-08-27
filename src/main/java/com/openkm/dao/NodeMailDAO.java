/**
 * OpenKM, Open Document Management System (http://www.openkm.com)
 * Copyright (c) 2006-2017 Paco Avila & Josep Llort
 * <p>
 * No bytes were intentionally harmed during the development of this application.
 * <p>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.openkm.dao;

import com.openkm.core.*;
import com.openkm.dao.bean.NodeBase;
import com.openkm.dao.bean.NodeFolder;
import com.openkm.dao.bean.NodeMail;
import com.openkm.extension.dao.ForumDAO;
import com.openkm.extension.dao.StapleGroupDAO;
import com.openkm.extension.dao.WikiPageDAO;
import com.openkm.module.db.stuff.SecurityHelper;
import com.openkm.spring.PrincipalUtils;
import com.openkm.util.FormatUtil;
import com.openkm.util.SystemProfiling;
import com.openkm.util.UserActivity;
import org.hibernate.*;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.hibernate.type.StandardBasicTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NodeMailDAO {
	private static Logger log = LoggerFactory.getLogger(NodeMailDAO.class);
	private static NodeMailDAO single = new NodeMailDAO();
	private static final String CACHE_MAILS_BY_CATEGORY = "com.openkm.cache.mailsByCategory";
	private static final String CACHE_MAILS_BY_KEYWORD = "com.openkm.cache.mailsByKeyword";

	private NodeMailDAO() {
	}

	public static NodeMailDAO getInstance() {
		return single;
	}

	/**
	 * Create node
	 */
	public synchronized void create(NodeMail nMail) throws PathNotFoundException, AccessDeniedException, ItemExistsException,
			DatabaseException {
		log.debug("create({})", nMail);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Security Check
			NodeBase parentNode = session.get(NodeBase.class, nMail.getParent());
			SecurityHelper.checkRead(parentNode);
			SecurityHelper.checkWrite(parentNode);

			// Check for same mail name in same parent
			NodeBaseDAO.getInstance().checkItemExistence(session, nMail.getParent(), nMail.getName());

			// Need to replace 0x00 because PostgreSQL does not accept string containing 0x00
			nMail.setContent(FormatUtil.fixUTF8(nMail.getContent()));

			// Need to remove Unicode surrogate because of MySQL => SQL Error: 1366, SQLState: HY000
			nMail.setContent(FormatUtil.trimUnicodeSurrogates(nMail.getContent()));

			session.persist(nMail);
			HibernateUtil.commit(tx);
			log.debug("create: void");
		} catch (PathNotFoundException | AccessDeniedException | ItemExistsException | DatabaseException e) {
			HibernateUtil.rollback(tx);
			throw e;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Update node
	 */
	public void update(NodeMail nMail) throws PathNotFoundException, AccessDeniedException, DatabaseException {
		log.debug("update({})", nMail);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Security Check
			SecurityHelper.checkRead(nMail);
			SecurityHelper.checkWrite(nMail);

			session.merge(nMail);
			HibernateUtil.commit(tx);
			log.debug("create: void");
		} catch (PathNotFoundException | AccessDeniedException | DatabaseException e) {
			HibernateUtil.rollback(tx);
			throw e;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find by parent
	 */
	
	public List<NodeMail> findByParent(String parentUuid) throws PathNotFoundException, DatabaseException {
		log.debug("findByParent({})", parentUuid);
		String qs = "from NodeMail nm where nm.parent=:parent order by nm.name";
		Session session = null;
		Transaction tx = null;

		try {
			long begin = System.currentTimeMillis();
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Security Check
			if (!Config.ROOT_NODE_UUID.equals(parentUuid)) {
				NodeBase parentNode = session.get(NodeBase.class, parentUuid);
				SecurityHelper.checkRead(parentNode);
			}

			Query<NodeMail> q = session.createQuery(qs, NodeMail.class).setCacheable(true);
			q.setParameter("parent", parentUuid);
			List<NodeMail> ret = q.getResultList();

			// Security Check
			SecurityHelper.pruneNodeList(ret);

			initialize(ret);
			HibernateUtil.commit(tx);

			SystemProfiling.log(parentUuid, System.currentTimeMillis() - begin);
			log.trace("findByParent.Time: {}", System.currentTimeMillis() - begin);
			log.debug("findByParent: {}", ret);
			return ret;
		} catch (PathNotFoundException | DatabaseException e) {
			HibernateUtil.rollback(tx);
			throw e;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find by pk
	 */
	public NodeMail findByPk(String uuid) throws DatabaseException, PathNotFoundException {
		return findByPk(uuid, false);
	}

	/**
	 * Find by pk and optionally initialize node property groups
	 */
	public NodeMail findByPk(String uuid, boolean initPropGroups) throws PathNotFoundException, DatabaseException {
		log.debug("findByPk({}, {})", uuid, initPropGroups);
		String qs = "from NodeMail nm where nm.uuid=:uuid";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<NodeMail> q = session.createQuery(qs, NodeMail.class);
			q.setParameter("uuid", uuid);
			NodeMail nMail = q.setMaxResults(1).uniqueResult();

			if (nMail == null) {
				throw new PathNotFoundException(uuid);
			}

			// Security Check
			SecurityHelper.checkRead(nMail);

			initialize(nMail, initPropGroups);
			log.debug("findByPk: {}", nMail);
			return nMail;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Check if this uuid represents a mail node.
	 * <p>
	 * Used in SearchDAO, and should exposed in other method should make Security Check
	 */
	public boolean isMail(Session session, String uuid) throws HibernateException {
		log.debug("isMail({}, {})", session, uuid);
		boolean ret = session.get(NodeMail.class, uuid) instanceof NodeMail;
		log.debug("isMail: {}", ret);
		return ret;
	}

	/**
	 * Search nodes by category
	 */
	
	public List<NodeMail> findByCategory(String catUuid) throws PathNotFoundException, DatabaseException {
		log.debug("findByCategory({})", catUuid);
		long begin = System.currentTimeMillis();
		final String qs = "from NodeMail nm where :category in elements(nm.categories) order by nm.name";
		final String sql = "select NBS_UUID from OKM_NODE_CATEGORY, OKM_NODE_MAIL " +
				"where NCT_CATEGORY = :catUuid and NCT_NODE = NBS_UUID";
		List<NodeMail> ret = new ArrayList<>();
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Security Check
			NodeBase catNode = session.get(NodeBase.class, catUuid);
			SecurityHelper.checkRead(catNode);

			if (Config.NATIVE_SQL_OPTIMIZATIONS) {
				NativeQuery<String> q = session.createNativeQuery(sql, String.class);
				q.setCacheable(true);
				q.setCacheRegion(CACHE_MAILS_BY_CATEGORY);
				q.setParameter("catUuid", catUuid);
				q.addScalar("NBS_UUID", StandardBasicTypes.STRING);

				for (String uuid : q.getResultList()) {
					NodeMail nMail = session.get(NodeMail.class, uuid);
					ret.add(nMail);
				}
			} else {
				Query<NodeMail> q = session.createQuery(qs, NodeMail.class).setCacheable(true);
				q.setParameter("category", catUuid);
				ret = q.getResultList();
			}

			// Security Check
			SecurityHelper.pruneNodeList(ret);

			initialize(ret);
			HibernateUtil.commit(tx);
			SystemProfiling.log(catUuid, System.currentTimeMillis() - begin);
			log.trace("findByCategory.Time: {}", System.currentTimeMillis() - begin);
			log.debug("findByCategory: {}", ret);
			return ret;
		} catch (PathNotFoundException | DatabaseException e) {
			HibernateUtil.rollback(tx);
			throw e;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Search nodes by keyword
	 */
	
	public List<NodeMail> findByKeyword(String keyword) throws DatabaseException {
		log.debug("findByKeyword({})", keyword);
		final String qs = "from NodeMail nm where :keyword in elements(nm.keywords) order by nm.name";
		final String sql = "select NBS_UUID from OKM_NODE_KEYWORD, OKM_NODE_MAIL " +
				"where NKW_KEYWORD = :keyword and NKW_NODE = NBS_UUID";
		List<NodeMail> ret = new ArrayList<>();
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			if (Config.NATIVE_SQL_OPTIMIZATIONS) {
				NativeQuery<String> q = session.createNativeQuery(sql, String.class);
				q.setCacheable(true);
				q.setCacheRegion(CACHE_MAILS_BY_KEYWORD);
				q.setParameter("keyword", keyword);
				q.addScalar("NBS_UUID", StandardBasicTypes.STRING);

				for (String uuid : q.getResultList()) {
					NodeMail nMail = session.get(NodeMail.class, uuid);
					ret.add(nMail);
				}
			} else {
				Query<NodeMail> q = session.createQuery(qs, NodeMail.class).setCacheable(true);
				q.setParameter("keyword", keyword);
				ret = q.getResultList();
			}

			// Security Check
			SecurityHelper.pruneNodeList(ret);

			initialize(ret);
			HibernateUtil.commit(tx);
			log.debug("findByKeyword: {}", ret);
			return ret;
		} catch (DatabaseException e) {
			HibernateUtil.rollback(tx);
			throw e;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Search nodes by property value
	 */
	
	public List<NodeMail> findByPropertyValue(String group, String property, String value) throws DatabaseException {
		log.debug("findByPropertyValue({}, {})", property, value);
		String qs = "select nb from NodeMail nb join nb.properties nbp where nbp.group=:group and nbp.name=:property and nbp.value like :value";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			Query<NodeMail> q = session.createQuery(qs, NodeMail.class);
			q.setParameter("group", group);
			q.setParameter("property", property);
			q.setParameter("value", "%" + value + "%");
			List<NodeMail> ret = q.getResultList();

			// Security Check
			SecurityHelper.pruneNodeList(ret);

			initialize(ret);
			HibernateUtil.commit(tx);
			log.debug("findByPropertyValue: {}", ret);
			return ret;
		} catch (DatabaseException e) {
			HibernateUtil.rollback(tx);
			throw e;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Check if folder has childs
	 */
	
	public boolean hasChildren(String parentUuid) throws PathNotFoundException, DatabaseException {
		log.debug("hasChildren({})", parentUuid);
		String qs = "from NodeMail nm where nm.parent=:parent";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Security Check
			if (!Config.ROOT_NODE_UUID.equals(parentUuid)) {
				NodeBase parentNode = session.get(NodeBase.class, parentUuid);
				SecurityHelper.checkRead(parentNode);
			}

			Query<NodeFolder> q = session.createQuery(qs, NodeFolder.class);
			q.setParameter("parent", parentUuid);
			List<NodeFolder> nodeList = q.getResultList();

			// Security Check
			SecurityHelper.pruneNodeList(nodeList);

			boolean ret = !nodeList.isEmpty();
			HibernateUtil.commit(tx);
			log.debug("hasChildren: {}", ret);
			return ret;
		} catch (PathNotFoundException | DatabaseException e) {
			HibernateUtil.rollback(tx);
			throw e;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Rename mail
	 */
	public synchronized NodeMail rename(String uuid, String newName) throws PathNotFoundException, AccessDeniedException,
			ItemExistsException, DatabaseException {
		log.debug("rename({}, {})", uuid, newName);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Security Check
			NodeBase parentNode = NodeBaseDAO.getInstance().getParentNode(session, uuid);
			SecurityHelper.checkRead(parentNode);
			SecurityHelper.checkWrite(parentNode);
			NodeMail nMail = session.get(NodeMail.class, uuid);
			SecurityHelper.checkRead(nMail);
			SecurityHelper.checkWrite(nMail);

			// Check for same folder name in same parent
			NodeBaseDAO.getInstance().checkItemExistence(session, nMail.getParent(), newName);

			nMail.setName(newName);

			if (Config.STORE_NODE_PATH) {
				nMail.setPath(parentNode.getPath() + "/" + newName);
			}

			session.merge(nMail);
			initialize(nMail, false);
			HibernateUtil.commit(tx);
			log.debug("rename: {}", nMail);
			return nMail;
		} catch (PathNotFoundException | AccessDeniedException | ItemExistsException | DatabaseException e) {
			HibernateUtil.rollback(tx);
			throw e;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Move mail
	 */
	public synchronized void move(String uuid, String dstUuid) throws PathNotFoundException, AccessDeniedException,
			ItemExistsException, DatabaseException {
		log.debug("move({}, {})", uuid, dstUuid);
		long begin = System.currentTimeMillis();
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Security Check
			NodeFolder nDstFld = session.get(NodeFolder.class, dstUuid);
			SecurityHelper.checkRead(nDstFld);
			SecurityHelper.checkWrite(nDstFld);
			NodeMail nMail = session.get(NodeMail.class, uuid);
			SecurityHelper.checkRead(nMail);
			SecurityHelper.checkWrite(nMail);

			// Check for same folder name in same parent
			NodeBaseDAO.getInstance().checkItemExistence(session, dstUuid, nMail.getName());

			// Check if context changes
			if (!nDstFld.getContext().equals(nMail.getContext())) {
				nMail.setContext(nDstFld.getContext());

				// Need recursive context changes
				moveHelper(session, uuid, nDstFld.getContext());
			}

			nMail.setParent(dstUuid);

			if (Config.STORE_NODE_PATH) {
				nMail.setPath(nDstFld.getPath() + "/" + nMail.getName());
			}

			session.merge(nMail);
			HibernateUtil.commit(tx);
			SystemProfiling.log(uuid, System.currentTimeMillis() - begin);
			log.trace("move.Time: {}", System.currentTimeMillis() - begin);
			log.debug("move: void");
		} catch (PathNotFoundException | AccessDeniedException | DatabaseException | ItemExistsException e) {
			HibernateUtil.rollback(tx);
			throw e;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Delete mail
	 */
	public void delete(String name, String uuid, String trashUuid) throws PathNotFoundException, AccessDeniedException,
			DatabaseException {
		log.debug("delete({}, {}, {})", name, uuid, trashUuid);
		long begin = System.currentTimeMillis();
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Security Check
			NodeFolder nTrashFld = session.get(NodeFolder.class, trashUuid);
			SecurityHelper.checkRead(nTrashFld);
			SecurityHelper.checkWrite(nTrashFld);
			NodeMail nMail = session.get(NodeMail.class, uuid);
			SecurityHelper.checkRead(nMail);
			SecurityHelper.checkWrite(nMail);
			SecurityHelper.checkDelete(nMail);

			// Test if already exists a mail with the same name in the trash
			String testName = name;

			for (int i = 1; NodeBaseDAO.getInstance().testItemExistence(session, trashUuid, testName); i++) {
				// log.info("Trying with: {}", testName);
				testName = name + " (" + i + ")";
			}

			// Need recursive context changes
			moveHelper(session, uuid, nTrashFld.getContext());

			nMail.setContext(nTrashFld.getContext());
			nMail.setParent(trashUuid);
			nMail.setName(testName);

			if (Config.STORE_NODE_PATH) {
				nMail.setPath(nTrashFld.getPath() + "/" + testName);
			}

			session.merge(nMail);
			HibernateUtil.commit(tx);
			SystemProfiling.log(uuid, System.currentTimeMillis() - begin);
			log.trace("delete.Time: {}", System.currentTimeMillis() - begin);
			log.debug("delete: void");
		} catch (PathNotFoundException | AccessDeniedException | DatabaseException e) {
			HibernateUtil.rollback(tx);
			throw e;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	
	private void moveHelper(Session session, String parentUuid, String newContext) throws HibernateException {
		String qs = "from NodeBase nf where nf.parent=:parent";
		Query<NodeBase> q = session.createQuery(qs, NodeBase.class);
		q.setParameter("parent", parentUuid);

		for (NodeBase nBase : q.getResultList()) {
			nBase.setContext(newContext);
		}
	}

	/**
	 * Purge in depth
	 */
	public void purge(String uuid) throws PathNotFoundException, AccessDeniedException, LockException,
			DatabaseException, IOException {
		log.debug("purge({})", uuid);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Security Check
			NodeMail nMail = session.get(NodeMail.class, uuid);
			SecurityHelper.checkRead(nMail);
			SecurityHelper.checkDelete(nMail);

			purgeHelper(session, nMail);
			HibernateUtil.commit(tx);
			log.debug("purge: void");
		} catch (PathNotFoundException | AccessDeniedException | IOException | DatabaseException e) {
			HibernateUtil.rollback(tx);
			throw e;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Purge in depth helper
	 *
	 * @see com.openkm.dao.NodeFolderDAO.purgeHelper(Session, NodeFolder, boolean)
	 */
	
	public void purgeHelper(Session session, String parentUuid) throws PathNotFoundException, AccessDeniedException,
			LockException, IOException, DatabaseException, HibernateException {
		String qs = "from NodeMail nm where nm.parent=:parent";
		long begin = System.currentTimeMillis();
		Query<NodeMail> q = session.createQuery(qs, NodeMail.class);
		q.setParameter("parent", parentUuid);
		List<NodeMail> listMails = q.getResultList();

		for (NodeMail nMail : listMails) {
			purgeHelper(session, nMail);
		}

		SystemProfiling.log(parentUuid, System.currentTimeMillis() - begin);
		log.trace("purgeHelper.Time: {}", System.currentTimeMillis() - begin);
	}

	/**
	 * Purge in depth helper
	 */
	private void purgeHelper(Session session, NodeMail nMail) throws PathNotFoundException, AccessDeniedException,
			LockException, IOException, DatabaseException, HibernateException {
		String path = NodeBaseDAO.getInstance().getPathFromUuid(session, nMail.getUuid());
		String user = PrincipalUtils.getUser();

		// Security Check
		SecurityHelper.checkRead(nMail);
		SecurityHelper.checkDelete(nMail);

		// Delete children documents
		NodeDocumentDAO.getInstance().purgeHelper(session, nMail.getUuid());

		// Delete children notes
		NodeNoteDAO.getInstance().purgeHelper(session, nMail.getUuid());

		// Delete bookmarks
		BookmarkDAO.purgeBookmarksByNode(nMail.getUuid());

		// Delete children wiki pages
		WikiPageDAO.purgeWikiPagesByNode(nMail.getUuid());

		// Delete children forum topics
		ForumDAO.purgeTopicsByNode(nMail.getUuid());

		// Delete children staples
		StapleGroupDAO.purgeStaplesByNode(nMail.getUuid());

		// Delete the node itself
		session.remove(nMail);

		// Activity log
		UserActivity.log(user, "PURGE_MAIL", nMail.getUuid(), path, null);
	}

	/**
	 * Check for mail item existence.
	 */
	public boolean itemExists(String uuid) throws DatabaseException {
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();

			// Security Check
			NodeBase nBase = session.get(NodeMail.class, uuid);

			if (nBase instanceof NodeMail) {
				SecurityHelper.checkRead(nBase);
				return true;
			}

			return false;
		} catch (ObjectNotFoundException | PathNotFoundException e) {
			return false;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Check for a valid mail node.
	 */
	public boolean isValid(String uuid) throws DatabaseException {
		try {
			findByPk(uuid);
			return true;
		} catch (PathNotFoundException e) {
			return false;
		}
	}

	/**
	 * Force initialization of a proxy
	 */
	public void initialize(NodeMail nMail, boolean initPropGroups) {
		if (nMail != null) {
			Hibernate.initialize(nMail);
			Hibernate.initialize(nMail.getTo());
			Hibernate.initialize(nMail.getCc());
			Hibernate.initialize(nMail.getBcc());
			Hibernate.initialize(nMail.getReply());
			Hibernate.initialize(nMail.getKeywords());
			Hibernate.initialize(nMail.getCategories());
			Hibernate.initialize(nMail.getSubscriptors());
			Hibernate.initialize(nMail.getUserPermissions());
			Hibernate.initialize(nMail.getRolePermissions());

			if (initPropGroups) {
				Hibernate.initialize(nMail.getProperties());
			}
		}
	}

	/**
	 * Force initialization of a proxy
	 */
	private void initialize(List<NodeMail> nMailList) {
		for (NodeMail nMail : nMailList) {
			initialize(nMail, true);
		}
	}
}
