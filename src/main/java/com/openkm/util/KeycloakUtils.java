package com.openkm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openkm.spring.PrincipalUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class KeycloakUtils {
	@Value("${tokenEndpoint}")
	private String tokenEndpoint;

	@Value("${clientId}")
	private String clientId;

	@Value("${clientSecret}")
	private String clientSecret;

	@Value("${realmURL}")
	private String realmURL;

	@Value("${kcBase}")
	private String kcBase;

	@Value("${realmId}")
	private String realmId;

	public KeycloakUtils() {}

	private String getAccessToken(String username, String password) throws IOException {
		System.out.println(clientId);
		System.out.println(clientSecret);
		System.out.println(tokenEndpoint);
		System.out.println(realmURL);
		System.out.println(kcBase);
		System.out.println(realmId);

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
		return root.get("access_token").asText();
	}

	public List<GrantedAuthority> getAuthorities(String username,String password) {
		Set<String> roles = new HashSet<>();
		try {

			// Decode the token and extract roles
			String[] parts = this.getAccessToken(username,password).split("\\.");
			String payload = new String(Base64.getDecoder().decode(parts[1]), "UTF-8");
			ObjectMapper mapper = new ObjectMapper();
			JsonNode tokenJson = mapper.readTree(payload);
			JsonNode rolesNode = tokenJson.path("resource_access").path("some-api").path("roles");

			if (rolesNode.isArray()) {
				for (JsonNode role : rolesNode) {
					roles.add(role.asText());
				}
			}

		} catch (Exception e) {
			System.err.println(e.getMessage());
			throw new AuthenticationServiceException(e.getMessage());
		}


		List<GrantedAuthority> authorities = new ArrayList<>();

		for (String role : roles) {
			if ("admin_role".equals(role)) {
				authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
			}
		}
		return authorities;
	}

	public void createUser(String username, String password) throws IOException {
		String token = getAccessToken(PrincipalUtils.getUser(), PrincipalUtils.getAuthentication().getCredentials().toString());
		HttpPost post = new HttpPost(kcBase + "/admin/realms/" + realmId + "/users");
		post.setHeader("Authorization", "Bearer " + token);
		post.setHeader("Content-Type", "application/json");

		JSONObject user = new JSONObject();
		user.put("username", username);
		user.put("enabled", true);

		post.setEntity(new StringEntity(user.toString()));

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(post)) {
			if (response.getStatusLine().getStatusCode() == 201) {
				System.out.println("User created.");
				Header location = response.getFirstHeader("Location");
				if (location != null) {
					String userId = location.getValue().replaceAll(".*/", "");
					setPassword(token, userId, password);
				}
			} else {
				System.out.println("Failed to create user: " + response.getStatusLine());
			}
		}
	}

	private void setPassword(String token, String userId, String password) throws IOException {
		HttpPut put = new HttpPut(kcBase + "/admin/realms/" + realmId + "/users/" + userId + "/reset-password");
		put.setHeader("Authorization", "Bearer " + token);
		put.setHeader("Content-Type", "application/json");

		JSONObject pwd = new JSONObject();
		pwd.put("type", "password");
		pwd.put("temporary", false);
		pwd.put("value", password);

		put.setEntity(new StringEntity(pwd.toString()));

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(put)) {
			System.out.println("Password set: " + response.getStatusLine());
		}
	}
}
