package com.openkm.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openkm.core.Config;
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
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;
import java.net.URI;


class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {
	public static final String METHOD_NAME = "DELETE";

	public HttpDeleteWithBody(final String uri) {
		super();
		setURI(URI.create(uri));
	}

	@Override
	public String getMethod() {
		return METHOD_NAME;
	}
}


@Component
public class KeycloakUtils {

	private static final Logger log = LoggerFactory.getLogger(KeycloakUtils.class);

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
		if(instance == null) {
			instance = this;
		}
	}
	private String getAccessToken(String username, String password) throws IOException {
		
		String urlParameters = "grant_type=password"
			+ "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
			+ "&username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
			+ "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
			+ "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

		URL url = URI.create(tokenEndpoint).toURL();
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

		try (OutputStream os = conn.getOutputStream()) {
			os.write(urlParameters.getBytes(StandardCharsets.UTF_8));
		}

		String json;
		try (InputStream is = conn.getInputStream();
			 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			json = reader.lines().collect(Collectors.joining("\n"));
		}

		ObjectMapper mapper = new ObjectMapper();
		JsonNode root = mapper.readTree(json);
		return root.get("access_token").asText();
	}

	public List<GrantedAuthority> getAuthorities(String username,String password) {
		Set<String> roles = new HashSet<>();
		try {

			// Decode the token and extract roles
			String[] parts = this.getAccessToken(username,password).split("\\.");
			String payload = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
			ObjectMapper mapper = new ObjectMapper();
			JsonNode tokenJson = mapper.readTree(payload);
			JsonNode rolesNode = tokenJson.path("resource_access").path(this.clientId).path("roles");

			if (rolesNode.isArray()) {
				for (JsonNode role : rolesNode) {
					roles.add(role.asText());
				}
			}

		} catch (Exception e) {
			log.error(e.getMessage());
			throw new AuthenticationServiceException(e.getMessage());
		}


		List<GrantedAuthority> authorities = new ArrayList<>();
		if(roles.contains(Config.DEFAULT_ADMIN_ROLE))
			authorities.add(new SimpleGrantedAuthority(Config.DEFAULT_ADMIN_ROLE));
		else
			authorities.add(new SimpleGrantedAuthority(Config.DEFAULT_USER_ROLE));

		return authorities;
	}

	public void createUser(User userDetails) throws IOException {
		String token = getAccessToken("superuser", "sudo");
		HttpPost post = new HttpPost(kcBase + "/admin/realms/" + realmId + "/users");
		post.setHeader("Authorization", "Bearer " + token);
		post.setHeader("Content-Type", "application/json");

		ObjectMapper mapper = new ObjectMapper();
		ObjectNode user = mapper.createObjectNode();
		setJSON(userDetails, user);
		user.put("emailVerified", true);
		user.put("enabled", true);

		post.setEntity(new StringEntity(mapper.writeValueAsString(user), StandardCharsets.UTF_8));
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
				log.error("Failed to create user: " + response.getStatusLine());
				throw new RuntimeException("Error creating user");
			}
		}
	}

	public void updatePassword(String username, String newPassword) throws IOException {
		String token = getAccessToken("superuser", "sudo");
		String userInternalId = getInternalUserId(token, username);
		setPassword(token, userInternalId, newPassword);
	}

	private void updateKCEmail(String token, String userInternalId, String email) throws IOException {
		HttpPut put = new HttpPut(kcBase + "/admin/realms/" + realmId + "/users/" + userInternalId);
		put.setHeader("Authorization", "Bearer " + token);
		put.setHeader("Content-Type", "application/json");
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode newData = mapper.createObjectNode();
		newData.put("email", email);
		put.setEntity(new StringEntity(mapper.writeValueAsString(newData), StandardCharsets.UTF_8));

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(put)) {
			if(response.getStatusLine().getStatusCode() != 204){
				throw new RuntimeException("Failed to update user email: " + response.getStatusLine());
			}
		}
	}

	public void updateEmail(String username, String newEmail) throws IOException {
		String token = getAccessToken("superuser", "sudo");
		String userInternalId = getInternalUserId(token, username);
		updateKCEmail(token, userInternalId, newEmail);

	}

	public void updateUserData(User userDetails) throws IOException {
		String token = getAccessToken("superuser", "sudo");
		String userInternalId = getInternalUserId(token, userDetails.getId());
		HttpPut put = new HttpPut(kcBase + "/admin/realms/" + realmId + "/users/" + userInternalId);
		put.setHeader("Authorization", "Bearer " + token);
		put.setHeader("Content-Type", "application/json");

		ObjectMapper mapper = new ObjectMapper();
		ObjectNode user = mapper.createObjectNode();
		setJSON(userDetails, user);

		put.setEntity(new StringEntity(mapper.writeValueAsString(user), StandardCharsets.UTF_8));
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(put)) {
			if (response.getStatusLine().getStatusCode() == 204) {
				removeAllClientRolesFromUser(token, userInternalId);
				createRole(userDetails,token,userInternalId);
			} else {
				log.error("Failed to update user data: {}", response.getStatusLine());
				throw new RuntimeException("Error updating user");
			}
		}

	}

	private void setJSON(User userDetails, ObjectNode user) {
		String[] fullName = userDetails.getName().split(" ");

		if(fullName.length > 0) {
			user.put("firstName", fullName[0]);
			if(fullName.length > 1) {
				user.put("lastName", fullName[fullName.length - 1]);
			}else {
				user.put("lastName", "");
			}
		}
		else {
			user.put("firstName", "");
		}
		user.put("username", userDetails.getId());
		user.put("email", userDetails.getEmail());
	}

	private void setPassword(String token, String userId, String password) throws IOException {
		HttpPut put = new HttpPut(kcBase + "/admin/realms/" + realmId + "/users/" + userId + "/reset-password");
		put.setHeader("Authorization", "Bearer " + token);
		put.setHeader("Content-Type", "application/json");

		ObjectMapper mapper = new ObjectMapper();
		ObjectNode pwd = mapper.createObjectNode();
		pwd.put("type", "password");
		pwd.put("temporary", false);
		pwd.put("value", password);

		put.setEntity(new StringEntity(mapper.writeValueAsString(pwd), StandardCharsets.UTF_8));

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(put)) {
			if (response.getStatusLine().getStatusCode() != 204) {
				throw new RuntimeException("Failed to set password: " + response.getStatusLine());
			}
		}
	}

	private void createRole(User user, String token, String userId) throws IOException {
		Collection<Role> roles = user.getRoles();
		String roleName = "";
		for (Role role : roles) {
			if(role.getId().equals(Config.DEFAULT_ADMIN_ROLE)) {
				roleName = Config.DEFAULT_ADMIN_ROLE;
				break;
			}
		}
		if(roleName.isEmpty())
			roleName = Config.DEFAULT_USER_ROLE;
		String clientInternalId = getClientUUID(token,this.clientId);
		JsonNode keycloakRole = getClientRole(token,clientInternalId,roleName);
		assignClientRoleToUser(token,userId,clientInternalId,keycloakRole);

	}
	private String getClientUUID(String token, String clientName) throws IOException {
		HttpGet get = new HttpGet(kcBase + "/admin/realms/" + realmId + "/clients");
		get.setHeader("Authorization", "Bearer " + token);

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get)) {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode clients = mapper.readTree(EntityUtils.toString(response.getEntity()));
			for (JsonNode c : clients) {
				if (clientName.equals(c.path("clientId").asText())) {
					return c.path("id").asText();
				}
			}
			throw new RuntimeException("Client not found: " + clientName);
		}
	}

	private JsonNode getClientRole(String token, String clientUUID, String roleName) throws IOException {
		HttpGet get = new HttpGet(kcBase + "/admin/realms/" + realmId + "/clients/" + clientUUID + "/roles/" + roleName);
		get.setHeader("Authorization", "Bearer " + token);

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get)) {
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readTree(EntityUtils.toString(response.getEntity()));
		}
	}

	private void assignClientRoleToUser(String token, String userId, String clientUUID, JsonNode role) throws IOException {
		HttpPost post = new HttpPost(kcBase + "/admin/realms/" + realmId + "/users/" + userId + "/role-mappings/clients/" + clientUUID);
		post.setHeader("Authorization", "Bearer " + token);
		post.setHeader("Content-Type", "application/json");

		ObjectMapper mapper = new ObjectMapper();
		ArrayNode roles = mapper.createArrayNode();
		roles.add(role);

		post.setEntity(new StringEntity(mapper.writeValueAsString(roles), StandardCharsets.UTF_8));

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

			ObjectMapper mapper = new ObjectMapper();
			JsonNode users = mapper.readTree(EntityUtils.toString(response.getEntity()));
			if (users.size() > 0) {
				return users.path(0).path("id").asText();
			} else {
				throw new RuntimeException("User not found: " + username);
			}
		}
	}
	private void removeAllClientRolesFromUser(String token, String userId) throws IOException {
		String clientUUID = getClientUUID(token,clientId);
		HttpGet get = new HttpGet(kcBase + "/admin/realms/" + realmId + "/users/" + userId + "/role-mappings/clients/" + clientUUID);
		get.setHeader("Authorization", "Bearer " + token);

		ObjectMapper mapper = new ObjectMapper();
		JsonNode assignedRoles;
		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(get)) {

			int status = response.getStatusLine().getStatusCode();
			if (status != 200) {
				throw new RuntimeException("Failed to get assigned client roles: " + status);
			}
			assignedRoles = mapper.readTree(EntityUtils.toString(response.getEntity()));
		}

		if (assignedRoles.size() == 0) {
			return;
		}

		HttpDeleteWithBody delete = new HttpDeleteWithBody(kcBase + "/admin/realms/" + realmId + "/users/" + userId + "/role-mappings/clients/" + clientUUID);
		delete.setHeader("Authorization", "Bearer " + token);
		delete.setHeader("Content-Type", "application/json");
		delete.setEntity(new StringEntity(mapper.writeValueAsString(assignedRoles), StandardCharsets.UTF_8));

		try (CloseableHttpClient client = HttpClients.createDefault();
			 CloseableHttpResponse response = client.execute(delete)) {

			if (response.getStatusLine().getStatusCode() != 204) {
				throw new RuntimeException("Failed to remove client roles: " + response.getStatusLine());
			}
		}
	}

}
