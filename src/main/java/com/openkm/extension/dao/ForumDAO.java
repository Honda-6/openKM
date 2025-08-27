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

package com.openkm.extension.dao;

import com.openkm.core.DatabaseException;
import com.openkm.dao.HibernateUtil;
import com.openkm.extension.dao.bean.Forum;
import com.openkm.extension.dao.bean.ForumPost;
import com.openkm.extension.dao.bean.ForumTopic;
import org.hibernate.HibernateException;
import org.hibernate.query.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * ExtensionDAO
 *
 * @author pavila
 */
public class ForumDAO {
	private static Logger log = LoggerFactory.getLogger(ForumDAO.class);

	private ForumDAO() {
	}

	/**
	 * Create
	 */
	public static long create(Forum frm) throws DatabaseException {
		log.debug("create({})", frm);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Long id = (Long) frm.getId();
			session.persist(frm);
			HibernateUtil.commit(tx);
			log.debug("create: {}" + id);
			return id;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Update
	 */
	public static void update(Forum frm) throws DatabaseException {
		log.debug("update({})", frm);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			session.merge(frm);
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
	 * Update
	 */
	public static void update(ForumTopic topic) throws DatabaseException {
		log.debug("update({})", topic);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			session.merge(topic);
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
	 * Update
	 */
	public static void update(ForumPost post) throws DatabaseException {
		log.debug("update({})", post);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			session.merge(post);
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
	public static void delete(long frmId) throws DatabaseException {
		log.debug("delete({})", frmId);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Forum frm = (Forum) session.get(Forum.class, frmId);
			session.remove(frm);
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("deleteSent: void");
	}

	/**
	 * Find by pk
	 */
	public static Forum findByPk(long id) throws DatabaseException {
		log.debug("findByPk({})", id);
		String qs = "from Forum frm where frm.id=:id";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<Forum> q = session.createQuery(qs, Forum.class);
			q.setParameter("id", id);
			Forum ret = q.setMaxResults(1).uniqueResult();
			log.debug("findByPk: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find by pk
	 */
	public static ForumTopic findTopicByPk(long id) throws DatabaseException {
		log.debug("findTopicByPk({})", id);
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			ForumTopic ret = (ForumTopic) session.get(ForumTopic.class, id);
			log.debug("findTopicByPk: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find by pk
	 */
	public static ForumPost findPostByPk(long id) throws DatabaseException {
		log.debug("findPostByPk({})", id);
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			ForumPost ret = (ForumPost) session.get(ForumPost.class, id);
			log.debug("findPostByPk: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find all forums
	 */
	
	public static List<Forum> findAll() throws DatabaseException {
		log.debug("findAll()");
		String qs = "from Forum frm order by frm.date asc";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<Forum> q = session.createQuery(qs, Forum.class);
			List<Forum> ret = q.getResultList();

			log.debug("findAll: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find all topics by node
	 */
	
	public static List<ForumTopic> findAllTopicsByNode(String uuid) throws DatabaseException {
		log.debug("findAllTopicsByNode({})", uuid);
		String qs = "from ForumTopic ft where ft.node=:uuid order by ft.lastPostDate desc";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<ForumTopic> q = session.createQuery(qs, ForumTopic.class);
			q.setParameter("uuid", uuid);
			List<ForumTopic> ret = q.getResultList();
			log.debug("findAllTopicsByNode: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Remove forum topics by parent node
	 */
	
	public static void purgeTopicsByNode(String nodeUuid) throws DatabaseException {
		log.debug("purgeTopicsByNode({})", nodeUuid);
		String qs = "from ForumTopic ft where ft.node=:uuid";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			Query<ForumTopic> q = session.createQuery(qs, ForumTopic.class);
			q.setParameter("uuid", nodeUuid);

			for (ForumTopic ft : q.getResultList()) {
				session.remove(ft);
			}

			HibernateUtil.commit(tx);
			log.debug("purgeTopicsByNode: void");
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}
}
