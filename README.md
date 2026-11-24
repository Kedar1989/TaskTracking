# Task Tracking API

A Spring Boot REST API for user accounts, team projects, task assignment and tracking, comments, and file attachments.

## Requirements

- Java 21
- Gradle (or use the included Gradle wrapper)

## Run

On Windows:

```powershell
.\gradlew.bat bootRun
```

The API listens on `http://localhost:8081`. The default configuration uses an in-memory H2 database, so data is reset when the application stops. To use a persistent or production database, set `DB_URL`, `DB_USER`, and `DB_PASSWORD` for a supported JDBC database and configure its driver dependency.

Set `JWT_SECRET` to a unique secret of at least 32 bytes before deploying. `JWT_EXPIRATION_MS` defaults to two hours. Uploaded files are stored under `uploads` by default; change this with `UPLOAD_DIR`. Requests are limited to 10 MB per uploaded file. The H2 web console is disabled by default; enable it for local development with `H2_CONSOLE_ENABLED=true`.

## Web UI

The app includes a responsive browser UI served by Spring Boot at `http://localhost:8081/`. It supports account registration and login, profile updates, team creation and member invitations, task creation and assignment, task status and filtering, comments, and attachment uploads/downloads. Invitees must register before their email can be added to a team.

The UI stores the bearer token in the current browser tab's session storage and clears it when you sign out. No separate frontend installation or development server is needed.

## Run and test from IntelliJ IDEA

1. Install a JDK 21 and IntelliJ IDEA with the Gradle plugin enabled.
2. Open the project root folder (the one containing `build.gradle`) and choose to load it as a Gradle project.
3. In **File → Project Structure → Project**, set the Project SDK to JDK 21. In **Settings → Build, Execution, Deployment → Build Tools → Gradle**, set the Gradle JVM to JDK 21.
4. Open `src/main/java/com/airtribe/TaskTracking/TaskTrackingApplication.java` and run `TaskTrackingApplication.main()` using the gutter run icon. The default profile uses the in-memory H2 database and listens on port 8081.
5. Open `http://localhost:8081/`, register an account, and create a team. To try collaboration, register a second account in another browser profile/tab session, then add its email to the team. You can then create and assign team tasks, update their status, comment, and upload files.
6. To run automated tests, open the Gradle tool window and run **Tasks → verification → test**, or run `.\gradlew.bat test` in IntelliJ's terminal.

If port 8081 is already occupied, change `server.port` in `src/main/resources/application.properties`. For local H2 console access, set `H2_CONSOLE_ENABLED=true` and open `http://localhost:8081/h2-console`.

## Authentication

Register or log in to receive a signed bearer token. Supply it on protected requests:

```http
Authorization: Bearer <accessToken>
```

Passwords are stored as BCrypt hashes. Tokens are signed with HMAC-SHA256, expire after the configured lifetime, and are revoked by logout in the running application process. Because logout revocation is in-memory, use a shared token store when running multiple application instances.

## API

All request and response bodies use JSON except attachment uploads/downloads.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Register and receive an access token |
| `POST` | `/api/auth/login` | Authenticate and receive an access token |
| `POST` | `/api/auth/logout` | Revoke the current bearer token |
| `GET` | `/api/auth/me` | View the authenticated profile |
| `PUT` | `/api/auth/me` | Update name and contact number |
| `POST` | `/api/teams` | Create a team; optionally add existing users by email |
| `GET` | `/api/teams` | List teams the user belongs to |
| `GET` | `/api/teams/{teamId}` | View a team |
| `POST` | `/api/teams/{teamId}/members` | Add an existing user (owner only) |
| `POST` | `/api/tasks` | Create a task, optionally in a team and assign it |
| `GET` | `/api/tasks` | List accessible tasks with filters and sorting |
| `GET` | `/api/tasks/{taskId}` | View a task |
| `PUT` | `/api/tasks/{taskId}` | Update task details |
| `PATCH` | `/api/tasks/{taskId}/status` | Change task status |
| `DELETE` | `/api/tasks/{taskId}` | Delete a task (creator only) |
| `GET`, `POST` | `/api/tasks/{taskId}/comments` | List or add task comments |
| `GET`, `POST` | `/api/tasks/{taskId}/attachments` | List or upload task attachments |
| `GET` | `/api/tasks/{taskId}/attachments/{attachmentId}` | Download an attachment |

Task statuses are `OPEN`, `IN_PROGRESS`, and `COMPLETED`. Task listing supports optional `status`, `search`, `teamId`, and `assignedToMe` query parameters, plus `sort` (`title`, `dueDate`, `status`, `createdAt`, or `updatedAt`) and `direction` (`asc` or `desc`).

Example registration:

```json
{
  "name": "Alex",
  "email": "alex@example.com",
  "password": "a-long-password",
  "contactNumber": "555-0100"
}
```

Example task:

```json
{
  "title": "Prepare release notes",
  "description": "Summarize changes for the next release.",
  "dueDate": "2026-12-01",
  "teamId": 1,
  "assigneeEmail": "alex@example.com"
}
```

Upload an attachment as `multipart/form-data` using the `file` field. Team tasks and their comments and attachments are visible only to team members; a user can also access tasks they created or are assigned to.

## Validate

```powershell
.\gradlew.bat test
```
