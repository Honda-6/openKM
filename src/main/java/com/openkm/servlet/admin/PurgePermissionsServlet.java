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

package com.openkm.servlet.admin;

import com.openkm.dao.HibernateUtil;
import com.openkm.dao.bean.NodeBase;
import com.openkm.util.WebUtils;
import org.hibernate.query.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Purge permissions
 */
public class PurgePermissionsServlet extends BaseServlet {
	private static final long serialVersionUID = 1L;
	private static Logger log = LoggerFactory.getLogger(PurgePermissionsServlet.class);

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		log.debug("doGet({}, {})", request, response);
		request.setCharacterEncoding("UTF-8");
		String action = WebUtils.getString(request, "action");
		updateSessionManager(request);
		Session session = null;
		Transaction tx = null;

		try {
			ServletContext sc = getServletContext();
			session = HibernateUtil.getSessionFactory().openSession();
			tx = session.beginTransaction();

			if (action.equals("listUsers")) {
				String qs = "select distinct(indices(nb.userPermissions)) from NodeBase nb";
				Query<NodeBase> query = session.createQuery(qs,NodeBase.class);
				sc.setAttribute("type", "User");
				sc.setAttribute("elements", query.list());
				sc.getRequestDispatcher("/admin/purge_perms_list.jsp").forward(request, response);
			} else if (action.equals("listRoles")) {
				String qs = "select distinct(indices(nb.rolePermissions)) from NodeBase nb";
				Query<NodeBase> query = session.createQuery(qs,NodeBase.class);
				sc.setAttribute("type", "Role");
				sc.setAttribute("elements", query.list());
				sc.getRequestDispatcher("/admin/purge_perms_list.jsp").forward(request, response);
			} else if (action.equals("purgeUser")) {
				String qs = "from NodeBase nb where :user in indices(nb.userPermissions)";
				String user = WebUtils.getString(request, "elto");
				Query<NodeBase> query = session.createQuery(qs,NodeBase.class);
				query.setParameter("user", user);

				for (NodeBase node : query.list()) {
					node.getUserPermissions().remove(user);
					session.merge(node);
				}

				response.sendRedirect(request.getContextPath() + request.getServletPath() + "?action=listUsers");
			} else if (action.equals("purgeRole")) {
				String qs = "from NodeBase nb where :role in indices(nb.rolePermissions)";
				String role = WebUtils.getString(request, "elto");
				Query<NodeBase> query = session.createQuery(qs,NodeBase.class);
				query.setParameter("role", role);

				for (NodeBase node : query.list()) {
					node.getRolePermissions().remove(role);
					session.merge(node);
				}

				response.sendRedirect(request.getContextPath() + request.getServletPath() + "?action=listRoles");
			} else {
				sc.getRequestDispatcher("/admin/purge_perms.jsp").forward(request, response);
			}

			HibernateUtil.commit(tx);
		} catch (Exception e) {
			HibernateUtil.rollback(tx);
			sendErrorRedirect(request, response, e);
		} finally {
			HibernateUtil.close(session);
		}
	}
}
