package com.openkm.spring;


import com.openkm.util.KeycloakUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.stereotype.Component;


@Component
public class KeycloakAuthenticationProvider implements AuthenticationProvider {

	@Autowired
	private KeycloakUtils keycloakUtils;

	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String username = authentication.getName();
		String password = authentication.getCredentials().toString();
		return new UsernamePasswordAuthenticationToken(username, password, keycloakUtils.getAuthorities(username, password));
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}

