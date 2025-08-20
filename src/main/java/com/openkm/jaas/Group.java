package com.openkm.jaas;

import java.security.Principal;
import java.util.Enumeration;

public interface Group extends Principal {
	public boolean isMember(Principal principal);
	public boolean addMember(Principal principal);
	public boolean removeMember(Principal principal);
	public Enumeration<? extends Principal> members();
}
