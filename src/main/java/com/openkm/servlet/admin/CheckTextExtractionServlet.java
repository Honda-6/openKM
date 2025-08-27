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

import com.openkm.api.OKMDocument;
import com.openkm.api.OKMRepository;
import com.openkm.bean.Repository;
import com.openkm.core.*;
import com.openkm.extractor.RegisteredExtractors;
import com.openkm.extractor.TextExtractor;
import com.openkm.util.PathUtils;
// import org.apache.commons.fileupload.FileItem;
// import org.apache.commons.fileupload.FileItemFactory;
// import org.apache.commons.fileupload.FileUploadException;
// import org.apache.commons.fileupload.disk.DiskFileItemFactory;
// import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.DiskFileItem;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Mime type management servlet
 */
public class CheckTextExtractionServlet extends BaseServlet {
	private static final long serialVersionUID = 1L;
	private static Logger log = LoggerFactory.getLogger(CheckTextExtractionServlet.class);

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		log.debug("doGet({}, {})", request, response);
		request.setCharacterEncoding("UTF-8");
		updateSessionManager(request);

		ServletContext sc = getServletContext();
		sc.setAttribute("repoPath", "/" + Repository.ROOT);
		sc.setAttribute("docUuid", null);
		sc.setAttribute("text", null);
		sc.setAttribute("time", null);
		sc.setAttribute("mimeType", null);
		sc.setAttribute("extractor", null);
		sc.getRequestDispatcher("/admin/check_text_extraction.jsp").forward(request, response);
	}

	@SuppressWarnings("unchecked")
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		log.debug("doPost({}, {})", request, response);
		request.setCharacterEncoding("UTF-8");
		updateSessionManager(request);
		InputStream is = null;

		try {
			if (JakartaServletFileUpload.isMultipartContent(request)) {
				var factory = DiskFileItemFactory.builder().get();
				var upload = new JakartaServletFileUpload<>(factory);
				List<DiskFileItem> items = upload.parseRequest(request);
				String docUuid = null;
				String repoPath = null;
				String text = null;
				String error = null;
				String mimeType = null;
				String extractor = null;

				for (DiskFileItem item : items) {
					if (item.isFormField()) {
						if (item.getFieldName().equals("docUuid")) {
							docUuid = item.getString(StandardCharsets.UTF_8);
						} else if (item.getFieldName().equals("repoPath")) {
							repoPath = item.getString(StandardCharsets.UTF_8);
						}
					} else {
						is = item.getInputStream();
						String name = FilenameUtils.getName(item.getName());
						mimeType = MimeTypeConfig.mimeTypes.getContentType(name.toLowerCase());

						if (!name.isEmpty() && item.getSize() > 0) {
							docUuid = null;
							repoPath = null;
						} else if (docUuid != null && docUuid.isEmpty() && repoPath != null && !repoPath.isEmpty()) {
							mimeType = null;
						}
					}
				}

				if (docUuid != null && !docUuid.isEmpty()) {
					repoPath = OKMRepository.getInstance().getNodePath(null, docUuid);
				}

				if (repoPath != null && !repoPath.isEmpty()) {
					String name = PathUtils.getName(repoPath);
					mimeType = MimeTypeConfig.mimeTypes.getContentType(name.toLowerCase());
					is = OKMDocument.getInstance().getContent(null, repoPath, false);
				}

				long begin = System.currentTimeMillis();

				if (is != null) {
					if (!MimeTypeConfig.MIME_UNDEFINED.equals(mimeType)) {
						try {
							if (extractor != null && !extractor.isEmpty()) {
								TextExtractor extClass = (TextExtractor) Class.forName(extractor).getDeclaredConstructor().newInstance();
								text = extClass.extractText(is, mimeType, null);
							} else {
								TextExtractor extClass = RegisteredExtractors.getTextExtractor(mimeType);

								if (extClass != null) {
									extractor = extClass.getClass().getCanonicalName();
									text = RegisteredExtractors.getText(mimeType, null, is);
								} else {
									extractor = "Undefined text extractor";
								}
							}
						} catch (Exception e) {
							error = e.getMessage();
						}
					}
				}

				ServletContext sc = getServletContext();
				sc.setAttribute("docUuid", docUuid);
				sc.setAttribute("repoPath", repoPath);
				sc.setAttribute("text", text);
				sc.setAttribute("time", System.currentTimeMillis() - begin);
				sc.setAttribute("mimeType", mimeType);
				sc.setAttribute("error", error);
				sc.setAttribute("extractor", extractor);
				sc.getRequestDispatcher("/admin/check_text_extraction.jsp").forward(request, response);
			}
		} catch (DatabaseException | FileUploadException | PathNotFoundException | AccessDeniedException | RepositoryException | LockException e) {
			sendErrorRedirect(request, response, e);
		} finally {
			IOUtils.closeQuietly(is);
		}
	}
}
