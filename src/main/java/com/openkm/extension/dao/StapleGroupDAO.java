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

package com.openkm.extension.dao;

import com.openkm.core.DatabaseException;
import com.openkm.core.RepositoryException;
import com.openkm.dao.HibernateUtil;
import com.openkm.extension.dao.bean.Staple;
import com.openkm.extension.dao.bean.StapleGroup;
import org.hibernate.HibernateException;
import org.hibernate.query.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class StapleGroupDAO {
	private static Logger log = LoggerFactory.getLogger(StapleGroupDAO.class);

	private StapleGroupDAO() {
	}

	/**
	 * Create
	 */
	public static long create(StapleGroup sg) throws DatabaseException {
		log.debug("create({})", sg);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Long id = (Long) sg.getId();
			session.persist(sg);
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
	 * Delete
	 */
	public static void delete(long sgId) throws DatabaseException {
		log.debug("delete({})", sgId);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			StapleGroup sg = session.get(StapleGroup.class, sgId);
			session.remove(sg);
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
	 * Delete
	 */
	public static void deleteStaple(long stId) throws DatabaseException {
		log.debug("delete({})", stId);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Staple st = session.get(Staple.class, stId);
			session.remove(st);
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
	 * Find all stapling groups
	 */
	
	public static List<StapleGroup> findAll(String nodeUuid) throws DatabaseException,
			RepositoryException {
		log.debug("findAll({}, {})", nodeUuid);
		String qs = "select sg from StapleGroup sg, Staple st where st.node=:node and st in elements(sg.staples)";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<StapleGroup> q = session.createQuery(qs, StapleGroup.class);
			q.setParameter("node", nodeUuid);
			List<StapleGroup> ret = q.getResultList();

			log.debug("findAll: {}", ret);
			return ret;
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
	public static StapleGroup findByPk(long sgId) throws DatabaseException {
		log.debug("findByPk({})", sgId);
		String qs = "from StapleGroup sg where sg.id=:id";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<StapleGroup> q = session.createQuery(qs, StapleGroup.class);
			q.setParameter("id", sgId);
			StapleGroup ret = q.setMaxResults(1).uniqueResult();
			log.debug("findByPk: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Update
	 */
	public static void update(StapleGroup sg) throws DatabaseException {
		log.debug("update({})", sg);
		String qs = "select sg.user from StapleGroup sg where sg.id=:id";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Query<String> q = session.createQuery(qs, String.class);
			q.setParameter("id", sg.getId());
			String user = q.setMaxResults(1).uniqueResult();
			sg.setUser(user);
			session.merge(sg);
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
	 * Delete by node uuid
	 */
	
	public static void purgeStaplesByNode(String nodeUuid) throws DatabaseException {
		log.debug("purgeStaplesByNode({})", nodeUuid);
		String qsStaples = "from Staple st where st.node=:uuid";
		String qsEmpty = "select sg.id from StapleGroup sg left join sg.staples st group by sg.id having count(st)=0";
		String qsDelete = "delete from StapleGroup sg where sg.id=:id";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			Query<Staple> qStaples = session.createQuery(qsStaples, Staple.class);
			qStaples.setParameter("uuid", nodeUuid);

			for (Staple st : qStaples.getResultList()) {
				session.remove(st);
			}

			// Remove empty staple groups
			for (long sgId : (List<Long>) session.createQuery(qsEmpty, Long.class).getResultList()) {
				session.createQuery(qsDelete, StapleGroup.class).setParameter("id", sgId).executeUpdate();
			}

			HibernateUtil.commit(tx);
			log.debug("purgeStaplesByNode: void");
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}
}
