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

import com.openkm.core.AccessDeniedException;
import com.openkm.core.Config;
import com.openkm.core.DatabaseException;
import com.openkm.dao.bean.QueryParams;
import com.openkm.dao.bean.Role;
import com.openkm.dao.bean.User;
import com.openkm.spring.PrincipalUtils;
import com.openkm.util.KeycloakUtils;
import com.openkm.util.SecureStore;
import org.hibernate.*;
import org.hibernate.query.Query;
import org.hibernate.query.MutationQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;


public class AuthDAO {
	private static Logger log = LoggerFactory.getLogger(AuthDAO.class);


	private AuthDAO() {
	}

	/**
	 * Create user in database
	 */
	public static void createUser(User user) throws DatabaseException, AccessDeniedException {
		log.debug("createUser({})", user);
		// Check if user has enough grants for the action
		checkAccessGrants(null);

		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			KeycloakUtils keycloakUtils = KeycloakUtils.getInstance();
			keycloakUtils.createUser(user);
			user.setPassword(SecureStore.md5Encode(user.getPassword().getBytes()));
			session.persist(user);
			HibernateUtil.commit(tx);
		} catch (HibernateException | NoSuchAlgorithmException | IOException e) {
			HibernateUtil.rollback(tx);
			log.error(e.getMessage(), e);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("createUser: void");
	}

	/**
	 * Update user in database
	 */
	public static void updateUser(User user) throws DatabaseException, AccessDeniedException {
		log.debug("updateUser({})", user);
		// Check if user has enought grants for the action
		checkAccessGrants(user.getId());

		String qs = "select u.password from User u where u.id=:id";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Query<String> q = session.createQuery(qs, String.class);
			q.setParameter("id", user.getId());
			KeycloakUtils keycloakUtils = KeycloakUtils.getInstance();
			keycloakUtils.updateUserData(user);
			String password = q.setMaxResults(1).uniqueResult();
			user.setPassword(password);
			session.merge(user);
			HibernateUtil.commit(tx);
		} catch (HibernateException | IOException e) {
			HibernateUtil.rollback(tx);
			log.error(e.getMessage(), e);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("updateUser: void");
	}

	/**
	 * Active user in database
	 */
	public static void activeUser(String usrId, boolean active) throws DatabaseException, AccessDeniedException {
		log.debug("activeUser({}, {})", usrId, active);
		// Check if user has enought grants for the action
		checkAccessGrants(null);

		String qs = "update User u set u.active=:active where u.id=:id";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			MutationQuery q = session.createMutationQuery(qs);
			q.setParameter("active", active);
			q.setParameter("id", usrId);
			q.executeUpdate();
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("activeUser: void");
	}

	/**
	 * Update user password in database
	 */
	public static void updateUserPassword(String usrId, String usrPassword) throws DatabaseException, AccessDeniedException {
		log.debug("updateUserPassword({}, {})", usrId, usrPassword);
		// Check if user has enought grants for the action
		checkAccessGrants(usrId);

		String qs = "update User u set u.password=:password where u.id=:id";
		Session session = null;
		Transaction tx = null;

		try {
			if (usrPassword != null && usrPassword.trim().length() > 0) {
				session = HibernateUtil.getSessionFactory().openSession();
				KeycloakUtils keycloakUtils = KeycloakUtils.getInstance();
				keycloakUtils.updatePassword(usrId,usrPassword);
				tx = session.beginTransaction();
				MutationQuery q = session.createMutationQuery(qs);
				q.setParameter("password", SecureStore.md5Encode(usrPassword.getBytes()));
				q.setParameter("id", usrId);
				q.executeUpdate();
				HibernateUtil.commit(tx);
			}
		} catch (HibernateException | NoSuchAlgorithmException | IOException e) {
			HibernateUtil.rollback(tx);
			log.error(e.getMessage(), e);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("updateUserPassword: void");
	}

	/**
	 * Update user email in database
	 */
	public static void updateUserEmail(String usrId, String usrEmail) throws DatabaseException, AccessDeniedException {
		log.debug("updateUserEmail({}, {})", usrId, usrEmail);
		// Check if user has enought grants for the action
		checkAccessGrants(usrId);

		String qs = "update User u set u.email=:email where u.id=:id";
		Session session = null;
		Transaction tx = null;

		try {
			if (usrEmail != null && usrEmail.trim().length() > 0) {
				session = HibernateUtil.getSessionFactory().openSession();
				tx = session.beginTransaction();
				MutationQuery q = session.createMutationQuery(qs);
				q.setParameter("email", usrEmail);
				q.setParameter("id", usrId);
				q.executeUpdate();
				KeycloakUtils keycloakUtils = KeycloakUtils.getInstance();
				keycloakUtils.updateEmail(usrId,usrEmail);
				HibernateUtil.commit(tx);
			}
		} catch (HibernateException | IOException e) {
			HibernateUtil.rollback(tx);
			log.error(e.getMessage(), e);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("updateUserEmail: void");
	}

	/**
	 * Delete user from database
	 */
	public static void deleteUser(String usrId) throws DatabaseException, AccessDeniedException {
		log.debug("deleteUser({})", usrId);
		// Check if user has enought grants for the action
		checkAccessGrants(null);

		String qsMail = "delete from MailAccount ma where ma.user=:user";
		String qsTwitter = "delete from TwitterAccount ta where ta.user=:user";
		String qsBookmark = "delete from Bookmark bm where bm.user=:user";
		String qsConfig = "delete from UserConfig uc where uc.user=:user";
		String qsItems = "delete from UserItems ui where ui.user=:user";
		String qsSharedQuery = "from QueryParams qp where :user in elements(qp.shared)";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			User user = session.get(User.class, usrId);
			session.remove(user);

			MutationQuery qMail = session.createMutationQuery(qsMail);
			qMail.setParameter("user", usrId);
			qMail.executeUpdate();

			MutationQuery qTwitter = session.createMutationQuery(qsTwitter);
			qTwitter.setParameter("user", usrId);
			qTwitter.executeUpdate();

			MutationQuery qBookmark = session.createMutationQuery(qsBookmark);
			qBookmark.setParameter("user", usrId);
			qBookmark.executeUpdate();

			MutationQuery qConfig = session.createMutationQuery(qsConfig);
			qConfig.setParameter("user", usrId);
			qConfig.executeUpdate();

			MutationQuery qItems = session.createMutationQuery(qsItems);
			qItems.setParameter("user", usrId);
			qItems.executeUpdate();

			Query<QueryParams> qSharedQuery = session.createQuery(qsSharedQuery, QueryParams.class);
			qSharedQuery.setParameter("user", usrId);
			for (QueryParams qp : qSharedQuery.getResultList()) {
				qp.getShared().remove(usrId);
				session.merge(qp);
			}
			KeycloakUtils keycloakUtils = KeycloakUtils.getInstance();
			keycloakUtils.deleteUser(user);

			HibernateUtil.commit(tx);
		} catch (HibernateException | IOException e) {
			HibernateUtil.rollback(tx);
			log.error(e.getMessage(), e);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("deleteUser: void");
	}

	/**
	 * Get all users in database
	 *
	 * @param filterByActive If only active user2 should be included.
	 */
	public static List<User> findAllUsers(boolean filterByActive) throws DatabaseException {
		log.debug("findAllUsers({})", filterByActive);
		String qs = "from User u " + (filterByActive ? "where u.active=:active" : "") + " order by u.id";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<User> q = session.createQuery(qs, User.class);

			if (filterByActive) {
				q.setParameter("active", true);
			}

			List<User> ret = q.getResultList();
			log.debug("findAllUsers: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Get all users within a role
	 */
	public static List<User> findUsersByRole(String rolId, boolean filterByActive) throws DatabaseException {
		log.debug("findUsersByRole({}, {})", rolId, filterByActive);
		String qs = "select u from User u, Role r where r.id=:rolId and r in elements(u.roles) "
				+ (filterByActive ? "and u.active=:active" : "") + " order by u.id";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<User> q = session.createQuery(qs, User.class);
			q.setParameter("rolId", rolId);

			if (filterByActive) {
				q.setParameter("active", true);
			}

			List<User> ret = q.getResultList();
			log.debug("findUsersByRole: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Get all users within a role
	 */
	public static List<Role> findRolesByUser(String usrId, boolean filterByActive) throws DatabaseException {
		log.debug("findRolesByUser({}, {})", usrId, filterByActive);
		String qs = "select r from User u, Role r where u.id=:usrId and r in elements(u.roles) "
				+ (filterByActive ? "and r.active=:active" : "") + " order by r.id";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<Role> q = session.createQuery(qs, Role.class);
			q.setParameter("usrId", usrId);

			if (filterByActive) {
				q.setParameter("active", true);
			}

			List<Role> ret = q.getResultList();
			log.debug("findRolesByUser: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Get user from database
	 */
	public static User findUserByPk(String usrId) throws DatabaseException {
		log.debug("findUserByPk({})", usrId);
		String qs = "from User u where u.id=:id";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<User> q = session.createQuery(qs, User.class);
			q.setParameter("id", usrId);
			User ret = q.setMaxResults(1).uniqueResult();
			log.debug("findUserByPk: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Create role in database
	 */
	public static void createRole(Role role) throws DatabaseException, AccessDeniedException {
		log.debug("createRole({})", role);
		// Check if user has enought grants for the action
		checkAccessGrants(null);

		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			session.persist(role);
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("createRole: void");
	}

	/**
	 * Update role in database
	 */
	public static void updateRole(Role role) throws DatabaseException, AccessDeniedException {
		log.debug("updateRole({})", role);
		// Check if user has enought grants for the action
		checkAccessGrants(null);

		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			session.merge(role);
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("updateRole: void");
	}

	/**
	 * Active role in database
	 */
	public static void activeRole(String rolId, boolean active) throws DatabaseException, AccessDeniedException {
		log.debug("activeRole({}, {})", rolId, active);
		// Check if user has enought grants for the action
		checkAccessGrants(null);

		String qs = "update Role r set r.active=:active where r.id=:id";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			MutationQuery q = session.createMutationQuery(qs);
			q.setParameter("active", active);
			q.setParameter("id", rolId);
			q.executeUpdate();
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("activeRole: void");
	}

	/**
	 * Delete role from database
	 */
	public static void deleteRole(String rolId) throws DatabaseException, AccessDeniedException {
		log.debug("deleteRole({})", rolId);
		// Check if user has enought grants for the action
		checkAccessGrants(null);

		String qs = "delete from OKM_USER_ROLE where UR_ROLE=:rolId";
		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			Role role = session.get(Role.class, rolId);
			session.remove(role);

			// TODO: Make Hibernate handle this relation.
			MutationQuery q = session.createNativeMutationQuery(qs);
			q.setParameter("rolId", rolId);
			q.executeUpdate();

			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("deleteRole: void");
	}

	/**
	 * Get all roles in database
	 */
	public static List<Role> findAllRoles() throws DatabaseException {
		log.debug("findAllRoles()");
		String qs = "from Role r order by r.id";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<Role> q = session.createQuery(qs, Role.class);
			List<Role> ret = q.getResultList();
			log.debug("findAllRoles: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Find role by pk
	 */
	public static Role findRoleByPk(String rolId) throws DatabaseException {
		log.debug("findRoleByPk({})", rolId);
		String qs = "from Role r where r.id=:id";
		Session session = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			Query<Role> q = session.createQuery(qs, Role.class);
			q.setParameter("id", rolId);
			Role ret = q.setMaxResults(1).uniqueResult();
			log.debug("findRoleByPk: {}", ret);
			return ret;
		} catch (HibernateException e) {
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}
	}

	/**
	 * Grant role to user
	 */
	public static void grantRole(String usrId, String rolId) throws DatabaseException, AccessDeniedException {
		log.debug("grantRole({}, {})", usrId, rolId);
		// Check if user has enought grants for the action
		checkAccessGrants(null);

		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			User user = session.get(User.class, usrId);
			Role role = session.get(Role.class, rolId);
			user.getRoles().add(role);
			session.merge(user);
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("grantRole: void");
	}

	/**
	 * Revoke role from user
	 */
	public static void revokeRole(String usrId, String rolId) throws DatabaseException, AccessDeniedException {
		log.debug("revokeRole({}, {})", usrId, rolId);
		// Check if user has enought grants for the action
		checkAccessGrants(null);

		Session session = null;
		Transaction tx = null;

		try {
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();
			User user = session.get(User.class, usrId);
			Role role = session.get(Role.class, rolId);
			user.getRoles().remove(role);
			session.merge(user);
			HibernateUtil.commit(tx);
		} catch (HibernateException e) {
			HibernateUtil.rollback(tx);
			throw new DatabaseException(e.getMessage(), e);
		} finally {
			HibernateUtil.close(session);
		}

		log.debug("revokeRole: void");
	}

	/**
	 * checkAccessGrants
	 */
	private static void checkAccessGrants(String userId) throws AccessDeniedException {
		// When userId != null means the user is able to make a change itself
		if (isNormalUser()) {
			if (userId == null || (userId != null && !PrincipalUtils.getUser().equals(userId))) {
				throw new AccessDeniedException("You need administrator grants for this change");
			}
		}
	}

	/**
	 * Check if it's normal user
	 */
	private static boolean isNormalUser() {
		boolean normalUser = !Config.ADMIN_USER.equalsIgnoreCase(PrincipalUtils.getUser()) &&
				!Config.SYSTEM_USER.equalsIgnoreCase(PrincipalUtils.getUser()) &&
				!PrincipalUtils.getRoles().contains(Config.DEFAULT_ADMIN_ROLE);
		return normalUser;
	}
}
