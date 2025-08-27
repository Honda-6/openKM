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

import com.openkm.core.DatabaseException;
import com.openkm.dao.bean.Language;
import com.openkm.dao.bean.Translation;
import com.openkm.util.SystemProfiling;
import org.hibernate.*;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * LanguageDAO
 *
 * @author jllort
 *
 */
public class LanguageDAO {
	private static Logger log = LoggerFactory.getLogger(LanguageDAO.class);

	private LanguageDAO() {
	}

	/**
	 * Find translations by pk
	 */
	public static Language findByPk(String id) throws DatabaseException {
		log.debug("findByPk({})", id);
		Session session = null;

		try {
			long begin = System.currentTimeMillis();
			session = HibernateUtil.getSessionFactory().openSession();
			Language ret = session.get(Language.class, id);
			Hibernate.initialize(ret);
			SystemProfiling.log(id, System.currentTimeMillis() - begin);
			log.trace("findByPk.Time: {}", System.currentTimeMillis() - begin);
			log.debug("findByPk: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find all languages
	 */
	public static List<Language> findAll() throws DatabaseException {
		return findAll(CacheMode.NORMAL);
	}

	/**
	 * Refresh 2nd level cache
	 */
	public static void refresh() throws DatabaseException {
		findAll(CacheMode.REFRESH);
	}

	/**
	 * Find all languages
	 *
	 * @param cacheMode Execute language query with the designed cache mode.
	 */
	private static List<Language> findAll(CacheMode cacheMode) throws DatabaseException {
		log.debug("findAll({})", cacheMode);
		String qs = "from Language lg order by lg.name asc";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			session.setCacheMode(cacheMode);
			Query<Language> q = session.createQuery(qs,Language.class).setCacheable(true);
			List<Language> ret = q.getResultList();
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
	 * Delete
	 */
	public static void delete(String id) throws DatabaseException {
		log.debug("delete({})", id);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Language lang = session.get(Language.class, id);
			session.remove(lang);
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
	 * Update language in database
	 */
	public static void update(Language lang) throws DatabaseException {
		log.debug("update({})", lang);
		String qs = "select lg.imageContent, lg.imageMime from Language lg where lg.id=:id";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			if (lang.getImageContent() == null || lang.getImageContent().length() == 0) {
				Query<Object[]> q = session.createQuery(qs, Object[].class);
				q.setParameter("id", lang.getId());
				Object[] data = q.setMaxResults(1).uniqueResult();
				lang.setImageContent((String) data[0]);
				lang.setImageMime((String) data[1]);
			}

			session.merge(lang);
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
	 * Create language in database
	 */
	public static void create(Language lang) throws DatabaseException {
		log.debug("create({})", lang);
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			session.persist(lang);
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
	 * Get all language translations
	 */
	public static Set<Translation> findTransAll(String langId) throws DatabaseException {
		log.debug("findTransAll({})", langId);
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Language lang = session.get(Language.class, langId);
			Set<Translation> trans = lang.getTranslations();
			log.debug("findTransAll: {}", trans);
			return trans;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Get translation
	 */
	public static String getTranslation(String lang, String module, String key) throws DatabaseException {
		log.debug("getTranslation({}, {}, {})", module, lang, key);
		String qs = "select tr.text from Translation tr where tr.translationId.key=:key "
				+ "and tr.translationId.language=:lang and tr.translationId.module=:module";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<String> q = session.createQuery(qs, String.class);
			q.setParameter("key", key);
			q.setParameter("module", module);
			q.setParameter("lang", lang);
			String ret = q.setMaxResults(1).uniqueResult();

			log.debug("getTranslation: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Normalize translation
	 */
	public static void normalizeTranslation(Language language) throws DatabaseException {
		log.debug("normalizeTranslation({})", language);
		String qs = "from Translation tr where tr.translationId.language=:langDef "
				+ " and concat(tr.translationId.module, tr.translationId.key) not in"
				+ " (select concat(tr1.translationId.module, tr1.translationId.key) from Translation tr1 where tr1.translationId.language=:lang) ";

		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			// Get deprecated translation (in new language but not in english)
			Query<Translation> qDeprecated = session.createQuery(qs, Translation.class);
			qDeprecated.setParameter("langDef", language.getId());
			qDeprecated.setParameter("lang", Language.DEFAULT);
			List<Translation> retDeprecated = qDeprecated.getResultList();

			for (Translation translation : retDeprecated) {
				session.remove(translation);
			}

			HibernateUtil.commit(tx);
			log.debug("normalizeTranslation");
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}
}
