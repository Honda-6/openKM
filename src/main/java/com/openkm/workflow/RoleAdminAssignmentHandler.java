package com.openkm.workflow;

import java.util.Collection;

import org.jbpm.graph.exe.ExecutionContext;
import org.jbpm.taskmgmt.def.AssignmentHandler;
import org.jbpm.taskmgmt.exe.Assignable;

import com.openkm.module.db.DbAuthModule;

public class RoleAdminAssignmentHandler implements AssignmentHandler {
	private static final long serialVersionUID = 1L;

	@Override
	public void assign(Assignable assignable, ExecutionContext executionContext) throws Exception {
		DbAuthModule authModule = new DbAuthModule();

		// Get all users assigned to the ROLE_ADMIN
		Collection<String> adminUsers = authModule.getUsersByRole(null,"ROLE_ADMIN");

		if (adminUsers == null || adminUsers.isEmpty()) {
			throw new Exception("No users found in ROLE_ADMIN");
		}

		// Assign all as pooled actors
		assignable.setPooledActors(adminUsers.toArray(new String[0]));
	}
}
