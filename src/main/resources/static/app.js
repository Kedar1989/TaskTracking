(() => {
    const state = {
        token: sessionStorage.getItem("taskflow-token"),
        user: null,
        teams: [],
        tasks: []
    };

    const byId = (id) => document.getElementById(id);
    const authView = byId("authView");
    const appView = byId("appView");
    const authMessage = byId("authMessage");
    const appMessage = byId("appMessage");
    const taskList = byId("taskList");
    const emptyTasks = byId("emptyTasks");
    const taskFormPanel = byId("taskFormPanel");
    let messageTimer;

    function escapeHtml(value) {
        return String(value ?? "").replace(/[&<>"']/g, (character) => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
        })[character]);
    }

    function initials(value) {
        return (value || "T").trim().charAt(0).toUpperCase();
    }

    function setMessage(element, message, success = false) {
        element.textContent = message || "";
        element.classList.toggle("success", success);
        if (element === appMessage && message) {
            window.clearTimeout(messageTimer);
            messageTimer = window.setTimeout(() => setMessage(element, ""), 5000);
        }
    }

    async function request(path, options = {}) {
        const headers = new Headers(options.headers || {});
        if (state.token) headers.set("Authorization", `Bearer ${state.token}`);
        const isFormData = options.body instanceof FormData;
        if (options.body && !isFormData && typeof options.body !== "string") {
            headers.set("Content-Type", "application/json");
            options = { ...options, body: JSON.stringify(options.body) };
        }
        const response = await fetch(path, { ...options, headers });
        if (response.status === 204) return null;
        const contentType = response.headers.get("content-type") || "";
        const result = contentType.includes("application/json") ? await response.json() : await response.text();
        if (!response.ok) {
            const details = typeof result === "string" ? result : Object.values(result).join(" ");
            const error = new Error(details || `Request failed (${response.status})`);
            error.status = response.status;
            throw error;
        }
        return result;
    }

    function showAuth(mode = "login", message = "") {
        state.token = null;
        state.user = null;
        sessionStorage.removeItem("taskflow-token");
        appView.classList.add("hidden");
        authView.classList.remove("hidden");
        byId("profilePanel").classList.add("hidden");
        showAuthMode(mode);
        setMessage(authMessage, message);
    }

    function showAuthMode(mode) {
        const registering = mode === "register";
        byId("loginForm").classList.toggle("hidden", registering);
        byId("registerForm").classList.toggle("hidden", !registering);
        byId("showLogin").classList.toggle("active", !registering);
        byId("showRegister").classList.toggle("active", registering);
        byId("authTitle").textContent = registering ? "Create your account" : "Welcome back";
        byId("authSubtitle").textContent = registering
            ? "Set up your account and get your team organized."
            : "Sign in to pick up where you left off.";
        setMessage(authMessage, "");
    }

    async function acceptAuth(response) {
        state.token = response.accessToken;
        sessionStorage.setItem("taskflow-token", state.token);
        state.user = response.user;
        authView.classList.add("hidden");
        appView.classList.remove("hidden");
        await refreshWorkspace();
    }

    function renderTeams() {
        const options = state.teams.map((team) =>
            `<option value="${team.id}">${escapeHtml(team.name)}</option>`).join("");
        byId("teamFilter").innerHTML = `<option value="">All teams</option>${options}`;
        byId("taskTeam").innerHTML = `<option value="">Personal task</option>${options}`;
        byId("teamCount").textContent = `${state.teams.length} ${state.teams.length === 1 ? "team" : "teams"}`;
        byId("emptyTeams").classList.toggle("hidden", state.teams.length > 0);
        byId("teamList").innerHTML = state.teams.map((team) => `
            <div class="team-item">
                <span class="team-avatar">${escapeHtml(initials(team.name))}</span>
                <span><strong>${escapeHtml(team.name)}</strong>
                <small>${team.members.length} ${team.members.length === 1 ? "member" : "members"} · ${escapeHtml(team.owner.name)}</small></span>
            </div>
            ${team.owner.id === state.user?.id ? `<form class="team-invite-form" data-team-id="${team.id}">
                <input name="email" type="email" placeholder="Invite by existing account email" required>
                <button class="text-button" type="submit">Add member</button>
            </form>` : ""}`).join("");
        renderAssigneeOptions();
    }

    function renderAssigneeOptions() {
        const teamId = byId("taskTeam").value;
        const team = state.teams.find((item) => String(item.id) === teamId);
        const select = byId("taskAssignee");
        select.disabled = !team;
        select.innerHTML = `<option value="">Unassigned</option>` + (team ? team.members.map((member) =>
            `<option value="${escapeHtml(member.email)}">${escapeHtml(member.name)} · ${escapeHtml(member.email)}</option>`
        ).join("") : "");
    }

    function taskQuery() {
        const formData = new FormData(byId("filterForm"));
        const query = new URLSearchParams();
        for (const key of ["search", "status", "teamId"]) {
            const value = String(formData.get(key) || "").trim();
            if (value) query.set(key, value);
        }
        if (formData.get("assignedToMe")) query.set("assignedToMe", "true");
        query.set("sort", "updatedAt");
        query.set("direction", "desc");
        return query.toString();
    }

    function formatDate(date) {
        if (!date) return "";
        return new Date(`${date}T00:00:00`).toLocaleDateString(undefined, {
            month: "short", day: "numeric", year: "numeric"
        });
    }

    function statusLabel(status) {
        return ({ OPEN: "Open", IN_PROGRESS: "In progress", COMPLETED: "Completed" })[status] || status;
    }

    function renderTasks() {
        const completed = state.tasks.filter((task) => task.status === "COMPLETED").length;
        const inProgress = state.tasks.filter((task) => task.status === "IN_PROGRESS").length;
        byId("allCount").textContent = state.tasks.length;
        byId("completedCount").textContent = completed;
        byId("progressCount").textContent = inProgress;
        byId("visibleCount").textContent = state.tasks.length;
        emptyTasks.classList.toggle("hidden", state.tasks.length > 0);
        taskList.innerHTML = state.tasks.map((task) => {
            const team = task.teamName ? `<span>◉ ${escapeHtml(task.teamName)}</span>` : "";
            const due = task.dueDate ? `<span>◷ Due ${escapeHtml(formatDate(task.dueDate))}</span>` : "";
            const assignee = task.assigneeName ? `<span>Assigned to ${escapeHtml(task.assigneeName)}</span>` : "<span>Unassigned</span>";
            const deleteAction = task.createdById === state.user?.id
                ? `<button class="text-button" type="button" data-action="delete">Delete</button>` : "";
            return `<article class="task-card" data-task-id="${task.id}">
                <div class="task-main">
                    <div>
                        <div class="task-title-row"><h3 class="task-title">${escapeHtml(task.title)}</h3></div>
                        ${task.description ? `<p class="task-description">${escapeHtml(task.description)}</p>` : ""}
                        <div class="task-meta">${team}${due}${assignee}</div>
                        <div class="task-actions"><button class="text-button" type="button" data-action="details">Comments &amp; files</button>
                        ${deleteAction}</div>
                    </div>
                    <select class="status-select status-${escapeHtml(task.status.toLowerCase())}" data-action="status" aria-label="Status for ${escapeHtml(task.title)}">
                        ${["OPEN", "IN_PROGRESS", "COMPLETED"].map((status) =>
                            `<option value="${status}" ${status === task.status ? "selected" : ""}>${statusLabel(status)}</option>`
                        ).join("")}
                    </select>
                </div>
                <div class="task-details hidden">
                    <div class="detail-grid">
                        <section class="detail-section">
                            <h4>Comments</h4>
                            <div class="comment-list"><span class="field-help">Loading comments…</span></div>
                            <form class="comment-form">
                                <input name="content" maxlength="5000" placeholder="Write a comment…" required>
                                <button class="button button-primary button-small" type="submit">Send</button>
                            </form>
                        </section>
                        <section class="detail-section">
                            <h4>Attachments</h4>
                            <div class="attachment-list"><span class="field-help">Loading attachments…</span></div>
                            <form class="upload-form">
                                <input name="file" type="file" required>
                                <button class="button button-outline button-small" type="submit">Upload</button>
                            </form>
                        </section>
                    </div>
                </div>
            </article>`;
        }).join("");
    }

    async function refreshWorkspace() {
        try {
            const [user, teams] = await Promise.all([
                request("/api/auth/me"),
                request("/api/teams")
            ]);
            state.user = user;
            state.teams = teams;
            byId("userName").textContent = user.name;
            byId("userAvatar").textContent = initials(user.name);
            byId("profileForm").elements.name.value = user.name;
            byId("profileForm").elements.contactNumber.value = user.contactNumber || "";
            renderTeams();
            await loadTasks();
        } catch (error) {
            if (error.status === 401) {
                showAuth("login", "Your session expired. Please sign in again.");
            } else {
                setMessage(appMessage, error.message);
            }
        }
    }

    async function loadTasks() {
        try {
            state.tasks = await request(`/api/tasks?${taskQuery()}`);
            renderTasks();
        } catch (error) {
            setMessage(appMessage, error.message);
        }
    }

    async function loadTaskDetails(card) {
        const taskId = card.dataset.taskId;
        const details = card.querySelector(".task-details");
        details.classList.remove("hidden");
        const [comments, attachments] = await Promise.all([
            request(`/api/tasks/${taskId}/comments`),
            request(`/api/tasks/${taskId}/attachments`)
        ]);
        card.querySelector(".comment-list").innerHTML = comments.length ? comments.map((comment) => `
            <div class="comment-item"><strong>${escapeHtml(comment.authorName)} · ${escapeHtml(new Date(comment.createdAt).toLocaleString())}</strong>
            ${escapeHtml(comment.content)}</div>`).join("") : `<span class="field-help">No comments yet.</span>`;
        card.querySelector(".attachment-list").innerHTML = attachments.length ? attachments.map((attachment) => `
            <div class="attachment-item"><span aria-hidden="true">↳</span>
            <button type="button" class="text-button" data-action="download" data-attachment-id="${attachment.id}">${escapeHtml(attachment.fileName)}</button>
            <span>(${Math.ceil(attachment.size / 1024)} KB)</span></div>`).join("") : `<span class="field-help">No attachments yet.</span>`;
    }

    byId("showLogin").addEventListener("click", () => showAuthMode("login"));
    byId("showRegister").addEventListener("click", () => showAuthMode("register"));

    byId("loginForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        try {
            const response = await request("/api/auth/login", {
                method: "POST",
                body: { email: form.get("email"), password: form.get("password") }
            });
            await acceptAuth(response);
        } catch (error) {
            setMessage(authMessage, error.message);
        }
    });

    byId("registerForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        try {
            const response = await request("/api/auth/register", {
                method: "POST",
                body: {
                    name: form.get("name"),
                    email: form.get("email"),
                    password: form.get("password")
                }
            });
            await acceptAuth(response);
        } catch (error) {
            setMessage(authMessage, error.message);
        }
    });

    byId("logoutButton").addEventListener("click", async () => {
        try {
            await request("/api/auth/logout", { method: "POST" });
            showAuth("login", "You have signed out.");
        } catch (error) {
            setMessage(appMessage, error.message);
        }
    });

    byId("profileToggle").addEventListener("click", () => {
        const panel = byId("profilePanel");
        const open = panel.classList.toggle("hidden") === false;
        byId("profileToggle").setAttribute("aria-expanded", String(open));
    });

    byId("profileForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        try {
            const user = await request("/api/auth/me", {
                method: "PUT",
                body: { name: form.get("name"), contactNumber: form.get("contactNumber") || null }
            });
            state.user = user;
            byId("userName").textContent = user.name;
            byId("userAvatar").textContent = initials(user.name);
            byId("profilePanel").classList.add("hidden");
            setMessage(appMessage, "Profile updated.", true);
        } catch (error) {
            setMessage(appMessage, error.message);
        }
    });

    byId("teamForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const memberEmails = String(form.get("memberEmails") || "").split(",").map((email) => email.trim()).filter(Boolean);
        try {
            await request("/api/teams", {
                method: "POST",
                body: {
                    name: form.get("name"),
                    description: form.get("description") || null,
                    memberEmails
                }
            });
            event.currentTarget.reset();
            const teams = await request("/api/teams");
            state.teams = teams;
            renderTeams();
            setMessage(appMessage, "Team created.", true);
        } catch (error) {
            setMessage(appMessage, error.message);
        }
    });

    byId("teamList").addEventListener("submit", async (event) => {
        if (!event.target.matches(".team-invite-form")) return;
        event.preventDefault();
        const form = new FormData(event.target);
        const teamId = event.target.dataset.teamId;
        try {
            await request(`/api/teams/${teamId}/members`, {
                method: "POST",
                body: { email: form.get("email") }
            });
            const teams = await request("/api/teams");
            state.teams = teams;
            renderTeams();
            setMessage(appMessage, "Team member added.", true);
        } catch (error) {
            setMessage(appMessage, error.message);
        }
    });

    function openTaskForm() {
        taskFormPanel.classList.remove("hidden");
        byId("taskForm").elements.title.focus();
        taskFormPanel.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }
    byId("newTaskButton").addEventListener("click", openTaskForm);
    byId("newTaskShortcut").addEventListener("click", openTaskForm);
    document.querySelectorAll("[data-close-task]").forEach((button) => button.addEventListener("click", () => {
        taskFormPanel.classList.add("hidden");
    }));

    byId("taskTeam").addEventListener("change", renderAssigneeOptions);
    byId("taskForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const body = {
            title: form.get("title"),
            description: form.get("description") || null,
            dueDate: form.get("dueDate") || null,
            teamId: form.get("teamId") || null,
            assigneeEmail: form.get("assigneeEmail") || null
        };
        if (body.teamId) body.teamId = Number(body.teamId);
        try {
            await request("/api/tasks", { method: "POST", body });
            event.currentTarget.reset();
            renderAssigneeOptions();
            taskFormPanel.classList.add("hidden");
            await loadTasks();
            setMessage(appMessage, "Task created.", true);
        } catch (error) {
            setMessage(appMessage, error.message);
        }
    });

    byId("filterForm").addEventListener("submit", (event) => {
        event.preventDefault();
        loadTasks();
    });

    taskList.addEventListener("change", async (event) => {
        if (!event.target.matches('[data-action="status"]')) return;
        const card = event.target.closest(".task-card");
        try {
            await request(`/api/tasks/${card.dataset.taskId}/status`, {
                method: "PATCH",
                body: { status: event.target.value }
            });
            await loadTasks();
            setMessage(appMessage, "Task status updated.", true);
        } catch (error) {
            setMessage(appMessage, error.message);
        }
    });

    taskList.addEventListener("click", async (event) => {
        const button = event.target.closest("[data-action]");
        if (!button) return;
        const card = button.closest(".task-card");
        const taskId = card.dataset.taskId;
        try {
            if (button.dataset.action === "details") {
                if (!card.querySelector(".task-details").classList.contains("hidden")) {
                    card.querySelector(".task-details").classList.add("hidden");
                } else {
                    await loadTaskDetails(card);
                }
            } else if (button.dataset.action === "delete") {
                if (!window.confirm("Delete this task and its comments and attachments?")) return;
                await request(`/api/tasks/${taskId}`, { method: "DELETE" });
                await loadTasks();
                setMessage(appMessage, "Task deleted.", true);
            } else if (button.dataset.action === "download") {
                const response = await fetch(`/api/tasks/${taskId}/attachments/${button.dataset.attachmentId}`, {
                    headers: { Authorization: `Bearer ${state.token}` }
                });
                if (!response.ok) throw new Error("Could not download this attachment.");
                const blob = await response.blob();
                const url = URL.createObjectURL(blob);
                const link = document.createElement("a");
                link.href = url;
                link.download = button.textContent;
                link.click();
                URL.revokeObjectURL(url);
            }
        } catch (error) {
            setMessage(appMessage, error.message);
        }
    });

    taskList.addEventListener("submit", async (event) => {
        event.preventDefault();
        const card = event.target.closest(".task-card");
        const taskId = card.dataset.taskId;
        try {
            if (event.target.matches(".comment-form")) {
                const form = new FormData(event.target);
                await request(`/api/tasks/${taskId}/comments`, {
                    method: "POST",
                    body: { content: form.get("content") }
                });
                event.target.reset();
                await loadTaskDetails(card);
            } else if (event.target.matches(".upload-form")) {
                const form = new FormData(event.target);
                await request(`/api/tasks/${taskId}/attachments`, { method: "POST", body: form });
                event.target.reset();
                await loadTaskDetails(card);
                setMessage(appMessage, "Attachment uploaded.", true);
            }
        } catch (error) {
            setMessage(appMessage, error.message);
        }
    });

    if (state.token) {
        authView.classList.add("hidden");
        appView.classList.remove("hidden");
        refreshWorkspace();
    } else {
        showAuth("login");
    }
})();
