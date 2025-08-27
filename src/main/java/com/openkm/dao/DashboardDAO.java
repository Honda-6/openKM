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
import com.openkm.dao.bean.Dashboard;
import org.hibernate.HibernateException;
import org.hibernate.query.Query;
import org.hibernate.query.MutationQuery;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.List;

public class DashboardDAO {
	private static Logger log = LoggerFactory.getLogger(DashboardDAO.class);

	private DashboardDAO() {
	}

	/**
	 * Get dashboard stats
	 */
	public Dashboard findByPk(int dsId) throws DatabaseException {
		log.debug("findByPk({})", dsId);
		String qs = "from Dashboard db where db.id=:id";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<Dashboard> q = session.createQuery(qs, Dashboard.class);
			q.setParameter("id", dsId);
			List<Dashboard> results = q.getResultList(); // uniqueResult
			Dashboard ret = null;

			if (results.size() == 1) {
				ret = results.get(0);
			}

			log.debug("findByPk: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Create dashboard stats
	 */
	public static void createIfNew(Dashboard db) throws DatabaseException {
		String qs = "from Dashboard db where db.user=:user and db.source=:source " +
				"and db.node=:node and db.date=:date";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Query<Dashboard> q = session.createQuery(qs, Dashboard.class);
			q.setParameter("user", db.getUser());
			q.setParameter("source", db.getSource());
			q.setParameter("node", db.getNode());
			q.setParameter("date", db.getDate());

			if (q.getResultList().isEmpty()) {
				session.persist(db);
			}

			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Delete dashboard stats
	 */
	public void delete(int dsId) throws DatabaseException {
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Dashboard ds = session.get(Dashboard.class, dsId);
			session.remove(ds);
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find by user source
	 */
	public static List<Dashboard> findByUserSource(String user, String source) throws
			DatabaseException {
		log.debug("findByUserSource({}, {})", user, source);
		String qs = "from Dashboard db where db.user=:user and db.source=:source";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<Dashboard> q = session.createQuery(qs, Dashboard.class);
			q.setParameter("user", user);
			q.setParameter("source", source);
			List<Dashboard> ret = q.getResultList();
			log.debug("findByUserSource: " + ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Delete visited nodes
	 */
	public static void deleteVisitedNodes(String user, String source) throws DatabaseException {
		log.debug("deleteVisitedNodes({}, {})", user, source);
		String qs = "delete from Dashboard db where db.user=:user and db.source=:source";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Query<Dashboard> q = session.createQuery(qs, Dashboard.class);
			q.setParameter("user", user);
			q.setParameter("source", source);
			q.executeUpdate();
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("deleteVisitedNodes: void");
	}

	/**
	 * Delete old visited node
	 */
	public static void purgeOldVisitedNode(String user, String source, String node, Calendar date) throws
			DatabaseException {
		log.debug("purgeOldVisitedNode({}, {}, {}, {})", user, source, node, date);
		String qs = "delete from Dashboard db where db.user=:user and db.source=:source " +
				"and db.node=:node and db.date=:date";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			MutationQuery q = session.createMutationQuery(qs);
			q.setParameter("user", user);
			q.setParameter("source", source);
			q.setParameter("node", node);
			q.setParameter("date", date);
			q.executeUpdate();
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("purgeOldVisitedNode: void");
	}
}
