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

import com.openkm.core.Config;
import com.openkm.dao.HibernateUtil;
import com.openkm.dao.bean.NodeBase;
import com.openkm.dao.bean.NodeDocument;
import com.openkm.dao.bean.NodeFolder;
import com.openkm.dao.bean.NodeMail;
import com.openkm.util.FormatUtil;
import com.openkm.util.WebUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.hibernate.Session;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.backend.lucene.LuceneExtension;
import org.hibernate.search.backend.lucene.lowlevel.directory.spi.DirectoryProvider;
import org.hibernate.search.engine.backend.Backend;
import org.hibernate.search.engine.backend.document.model.spi.IndexField;
import org.hibernate.search.engine.backend.index.IndexManager;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.scope.SearchScope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * Rebuild Lucene indexes
 */
public class ListIndexesServlet extends BaseServlet {
	private static final long serialVersionUID = 1L;
	private static Logger log = LoggerFactory.getLogger(ListIndexesServlet.class);

	@Override
	public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		String method = request.getMethod();

		if (checkMultipleInstancesAccess(request, response)) {
			if (method.equals(METHOD_GET)) {
				doGet(request, response);
			} else if (method.equals(METHOD_POST)) {
				doPost(request, response);
			}
		}
	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		log.debug("doGet({}, {})", request, response);
		request.setCharacterEncoding("UTF-8");
		String action = WebUtils.getString(request, "action");
		updateSessionManager(request);

		try {
			if (action.equals("search")) {
				searchLuceneDocuments(request, response);
			} else {
				showLuceneDocument(request, response);
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			sendErrorRedirect(request, response, e);
		}
	}

	/**
	 * List Lucene indexes
	 */
	@SuppressWarnings("unchecked")
	private void showLuceneDocument(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		boolean showTerms = WebUtils.getBoolean(request, "showTerms");
		int id = WebUtils.getInt(request, "id", 0);
    	List<Map<String, String>> fields = new ArrayList<>();

		try {
        Session session = HibernateUtil.getSessionFactory().openSession();
        SearchSession searchSession = Search.session(session);

        // 🔹 Access Lucene index directly through extension
        SearchScope<NodeDocument> scope = searchSession.scope(NodeDocument.class);

        try (IndexReader reader = scope.extension(LuceneExtension.get()).openIndexReader()) {
                if (id >= 0 && id < reader.maxDoc() && !reader.hasDeletions()) {
                    Document doc = reader.document(id);
                    String hibClass = null;

                    // Collect stored fields
                    for (IndexableField fld : doc.getFields()) {
                        Map<String, String> field = new HashMap<>();
                        field.put("name", fld.name());
                        field.put("value", fld.stringValue());
                        fields.add(field);

                        if ("_hibernate_class".equals(fld.name())) {
                            hibClass = fld.stringValue();
                        }
                    }

                    // Collect terms if requested
                    if (showTerms && NodeDocument.class.getCanonicalName().equals(hibClass)) {
                        List<String> terms = new ArrayList<>();
                        Terms vector = reader.getTermVector(id, "text");
                        if (vector != null) {
                            TermsEnum te = vector.iterator();
                            BytesRef term;
                            while ((term = te.next()) != null) {
                                terms.add(term.utf8ToString());
                            }
                        }

                        Map<String, String> field = new HashMap<>();
                        field.put("name", "terms");
                        field.put("value", terms.toString());
                        fields.add(field);
                    }
                }
            };

        ServletContext sc = getServletContext();
        sc.setAttribute("fields", fields);
        sc.setAttribute("id", id);
		sc.setAttribute("prev", id > 0);
        sc.setAttribute("showTerms", showTerms);
        sc.getRequestDispatcher("/admin/list_indexes.jsp").forward(request, response);

    } finally {
        // HibernateUtil.close(session);
    }
}

	/**
	 * Search Lucene indexes
	 */
	@SuppressWarnings("unchecked")
	private void searchLuceneDocuments(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException,
			ParseException {
		String exp = WebUtils.getString(request, "exp");
		List<Map<String, String>> results = new ArrayList<>();

		try {
			Session session = HibernateUtil.getSessionFactory().openSession();
       	    SearchSession searchSession = Search.session(session);

			if (exp != null && !exp.isEmpty()) {
				Query query;

				if (FormatUtil.isValidUUID(exp)) {
					query = new TermQuery(new Term(NodeBase.UUID_FIELD, exp));
				} else {
					QueryParser parser = new QueryParser(NodeBase.UUID_FIELD, new WhitespaceAnalyzer());
					query = parser.parse(exp);
				}

				 // Create scope for multiple entity types
            SearchScope<NodeBase> scope = searchSession.scope(NodeBase.class);

            SearchResult<NodeBase> searchResult = searchSession.search(scope)
                    .where(f -> f.extension(LuceneExtension.get())
					.fromLuceneQuery(query))  // <-- here
					.fetch(50);

            searchResult.hits().forEach(nBase -> {
                Map<String, String> res = new HashMap<>();
                res.put("uuid", nBase.getUuid());
                res.put("name", nBase.getName());

                if (nBase instanceof NodeDocument) {
                    res.put("type", "Document");
                } else if (nBase instanceof NodeFolder) {
                    res.put("type", "Folder");
                } else if (nBase instanceof NodeMail) {
                    res.put("type", "Mail");
                }

                results.add(res);
				 });
			}

			ServletContext sc = getServletContext();
			sc.setAttribute("results", results);
			sc.setAttribute("exp", exp.replaceAll("\"", "&quot;"));
			sc.getRequestDispatcher("/admin/search_indexes.jsp").forward(request, response);
		} finally {
			// HibernateUtil.close(session);
		}
	}
}
