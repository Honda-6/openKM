package com.openkm.workflow;

import com.openkm.api.OKMDocument;
import com.openkm.bean.Document;
import com.openkm.core.PathNotFoundException;

import org.jbpm.graph.def.ActionHandler;
import org.jbpm.graph.exe.ExecutionContext;

public class DeleteDocumentActionHandler implements ActionHandler {
	private static final long serialVersionUID = 1L;

	@Override
	public void execute(ExecutionContext context) throws Exception {
		String path = (String) context.getContextInstance().getVariable("document");
		try {

			if (path == null) {
				String uuid = (String) context.getContextInstance().getVariable("uuid");
				if (uuid != null) {
					Document doc = OKMDocument.getInstance().getProperties(null, uuid);
					path = doc.getPath();
				} else {
					throw new RuntimeException("Missing 'document' and 'uuid' workflow variables.");
				}
			}
			OKMDocument.getInstance().purge(null, path);
		} catch (PathNotFoundException e) {
			throw new RuntimeException("File not found!", e);
		} catch (Exception e) {
			throw new RuntimeException("Failed to delete document: " + path, e);
		}
	}
}
