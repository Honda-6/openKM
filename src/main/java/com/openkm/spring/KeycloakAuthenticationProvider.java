package com.openkm.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class KeycloakAuthenticationProvider implements AuthenticationProvider {

	@Value("${tokenEndpoint}")
	String tokenEndpoint;

	@Value("${clientId}")
	String clientId;

	@Value("${clientSecret}")
	String clientSecret;

	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String username = authentication.getName();
		String password = authentication.getCredentials().toString();
		Set<String> roles = new HashSet<>();
		try {

			String urlParameters = "grant_type=password"
				+ "&client_id=" + URLEncoder.encode(clientId, "UTF-8")
				+ "&username=" + URLEncoder.encode(username, "UTF-8")
				+ "&password=" + URLEncoder.encode(password, "UTF-8")
				+ "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8");

			URL url = new URL(tokenEndpoint);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			try (OutputStream os = conn.getOutputStream()) {
				os.write(urlParameters.getBytes("UTF-8"));
			}

			InputStream is = conn.getInputStream();
			String json = new BufferedReader(new InputStreamReader(is))
				.lines().collect(Collectors.joining("\n"));

			ObjectMapper mapper = new ObjectMapper();
			JsonNode root = mapper.readTree(json);
			String accessToken = root.get("access_token").asText();

			// Decode the token and extract roles
			String[] parts = accessToken.split("\\.");
			String payload = new String(Base64.getDecoder().decode(parts[1]), "UTF-8");

			JsonNode tokenJson = mapper.readTree(payload);
			JsonNode rolesNode = tokenJson.path("resource_access").path("some-api").path("roles");

			if (rolesNode.isArray()) {
				for (JsonNode role : rolesNode) {
					roles.add(role.asText());
				}
			}

		} catch (Exception e) {
			throw new AuthenticationServiceException(e.getMessage());
		}


		List<GrantedAuthority> authorities = new ArrayList<>();

		for (String role : roles) {
			if ("admin_role".equals(role)) {
				authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
			}
		}
		if(authorities.isEmpty())
			throw new AuthenticationServiceException("Unauthorized user  or user not found");
		return new UsernamePasswordAuthenticationToken(username, password, authorities);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}

