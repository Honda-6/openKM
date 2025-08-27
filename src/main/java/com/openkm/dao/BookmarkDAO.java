/**
 * OpenKM, Open Document Management System (http://www.openkm.com)
 * Copyright (c) Paco Avila & Josep Llort
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.openkm.dao;

import com.openkm.bean.Folder;
import com.openkm.bean.Repository;
import com.openkm.core.DatabaseException;
import com.openkm.core.PathNotFoundException;
import com.openkm.core.RepositoryException;
import com.openkm.dao.bean.Bookmark;
import com.openkm.dao.bean.NodeBase;
import org.hibernate.HibernateException;
import org.hibernate.query.Query;
import org.hibernate.query.MutationQuery;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BookmarkDAO {
	private static Logger log = LoggerFactory.getLogger(BookmarkDAO.class);

	private BookmarkDAO() {
	}

	/**
	 * Create
	 */
	public static void create(Bookmark bm) throws DatabaseException {
		log.debug("create({})", bm);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			session.persist(bm);
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("create: void");
	}

	/**
	 * Update
	 */
	public static void update(Bookmark bm) throws DatabaseException {
		log.debug("update({})", bm);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			session.merge(bm);
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("update: void");
	}

	/**
	 * Delete
	 */
	public static void delete(long bmId) throws DatabaseException {
		log.debug("delete({})", bmId);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Bookmark bm = session.get(Bookmark.class, bmId);
			session.remove(bm);
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("delete: void");
	}

	/**
	 * Find by user
	 */
	public static List<Bookmark> findByUser(String usrId) throws DatabaseException, RepositoryException {
		log.debug("findByUser({})", usrId);
		String qs = "from Bookmark bm where bm.user=:user order by bm.id";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Query<Bookmark> q = session.createQuery(qs, Bookmark.class);
			q.setParameter("user", usrId);
			List<Bookmark> ret = q.getResultList();

			for (Bookmark bm : ret) {
				// If user bookmark is missing, set a default
				NodeBase nBase = session.get(NodeBase.class, bm.getNode());

				if (nBase == null) {
					String rootPath = "/" + Repository.ROOT;
					String rootUuid = NodeBaseDAO.getInstance().getUuidFromPath(session, rootPath);
					bm.setNode(rootUuid);
					bm.setType(Folder.TYPE);
					session.persist(bm);
				}
			}

			HibernateUtil.commit(tx);
			log.debug("findByUser: {}", ret);
			return ret;
		} catch (PathNotFoundException e) {
			HibernateUtil.rollback(tx);
			throw new RepositoryException(e.getMessage(), e);
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
	public static Bookmark findByPk(long bmId) throws DatabaseException, RepositoryException {
		log.debug("findByPk({})", bmId);
		String qs = "from Bookmark bm where bm.id=:id";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Query<Bookmark> q = session.createQuery(qs, Bookmark.class);
			q.setParameter("id", bmId);
			Bookmark ret = q.setMaxResults(1).uniqueResult();

			if (ret != null) {
				// If user bookmark is missing, set a default
				NodeBase nBase = session.get(NodeBase.class, ret.getNode());

				if (nBase == null) {
					String rootPath = "/" + Repository.ROOT;
					String rootUuid = NodeBaseDAO.getInstance().getUuidFromPath(session, rootPath);
					ret.setNode(rootUuid);
					ret.setType(Folder.TYPE);
					session.persist(ret);
				}
			}

			HibernateUtil.commit(tx);
			log.debug("findByPk: {}", ret);
			return ret;
		} catch (PathNotFoundException e) {
			HibernateUtil.rollback(tx);
			throw new RepositoryException(e.getMessage(), e);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Remove bookmarks by parent node
	 */
	public static void purgeBookmarksByNode(String nodeUuid) throws DatabaseException {
		log.debug("purgeBookmarksByNode({})", nodeUuid);
		String qs = "delete from Bookmark bm where bm.node=:uuid";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			MutationQuery q = session.createMutationQuery(qs);
			q.setParameter("uuid", nodeUuid);
			q.executeUpdate();

			HibernateUtil.commit(tx);
			log.debug("purgeBookmarksByNode: void");
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}
}
