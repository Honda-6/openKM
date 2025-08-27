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

import com.openkm.core.DatabaseException;
import com.openkm.dao.bean.QueryParams;
import org.hibernate.HibernateException;
import org.hibernate.query.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map.Entry;

public class QueryParamsDAO {
	private static Logger log = LoggerFactory.getLogger(QueryParamsDAO.class);

	private QueryParamsDAO() {
	}

	/**
	 * Create
	 */
	public static long create(QueryParams qp) throws DatabaseException {
		log.debug("create({})", qp);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Long id = (Long) qp.getId();
			session.persist(qp);
			QueryParams qpTmp = session.get(QueryParams.class, id);

			for (String keyword : qp.getKeywords()) {
				qpTmp.getKeywords().add(keyword);
			}

			for (String category : qp.getCategories()) {
				qpTmp.getCategories().add(category);
			}

			for (Entry<String, String> entry : qp.getProperties().entrySet()) {
				qpTmp.getProperties().put(entry.getKey(), entry.getValue());
			}

			HibernateUtil.commit(tx);
			log.debug("create: {}", id);
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
	public static void update(QueryParams qp) throws DatabaseException {
		log.debug("update({})", qp);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			session.merge(qp);
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
	public static void delete(long qpId) throws DatabaseException {
		log.debug("delete({})", qpId);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			QueryParams qp = session.get(QueryParams.class, qpId);
			session.remove(qp);
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
	 * Find by pk
	 */
	public static QueryParams findByPk(long qpId) throws DatabaseException {
		log.debug("findByPk({})", qpId);
		String qs = "from QueryParams qp where qp.id=:id";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<QueryParams> q = session.createQuery(qs, QueryParams.class);
			q.setParameter("id", qpId);
			QueryParams ret = q.setMaxResults(1).uniqueResult();
			log.debug("findByPk: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find by user
	 */
	public static List<QueryParams> findByUser(String user) throws DatabaseException {
		log.debug("findByUser({})", user);
		String qs = "from QueryParams qp where qp.user=:user";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<QueryParams> q = session.createQuery(qs, QueryParams.class);
			q.setParameter("user", user);
			List<QueryParams> ret = q.getResultList();
			HibernateUtil.commit(tx);
			log.debug("findByUser: {}", ret);
			return ret;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Share
	 */
	public static void share(long qpId, String user) throws DatabaseException {
		log.debug("share({})", qpId);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			QueryParams qp = session.get(QueryParams.class, qpId);
			qp.getShared().add(user);
			session.merge(qp);
			HibernateUtil.commit(tx);
			log.debug("share: void");
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Unshare
	 */
	public static void unshare(long qpId, String user) throws DatabaseException {
		log.debug("share({})", qpId);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			QueryParams qp = session.get(QueryParams.class, qpId);
			qp.getShared().remove(user);
			session.merge(qp);
			HibernateUtil.commit(tx);
			log.debug("share: void");
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find shared
	 */
	public static List<QueryParams> findShared(String user) throws DatabaseException {
		log.debug("findShared({})", user);
		String qs = "from QueryParams qp where :user in elements(qp.shared)";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<QueryParams> q = session.createQuery(qs, QueryParams.class);
			q.setParameter("user", user);
			List<QueryParams> ret = q.getResultList();
			log.debug("findShared: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find all proposed received from some user
	 */
	public static List<QueryParams> findProposedQueryByMeFromUser(String me, String user) throws DatabaseException {
		log.debug("findProposedQueryByMeFromUser({}, {})", me);
		String qs = "select qp from QueryParams qp, ProposedQueryReceived pr where pr.user=:me and pr.from=:user and pr in elements(qp.proposedReceived)";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<QueryParams> q = session.createQuery(qs, QueryParams.class);
			q.setParameter("me", me);
			q.setParameter("user", user);
			List<QueryParams> ret = q.getResultList();

			log.debug("findProposedQueryByMeFromUser: {}", ret);
			return ret;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find all proposed
	 */
	public static List<QueryParams> findProposedQueryFromMeToUser(String me, String user) throws DatabaseException {
		log.debug("findProposedQueryFromMeToUser({}, {})", me);
		String qs = "select qp from QueryParams qp, ProposedQuerySent pr where pr.user=:user and pr.from=:me and pr in elements(qp.proposedSent)";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<QueryParams> q = session.createQuery(qs, QueryParams.class);
			q.setParameter("me", me);
			q.setParameter("user", user);
			List<QueryParams> ret = q.getResultList();

			log.debug("findProposedQueryFromMeToUser: {}", ret);
			return ret;
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}
}
