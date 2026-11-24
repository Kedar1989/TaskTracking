package com.airtribe.TaskTracking;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.upload-dir=build/test-uploads")
class TaskTrackingApplicationTests {
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	@Value("${local.server.port}")
	private int port;

	@Test
	void supportsAuthenticatedTeamTaskAndCommentFlow() throws Exception {
		HttpResponse<String> page = send("GET", "/", null, null);
		Assertions.assertEquals(200, page.statusCode());
		Assertions.assertTrue(page.body().contains("Taskflow"));
		Assertions.assertEquals(200, send("GET", "/app.js", null, null).statusCode());
		Assertions.assertEquals(200, send("GET", "/styles.css", null, null).statusCode());

		String ownerToken = register("owner@example.com", "Owner");
		String memberToken = register("member@example.com", "Member");

		HttpResponse<String> createTeam = send("POST", "/api/teams",
				"{\"name\":\"Engineering\",\"memberEmails\":[\"member@example.com\"]}", ownerToken);
		Assertions.assertEquals(201, createTeam.statusCode(), createTeam.body());
		long teamId = numberField(createTeam.body(), "id");

		HttpResponse<String> createTask = send("POST", "/api/tasks",
				"{\"title\":\"Prepare release\",\"description\":\"Draft notes\",\"teamId\":"
						+ teamId + ",\"assigneeEmail\":\"member@example.com\"}", ownerToken);
		Assertions.assertEquals(201, createTask.statusCode(), createTask.body());
		long taskId = numberField(createTask.body(), "id");

		Assertions.assertEquals(200, send("GET", "/api/tasks/" + taskId, null, memberToken).statusCode());
		Assertions.assertEquals(200, send("PATCH", "/api/tasks/" + taskId + "/status",
				"{\"status\":\"COMPLETED\"}", memberToken).statusCode());
		Assertions.assertTrue(send("GET",
				"/api/tasks?status=COMPLETED&search=release&sort=title&direction=asc",
				null, ownerToken).body().contains("Prepare release"));
		HttpResponse<String> comment = send("POST", "/api/tasks/" + taskId + "/comments",
				"{\"content\":\"I will take this\"}", memberToken);
		Assertions.assertEquals(201, comment.statusCode(), comment.body());
		Assertions.assertTrue(send("GET", "/api/tasks/" + taskId + "/comments", null, ownerToken)
				.body().contains("I will take this"));
		Assertions.assertTrue(send("GET", "/api/tasks?assignedToMe=true", null, memberToken)
				.body().contains("Prepare release"));

		HttpResponse<String> attachment = upload(taskId, memberToken);
		Assertions.assertEquals(201, attachment.statusCode(), attachment.body());
		long attachmentId = numberField(attachment.body(), "id");
		HttpResponse<String> downloaded = send("GET",
				"/api/tasks/" + taskId + "/attachments/" + attachmentId, null, ownerToken);
		Assertions.assertEquals(200, downloaded.statusCode());
		Assertions.assertEquals("attachment contents", downloaded.body());

		Assertions.assertEquals(204, send("DELETE", "/api/tasks/" + taskId, null, ownerToken).statusCode());
		Assertions.assertEquals(200, send("POST", "/api/auth/login",
				"{\"email\":\"owner@example.com\",\"password\":\"a-long-password\"}", null).statusCode());
		Assertions.assertEquals(204, send("POST", "/api/auth/logout", null, ownerToken).statusCode());
		Assertions.assertEquals(401, send("GET", "/api/auth/me", null, ownerToken).statusCode());
	}

	private String register(String email, String name) throws IOException, InterruptedException {
		HttpResponse<String> response = send("POST", "/api/auth/register",
				"{\"name\":\"" + name + "\",\"email\":\"" + email + "\","
						+ "\"password\":\"a-long-password\"}", null);
		Assertions.assertEquals(201, response.statusCode(), response.body());
		return stringField(response.body(), "accessToken");
	}

	private HttpResponse<String> send(String method, String path, String body, String token)
			throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + path));
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		if (body == null) {
			request.method(method, HttpRequest.BodyPublishers.noBody());
		} else {
			request.header("Content-Type", "application/json")
					.method(method, HttpRequest.BodyPublishers.ofString(body));
		}
		return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> upload(long taskId, String token) throws IOException, InterruptedException {
		String boundary = "----TaskTrackingTestBoundary";
		String content = "--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"file\"; filename=\"notes.txt\"\r\n"
				+ "Content-Type: text/plain\r\n\r\n"
				+ "attachment contents\r\n"
				+ "--" + boundary + "--\r\n";
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/api/tasks/" + taskId + "/attachments"))
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(HttpRequest.BodyPublishers.ofByteArray(content.getBytes(StandardCharsets.UTF_8)))
				.build();
		return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private String stringField(String json, String field) {
		Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
		Assertions.assertTrue(matcher.find(), "Missing field '" + field + "' in " + json);
		return matcher.group(1);
	}

	private long numberField(String json, String field) {
		Matcher matcher = Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
		Assertions.assertTrue(matcher.find(), "Missing field '" + field + "' in " + json);
		return Long.parseLong(matcher.group(1));
	}
}
