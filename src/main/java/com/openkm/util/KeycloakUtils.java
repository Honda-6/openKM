package com.openkm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openkm.core.DatabaseException;
import com.openkm.dao.bean.Role;
import com.openkm.dao.bean.User;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class KeycloakUtils {

	private static KeycloakUtils instance;

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

	public static KeycloakUtils getInstance(){
		return instance;
	}
	private KeycloakUtils() {
		instance = this;
	}
	private String getAccessToken(String username, String password) throws IOException {

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

	public void createUser(User userDetails) throws IOException {
		String token = getAccessToken("superuser", "sudo");
		HttpPost post = new HttpPost(kcBase + "/admin/realms/" + realmId + "/users");
		post.setHeader("Authorization", "Bearer " + token);
		post.setHeader("Content-Type", "application/json");

		JSONObject user = new JSONObject();
		String[] fullName = userDetails.getName().split(" ");

		if(fullName.length > 0) {
			user.put("firstName", fullName[0]);
			if(fullName.length > 1) {
				user.put("lastName", fullName[fullName.length - 1]);
			}else {
				user.put("lastName","");
			}
		}
		else {
			user.put("firstName","");
		}
		user.put("username", userDetails.getId());
		user.put("email", userDetails.getEmail());
		user.put("emailVerified",true);
		user.put("enabled", true);

		post.setEntity(new StringEntity(user.toString()));

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(post)) {
			if (response.getStatusLine().getStatusCode() == 201) {
				Header location = response.getFirstHeader("Location");
				if (location != null) {
					String userId = location.getValue().replaceAll(".*/", "");
					setPassword(token, userId, userDetails.getPassword());
					createRole(userDetails,token,userId);
				}
			} else {
				System.err.println("Failed to create user: " + response.getStatusLine());
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

		try {
			CloseableHttpClient client = HttpClients.createDefault();
			client.execute(put);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			throw e;
		}
	}
	private void createRole(User user, String token, String userId) throws IOException {
		Collection<Role> roles = user.getRoles();
		String roleName = "";
		for (Role role : roles) {
			if(role.getId().equals("ROLE_ADMIN")) {
				roleName = "admin_role";
				break;
			}
		}
		if(roleName.isEmpty())
			roleName = "user_role";
		String clientInternalId = getClientUUID(token,this.clientId);
		JSONObject keycloakRole = getClientRole(token,clientInternalId,roleName);
		assignClientRoleToUser(token,userId,clientInternalId,keycloakRole);

	}
	private String getClientUUID(String token, String clientName) throws IOException {
		HttpGet get = new HttpGet(kcBase + "/admin/realms/" + realmId + "/clients");
		get.setHeader("Authorization", "Bearer " + token);

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get)) {
			JSONArray clients = new JSONArray(EntityUtils.toString(response.getEntity()));
			for (int i = 0; i < clients.length(); i++) {
				JSONObject c = clients.getJSONObject(i);
				if (clientName.equals(c.getString("clientId"))) {
					return c.getString("id");
				}
			}
			throw new RuntimeException("Client not found: " + clientName);
		}
	}

	private JSONObject getClientRole(String token, String clientUUID, String roleName) throws IOException {
		HttpGet get = new HttpGet(kcBase + "/admin/realms/" + realmId + "/clients/" + clientUUID + "/roles/" + roleName);
		get.setHeader("Authorization", "Bearer " + token);

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get)) {
			return new JSONObject(EntityUtils.toString(response.getEntity()));
		}
	}

	private void assignClientRoleToUser(String token, String userId, String clientUUID, JSONObject role) throws IOException {
		HttpPost post = new HttpPost(kcBase + "/admin/realms/" + realmId + "/users/" + userId + "/role-mappings/clients/" + clientUUID);
		post.setHeader("Authorization", "Bearer " + token);
		post.setHeader("Content-Type", "application/json");

		JSONArray roles = new JSONArray();
		roles.put(role);

		post.setEntity(new StringEntity(roles.toString()));

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(post)) {
			if (response.getStatusLine().getStatusCode() != 204) {
				throw new RuntimeException("Failed to assign client role: " + response.getStatusLine());
			}
		}
	}
	public void deleteUser(User user) throws IOException {
		String token = getAccessToken("superuser", "sudo");
		String internalUserId = getInternalUserId(token,user.getId());
		deleteKCUser(token,internalUserId);
	}
	private void deleteKCUser(String token, String userId) throws IOException {
		HttpDelete delete = new HttpDelete(kcBase + "/admin/realms/" + realmId + "/users/" + userId);
		delete.setHeader("Authorization", "Bearer " + token);

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(delete)) {

			int status = response.getStatusLine().getStatusCode();
			if (status == 404) {
				throw new RuntimeException("User not found.");
			} else if(status != 204) {
				throw new RemoteException("Failed to delete user: " + status + "\n" + EntityUtils.toString(response.getEntity()));
			}
		}
	}
	private String getInternalUserId(String token, String username) throws IOException {
		HttpGet get = new HttpGet(kcBase + "/admin/realms/" + realmId + "/users?username=" + username);
		get.setHeader("Authorization", "Bearer " + token);

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get)) {

			JSONArray users = new JSONArray(EntityUtils.toString(response.getEntity()));
			if (users.length() > 0) {
				return users.getJSONObject(0).getString("id");
			} else {
				throw new RuntimeException("User not found: " + username);
			}
		}
	}
}
