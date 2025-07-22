package com.openkm.spring;


import com.openkm.util.KeycloakUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.stereotype.Component;


import java.util.*;

@Component
public class KeycloakAuthenticationProvider implements AuthenticationProvider {

	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String username = authentication.getName();
		String password = authentication.getCredentials().toString();

		KeycloakUtils keycloakUtils = new KeycloakUtils();
		List<GrantedAuthority> authorities = keycloakUtils.getAuthorities(username, password);

		if(authorities.isEmpty())
			throw new AuthenticationServiceException("Unauthorized user or user not found");
		return new UsernamePasswordAuthenticationToken(username, password, authorities);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}

