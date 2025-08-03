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
		String uuid = (String) context.getContextInstance().getVariable("uuid");
		try {
			if (uuid != null) {
				Document doc = OKMDocument.getInstance().getProperties(null, uuid);
				OKMDocument.getInstance().purge(null, doc.getPath());
			} else {
				throw new RuntimeException("Missing 'document' and 'uuid' workflow variables.");
			}
		} catch (PathNotFoundException e) {
			throw new RuntimeException("File not found!", e);
		} catch (Exception e) {
			throw new RuntimeException("Failed to delete document", e);
		}
	}
}
