"use strict";

const DEFAULT_SERVER_URL = "http://localhost:8989";
const KEYS = {
  serverUrl: "ado.webLocal.serverUrl",
  projects: "ado.webLocal.projects",
  tasksByProject: "ado.webLocal.tasksByProject",
  taskDetails: "ado.webLocal.taskDetails",
  subtasksByTask: "ado.webLocal.subtasksByTask",
};

const state = {
  serverUrl: normalizeServerUrl(localStorage.getItem(KEYS.serverUrl) || DEFAULT_SERVER_URL),
  projects: readCache(KEYS.projects, []),
  selectedProjectId: null,
  tasksByProject: readCache(KEYS.tasksByProject, {}),
  selectedTaskId: null,
  taskDetails: readCache(KEYS.taskDetails, {}),
  subtasksByTask: readCache(KEYS.subtasksByTask, {}),
  showingProjectForm: false,
  showingTaskForm: false,
  showingSubtaskForm: false,
  taskFormMode: "single",
  subtaskFormMode: "single",
  status: "idle",
  message: "",
  messageKind: "",
};

const els = {};

document.addEventListener("DOMContentLoaded", () => {
  els.connectionStatus = document.getElementById("connectionStatus");
  els.serverUrl = document.getElementById("serverUrl");
  els.saveServerUrl = document.getElementById("saveServerUrl");
  els.testConnection = document.getElementById("testConnection");
  els.refreshProjects = document.getElementById("refreshProjects");
  els.showProjectForm = document.getElementById("showProjectForm");
  els.projectForm = document.getElementById("projectForm");
  els.projectName = document.getElementById("projectName");
  els.projectDescription = document.getElementById("projectDescription");
  els.projectTags = document.getElementById("projectTags");
  els.cancelProjectForm = document.getElementById("cancelProjectForm");
  els.messageArea = document.getElementById("messageArea");
  els.projectList = document.getElementById("projectList");
  els.showTaskForm = document.getElementById("showTaskForm");
  els.taskForm = document.getElementById("taskForm");
  els.taskModeSingle = document.getElementById("taskModeSingle");
  els.taskModeBulk = document.getElementById("taskModeBulk");
  els.taskSingleFields = document.getElementById("taskSingleFields");
  els.taskBulkFields = document.getElementById("taskBulkFields");
  els.taskName = document.getElementById("taskName");
  els.taskDescription = document.getElementById("taskDescription");
  els.taskBulk = document.getElementById("taskBulk");
  els.cancelTaskForm = document.getElementById("cancelTaskForm");
  els.projectActions = document.getElementById("projectActions");
  els.taskList = document.getElementById("taskList");
  els.showSubtaskForm = document.getElementById("showSubtaskForm");
  els.subtaskForm = document.getElementById("subtaskForm");
  els.subtaskModeSingle = document.getElementById("subtaskModeSingle");
  els.subtaskModeBulk = document.getElementById("subtaskModeBulk");
  els.subtaskSingleFields = document.getElementById("subtaskSingleFields");
  els.subtaskBulkFields = document.getElementById("subtaskBulkFields");
  els.subtaskName = document.getElementById("subtaskName");
  els.subtaskDescription = document.getElementById("subtaskDescription");
  els.subtaskBulk = document.getElementById("subtaskBulk");
  els.cancelSubtaskForm = document.getElementById("cancelSubtaskForm");
  els.taskDetail = document.getElementById("taskDetail");

  els.serverUrl.value = state.serverUrl;
  els.saveServerUrl.addEventListener("click", saveServerUrl);
  els.testConnection.addEventListener("click", onTestConnection);
  els.refreshProjects.addEventListener("click", loadProjects);
  els.showProjectForm.addEventListener("click", showProjectForm);
  els.cancelProjectForm.addEventListener("click", hideProjectForm);
  els.projectForm.addEventListener("submit", onCreateProject);
  els.showTaskForm.addEventListener("click", showTaskForm);
  els.taskModeSingle.addEventListener("click", () => setTaskFormMode("single"));
  els.taskModeBulk.addEventListener("click", () => setTaskFormMode("bulk"));
  els.cancelTaskForm.addEventListener("click", hideTaskForm);
  els.taskForm.addEventListener("submit", onCreateTask);
  els.showSubtaskForm.addEventListener("click", showSubtaskForm);
  els.subtaskModeSingle.addEventListener("click", () => setSubtaskFormMode("single"));
  els.subtaskModeBulk.addEventListener("click", () => setSubtaskFormMode("bulk"));
  els.cancelSubtaskForm.addEventListener("click", hideSubtaskForm);
  els.subtaskForm.addEventListener("submit", onCreateSubtask);

  render();
  loadProjects();
});

function normalizeServerUrl(input) {
  let value = String(input || "").trim();
  if (!value) return DEFAULT_SERVER_URL;
  if (!/^https?:\/\//i.test(value)) {
    value = `http://${value}`;
  }
  return value.replace(/\/+$/, "");
}

async function apiFetch(path, options = {}) {
  const url = `${state.serverUrl}${path}`;
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 10000);
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  try {
    const response = await fetch(url, {
      ...options,
      headers,
      signal: controller.signal,
      cache: "no-store",
    });
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      const message = data?.error?.message || `Request failed with HTTP ${response.status}`;
      throw apiError(message, response.status, data?.error?.code);
    }
    return data;
  } catch (error) {
    if (error.name === "AbortError") {
      throw apiError("Request timed out", null, "timeout");
    }
    if (error instanceof TypeError && /failed to fetch/i.test(error.message || "")) {
      throw apiError(
        `Could not reach ${state.serverUrl}. Confirm the ado server is running, the URL is correct, and the server was restarted with CORS support.`,
        null,
        "network_or_cors",
      );
    }
    if (error instanceof SyntaxError) {
      throw apiError("Server returned invalid JSON", null, "invalid_json");
    }
    throw error;
  } finally {
    window.clearTimeout(timeout);
  }
}

function apiError(message, status, code) {
  const error = new Error(message);
  error.status = status;
  error.code = code;
  return error;
}

async function getProjects() {
  return apiFetch("/api/v1/projects");
}

async function createProject(request) {
  return apiFetch("/api/v1/projects", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

async function getTasks(projectId) {
  return apiFetch(`/api/v1/projects/${encodeURIComponent(projectId)}/tasks`);
}

async function createTask(projectId, request) {
  return apiFetch(`/api/v1/projects/${encodeURIComponent(projectId)}/tasks`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

async function getTask(taskId) {
  return apiFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}`);
}

async function getSubtasks(taskId) {
  return apiFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/subtasks`);
}

async function getTemplate(templateKey) {
  return apiFetch(`/api/v1/templates/${encodeURIComponent(templateKey)}`);
}

async function createSubtask(taskId, request) {
  return apiFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}/subtasks`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

async function patchTaskStatus(taskId, status) {
  return apiFetch(`/api/v1/tasks/${encodeURIComponent(taskId)}`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}

async function patchSubtaskStatus(subtaskId, status) {
  return apiFetch(`/api/v1/subtasks/${encodeURIComponent(subtaskId)}`, {
    method: "PATCH",
    body: JSON.stringify({ status }),
  });
}

async function generateTemplate(templateKey, body) {
  return apiFetch(`/api/v1/templates/${encodeURIComponent(templateKey)}/generate`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

async function testConnection() {
  return apiFetch("/healthz");
}

function saveServerUrl() {
  state.serverUrl = normalizeServerUrl(els.serverUrl.value);
  els.serverUrl.value = state.serverUrl;
  localStorage.setItem(KEYS.serverUrl, state.serverUrl);
  setMessage("Server URL saved.", "success");
  render();
}

async function onTestConnection() {
  saveServerUrl();
  setStatus("idle", "Testing");
  try {
    await testConnection();
    setStatus("connected", "Connected");
    setMessage("Connection succeeded.", "success");
  } catch (error) {
    setStatus("error", "Error");
    setMessage(error.message || "Connection failed.", "error");
  }
  render();
}

async function loadProjects() {
  saveServerUrl();
  setMessage("Loading projects...", "");
  renderProjectList(true);
  try {
    const projects = await getProjects();
    state.projects = Array.isArray(projects) ? projects : [];
    writeCache(KEYS.projects, state.projects);
    setStatus("connected", "Connected");
    setMessage(`Loaded ${state.projects.length} projects.`, "success");
  } catch (error) {
    state.projects = readCache(KEYS.projects, []);
    setStatus(state.projects.length ? "offline" : "error", state.projects.length ? "Offline/cache" : "Error");
    setMessage(
      state.projects.length ? `Showing cached projects. ${error.message}` : error.message,
      state.projects.length ? "warning" : "error",
    );
  }
  render();
}

function showProjectForm() {
  state.showingProjectForm = true;
  renderProjectForm();
  els.projectName.focus();
}

function hideProjectForm() {
  state.showingProjectForm = false;
  els.projectForm.reset();
  renderProjectForm();
}

async function onCreateProject(event) {
  event.preventDefault();
  const name = els.projectName.value.trim();
  if (!name) {
    setMessage("Project name is required.", "error");
    renderMessage();
    return;
  }

  const request = {
    name,
    description: els.projectDescription.value.trim(),
    tags: splitTags(els.projectTags.value),
  };

  try {
    els.projectForm.querySelector("button[type='submit']").disabled = true;
    const project = await createProject(request);
    state.projects = [project, ...state.projects.filter((item) => getId(item) !== getId(project))]
      .sort((a, b) => getName(a).localeCompare(getName(b)));
    writeCache(KEYS.projects, state.projects);
    state.selectedProjectId = getId(project);
    state.selectedTaskId = null;
    state.tasksByProject[state.selectedProjectId] = [];
    writeCache(KEYS.tasksByProject, state.tasksByProject);
    setStatus("connected", "Connected");
    setMessage(`Created project "${getName(project)}".`, "success");
    hideProjectForm();
    render();
    await loadTasks(state.selectedProjectId);
  } catch (error) {
    setStatus("error", "Error");
    setMessage(error.message || "Unable to create project.", "error");
    render();
  } finally {
    els.projectForm.querySelector("button[type='submit']").disabled = false;
  }
}

function setTaskFormMode(mode) {
  state.taskFormMode = mode === "bulk" ? "bulk" : "single";
  renderTaskForm();
  focusTaskFormMode();
}

function focusTaskFormMode() {
  if (!state.showingTaskForm) return;
  if (state.taskFormMode === "bulk") {
    els.taskBulk.focus();
  } else {
    els.taskName.focus();
  }
}

function showTaskForm() {
  if (!state.selectedProjectId) {
    setMessage("Select a project before adding a task.", "error");
    render();
    return;
  }
  state.showingTaskForm = true;
  renderTaskForm();
  focusTaskFormMode();
}

function hideTaskForm() {
  state.showingTaskForm = false;
  els.taskForm.reset();
  state.taskFormMode = "single";
  renderTaskForm();
}

async function onCreateTask(event) {
  event.preventDefault();
  if (!state.selectedProjectId) {
    setMessage("Select a project before adding a task.", "error");
    render();
    return;
  }

  if (state.taskFormMode === "bulk") {
    await onCreateBulkTasks();
    return;
  }

  const name = els.taskName.value.trim();
  if (!name) {
    setMessage("Task name is required.", "error");
    renderMessage();
    return;
  }

  const request = {
    name,
    description: els.taskDescription.value.trim(),
  };

  try {
    els.taskForm.querySelector("button[type='submit']").disabled = true;
    const task = await createTask(state.selectedProjectId, request);
    if (!getId(task)) throw apiError("Server did not return the created task.", null, "invalid_response");

    const tasks = state.tasksByProject[state.selectedProjectId] || [];
    state.tasksByProject[state.selectedProjectId] = [
      task,
      ...tasks.filter((item) => getId(item) !== getId(task)),
    ];
    state.taskDetails[getId(task)] = task;
    state.subtasksByTask[getId(task)] = state.subtasksByTask[getId(task)] || [];
    state.selectedTaskId = getId(task);
    writeCache(KEYS.tasksByProject, state.tasksByProject);
    writeCache(KEYS.taskDetails, state.taskDetails);
    writeCache(KEYS.subtasksByTask, state.subtasksByTask);
    setStatus("connected", "Connected");
    setMessage(`Created task "${getName(task)}".`, "success");
    hideTaskForm();
    render();
  } catch (error) {
    setStatus("error", "Error");
    setMessage(error.message || "Unable to create task.", "error");
    render();
  } finally {
    els.taskForm.querySelector("button[type='submit']").disabled = false;
  }
}

async function onCreateBulkTasks() {
  const parsed = parseBulkTasks(els.taskBulk.value);
  if (!parsed.length) {
    setMessage("Bulk task input did not contain any tasks.", "error");
    renderMessage();
    return;
  }

  const submit = els.taskForm.querySelector("button[type='submit']");
  const createdTasks = [];
  let createdSubtasks = 0;

  try {
    submit.disabled = true;
    for (const entry of parsed) {
      const task = await createTask(state.selectedProjectId, {
        name: entry.name,
        description: entry.description,
      });
      if (!getId(task)) throw apiError("Server did not return the created task.", null, "invalid_response");
      cacheTask(task, "append");
      createdTasks.push(task);

      for (const subtaskEntry of entry.subtasks) {
        const subtask = await createSubtask(getId(task), {
          name: subtaskEntry.name,
          description: subtaskEntry.description,
        });
        if (!getId(subtask)) throw apiError("Server did not return the created subtask.", null, "invalid_response");
        cacheSubtask(subtask, getId(task));
        createdSubtasks += 1;
      }
    }

    if (createdTasks.length) {
      state.selectedTaskId = getId(createdTasks[0]);
    }
    writeCache(KEYS.tasksByProject, state.tasksByProject);
    writeCache(KEYS.taskDetails, state.taskDetails);
    writeCache(KEYS.subtasksByTask, state.subtasksByTask);
    setStatus("connected", "Connected");
    setMessage(`Created ${createdTasks.length} tasks and ${createdSubtasks} subtasks.`, "success");
    hideTaskForm();
    render();
  } catch (error) {
    writeCache(KEYS.tasksByProject, state.tasksByProject);
    writeCache(KEYS.taskDetails, state.taskDetails);
    writeCache(KEYS.subtasksByTask, state.subtasksByTask);
    setStatus("error", "Error");
    setMessage(error.message || "Unable to create bulk tasks.", "error");
    render();
  } finally {
    submit.disabled = false;
  }
}

function setSubtaskFormMode(mode) {
  state.subtaskFormMode = mode === "bulk" ? "bulk" : "single";
  renderSubtaskForm();
  focusSubtaskFormMode();
}

function focusSubtaskFormMode() {
  if (!state.showingSubtaskForm) return;
  if (state.subtaskFormMode === "bulk") {
    els.subtaskBulk.focus();
  } else {
    els.subtaskName.focus();
  }
}

function showSubtaskForm() {
  if (!state.selectedTaskId) {
    setMessage("Select a task before adding a subtask.", "error");
    render();
    return;
  }
  state.showingSubtaskForm = true;
  renderSubtaskForm();
  focusSubtaskFormMode();
}

function hideSubtaskForm() {
  state.showingSubtaskForm = false;
  els.subtaskForm.reset();
  state.subtaskFormMode = "single";
  renderSubtaskForm();
}

async function onCreateSubtask(event) {
  event.preventDefault();
  if (!state.selectedTaskId) {
    setMessage("Select a task before adding a subtask.", "error");
    render();
    return;
  }

  if (state.subtaskFormMode === "bulk") {
    await onCreateBulkSubtasks();
    return;
  }

  const name = els.subtaskName.value.trim();
  if (!name) {
    setMessage("Subtask name is required.", "error");
    renderMessage();
    return;
  }

  const request = {
    name,
    description: els.subtaskDescription.value.trim(),
  };

  try {
    els.subtaskForm.querySelector("button[type='submit']").disabled = true;
    const subtask = await createSubtask(state.selectedTaskId, request);
    if (!getId(subtask)) throw apiError("Server did not return the created subtask.", null, "invalid_response");

    const taskId = getTaskId(subtask) || state.selectedTaskId;
    const subtasks = state.subtasksByTask[taskId] || [];
    state.subtasksByTask[taskId] = [
      ...subtasks.filter((item) => getId(item) !== getId(subtask)),
      subtask,
    ];
    writeCache(KEYS.subtasksByTask, state.subtasksByTask);
    setStatus("connected", "Connected");
    setMessage(`Created subtask "${getName(subtask)}".`, "success");
    hideSubtaskForm();
    render();
  } catch (error) {
    setStatus("error", "Error");
    setMessage(error.message || "Unable to create subtask.", "error");
    render();
  } finally {
    els.subtaskForm.querySelector("button[type='submit']").disabled = false;
  }
}

async function onCreateBulkSubtasks() {
  const parsed = parseBulkSubtasks(els.subtaskBulk.value);
  if (!parsed.length) {
    setMessage("Bulk subtask input did not contain any subtasks.", "error");
    renderMessage();
    return;
  }

  const submit = els.subtaskForm.querySelector("button[type='submit']");
  const created = [];

  try {
    submit.disabled = true;
    for (const entry of parsed) {
      const subtask = await createSubtask(state.selectedTaskId, {
        name: entry.name,
        description: entry.description,
      });
      if (!getId(subtask)) throw apiError("Server did not return the created subtask.", null, "invalid_response");
      cacheSubtask(subtask, state.selectedTaskId);
      created.push(subtask);
    }

    writeCache(KEYS.subtasksByTask, state.subtasksByTask);
    setStatus("connected", "Connected");
    setMessage(`Created ${created.length} subtasks.`, "success");
    hideSubtaskForm();
    render();
  } catch (error) {
    writeCache(KEYS.subtasksByTask, state.subtasksByTask);
    setStatus("error", "Error");
    setMessage(error.message || "Unable to create bulk subtasks.", "error");
    render();
  } finally {
    submit.disabled = false;
  }
}

async function selectProject(projectId) {
  state.selectedProjectId = projectId;
  state.selectedTaskId = null;
  state.showingTaskForm = false;
  state.showingSubtaskForm = false;
  state.taskFormMode = "single";
  state.subtaskFormMode = "single";
  els.taskForm.reset();
  els.subtaskForm.reset();
  render();
  await loadTasks(projectId);
}

async function loadTasks(projectId) {
  const cached = state.tasksByProject[projectId] || [];
  if (cached.length) renderTaskList(true);
  try {
    const tasks = await getTasks(projectId);
    state.tasksByProject[projectId] = Array.isArray(tasks) ? tasks : [];
    writeCache(KEYS.tasksByProject, state.tasksByProject);
    setStatus("connected", "Connected");
    setMessage(`Loaded ${state.tasksByProject[projectId].length} tasks.`, "success");
  } catch (error) {
    setStatus(cached.length ? "offline" : "error", cached.length ? "Offline/cache" : "Error");
    setMessage(
      cached.length ? `Showing cached tasks. ${error.message}` : error.message,
      cached.length ? "warning" : "error",
    );
  }
  render();
}

async function selectTask(taskId) {
  state.selectedTaskId = taskId;
  state.showingSubtaskForm = false;
  state.subtaskFormMode = "single";
  els.subtaskForm.reset();
  render();
  await loadTaskDetail(taskId);
}

async function loadTaskDetail(taskId) {
  try {
    const [task, subtasks] = await Promise.all([getTask(taskId), getSubtasks(taskId)]);
    state.taskDetails[taskId] = task;
    state.subtasksByTask[taskId] = Array.isArray(subtasks) ? subtasks : [];
    writeCache(KEYS.taskDetails, state.taskDetails);
    writeCache(KEYS.subtasksByTask, state.subtasksByTask);
    setStatus("connected", "Connected");
    setMessage("Loaded task detail.", "success");
  } catch (error) {
    const hasCache = Boolean(state.taskDetails[taskId] || state.subtasksByTask[taskId]);
    setStatus(hasCache ? "offline" : "error", hasCache ? "Offline/cache" : "Error");
    setMessage(
      hasCache ? `Showing cached task detail. ${error.message}` : error.message,
      hasCache ? "warning" : "error",
    );
  }
  render();
}

async function toggleTask(task) {
  const nextStatus = toggledStatus(getStatus(task));
  try {
    await patchTaskStatus(getId(task), nextStatus);
    const canonical = await getTask(getId(task));
    replaceTask(canonical);
    state.taskDetails[getId(canonical)] = canonical;
    writeCache(KEYS.tasksByProject, state.tasksByProject);
    writeCache(KEYS.taskDetails, state.taskDetails);
    if (state.selectedProjectId) {
      const tasks = await getTasks(state.selectedProjectId);
      state.tasksByProject[state.selectedProjectId] = Array.isArray(tasks) ? tasks : [];
      writeCache(KEYS.tasksByProject, state.tasksByProject);
    }
    setStatus("connected", "Connected");
    setMessage(`Task marked ${nextStatus}.`, "success");
  } catch (error) {
    setStatus("error", "Error");
    setMessage(error.message || "Unable to update task.", "error");
  }
  render();
}

async function toggleSubtask(subtask) {
  const nextStatus = toggledStatus(getStatus(subtask));
  try {
    await patchSubtaskStatus(getId(subtask), nextStatus);
    const taskId = getTaskId(subtask) || state.selectedTaskId;
    if (taskId) {
      const subtasks = await getSubtasks(taskId);
      state.subtasksByTask[taskId] = Array.isArray(subtasks) ? subtasks : [];
    }
    writeCache(KEYS.subtasksByTask, state.subtasksByTask);
    setStatus("connected", "Connected");
    setMessage(`Subtask marked ${nextStatus}.`, "success");
  } catch (error) {
    setStatus("error", "Error");
    setMessage(error.message || "Unable to update subtask.", "error");
  }
  render();
}

async function generateDaily(dateAlias) {
  const targetName = dailyNameForAlias(dateAlias);
  const carryOver = window.confirm(`Carry over unfinished non-default items into ${dailyLabel(dateAlias)}?`);
  const carryOvers = carryOver ? await dailyCarryOverItems(targetName) : [];
  await generateForProject("daily", { date: targetName }, "That daily list already exists.", carryOvers);
}

async function generateSeasonal(templateKey) {
  await generateForProject(templateKey, { year: new Date().getFullYear() }, "That chore list already exists.");
}

async function generateForProject(templateKey, body, conflictMessage, carryOvers = []) {
  try {
    const generated = await generateTemplate(templateKey, body);
    if (carryOvers.length) {
      const taskId = generated?.task_id || generated?.taskId;
      if (!taskId) throw apiError("Server did not return the generated task ID.", null, "invalid_response");
      for (const item of carryOvers) {
        await createSubtask(taskId, { name: item.name, description: item.description });
      }
      const subtasks = await getSubtasks(taskId);
      state.subtasksByTask[taskId] = Array.isArray(subtasks) ? subtasks : [];
      writeCache(KEYS.subtasksByTask, state.subtasksByTask);
    }
    setStatus("connected", "Connected");
    setMessage(carryOvers.length ? `Generated list with ${carryOvers.length} carry-over items.` : "Generated list.", "success");
    if (state.selectedProjectId) await loadTasks(state.selectedProjectId);
  } catch (error) {
    if (error.status === 409) {
      setMessage(conflictMessage, "warning");
    } else {
      setStatus("error", "Error");
      setMessage(error.message || "Unable to generate list.", "error");
    }
    render();
  }
}

async function dailyCarryOverItems(targetDateName) {
  const project = selectedProject();
  if (!project) return [];
  const defaultNames = await dailyDefaultNames();
  let tasks = [];
  try {
    tasks = await getTasks(getId(project));
    state.tasksByProject[getId(project)] = Array.isArray(tasks) ? tasks : [];
    writeCache(KEYS.tasksByProject, state.tasksByProject);
  } catch (_) {
    tasks = state.tasksByProject[getId(project)] || [];
  }

  const targetDate = parseDailyTaskDate(targetDateName);
  const previous = tasks
    .map((task) => ({ task, date: parseDailyTaskDate(getName(task)) }))
    .filter((entry) => entry.date && targetDate && entry.date < targetDate)
    .sort((a, b) => b.date.localeCompare(a.date))[0]?.task;
  if (!previous) return [];

  let subtasks = [];
  try {
    subtasks = await getSubtasks(getId(previous));
    state.subtasksByTask[getId(previous)] = Array.isArray(subtasks) ? subtasks : [];
    writeCache(KEYS.subtasksByTask, state.subtasksByTask);
  } catch (_) {
    subtasks = state.subtasksByTask[getId(previous)] || [];
  }

  const seen = new Set();
  return subtasks
    .map((subtask) => ({
      name: getName(subtask),
      description: getDescription(subtask),
      status: getStatus(subtask),
      normalized: normalizeCarryOverName(getName(subtask)),
    }))
    .filter((item) => {
      if (!item.normalized || item.status === "done" || item.status === "archived") return false;
      if (defaultNames.has(item.normalized) || seen.has(item.normalized)) return false;
      seen.add(item.normalized);
      return true;
    })
    .map((item) => ({ name: item.name, description: item.description }));
}

async function dailyDefaultNames() {
  try {
    const template = await getTemplate("daily");
    return new Set((template?.items || []).map((item) => normalizeCarryOverName(item.name)));
  } catch (_) {
    return new Set(["review calendar", "set priorities"].map(normalizeCarryOverName));
  }
}

function render() {
  els.serverUrl.value = state.serverUrl;
  renderStatus();
  renderMessage();
  renderProjectForm();
  renderProjectList(false);
  renderTaskForm();
  renderProjectActions();
  renderTaskList(false);
  renderSubtaskForm();
  renderTaskDetail();
}

function renderProjectForm() {
  els.projectForm.classList.toggle("hidden", !state.showingProjectForm);
  els.showProjectForm.disabled = state.showingProjectForm;
}

function renderTaskForm() {
  const canCreate = Boolean(state.selectedProjectId);
  if (!canCreate) state.showingTaskForm = false;
  els.taskForm.classList.toggle("hidden", !state.showingTaskForm);
  els.showTaskForm.disabled = state.showingTaskForm || !canCreate;
  els.taskSingleFields.classList.toggle("hidden", state.taskFormMode !== "single");
  els.taskBulkFields.classList.toggle("hidden", state.taskFormMode !== "bulk");
  els.taskModeSingle.classList.toggle("selected", state.taskFormMode === "single");
  els.taskModeBulk.classList.toggle("selected", state.taskFormMode === "bulk");
  els.taskModeSingle.setAttribute("aria-pressed", String(state.taskFormMode === "single"));
  els.taskModeBulk.setAttribute("aria-pressed", String(state.taskFormMode === "bulk"));
}

function renderSubtaskForm() {
  const canCreate = Boolean(state.selectedTaskId);
  if (!canCreate) state.showingSubtaskForm = false;
  els.subtaskForm.classList.toggle("hidden", !state.showingSubtaskForm);
  els.showSubtaskForm.disabled = state.showingSubtaskForm || !canCreate;
  els.subtaskSingleFields.classList.toggle("hidden", state.subtaskFormMode !== "single");
  els.subtaskBulkFields.classList.toggle("hidden", state.subtaskFormMode !== "bulk");
  els.subtaskModeSingle.classList.toggle("selected", state.subtaskFormMode === "single");
  els.subtaskModeBulk.classList.toggle("selected", state.subtaskFormMode === "bulk");
  els.subtaskModeSingle.setAttribute("aria-pressed", String(state.subtaskFormMode === "single"));
  els.subtaskModeBulk.setAttribute("aria-pressed", String(state.subtaskFormMode === "bulk"));
}

function renderStatus() {
  const status = els.connectionStatus;
  status.className = `status-pill status-${state.status}`;
  status.textContent = statusLabel();
}

function statusLabel() {
  if (state.status === "connected") return "Connected";
  if (state.status === "offline") return "Offline/cache";
  if (state.status === "error") return "Error";
  return "Idle";
}

function renderMessage() {
  if (!state.message) {
    els.messageArea.innerHTML = "";
    return;
  }
  const kind = state.messageKind ? ` ${state.messageKind}` : "";
  els.messageArea.innerHTML = `<div class="message${kind}">${escapeHtml(state.message)}</div>`;
}

function renderProjectList(loading) {
  if (loading && !state.projects.length) {
    els.projectList.innerHTML = `<div class="loading">Loading projects...</div>`;
    return;
  }
  if (!state.projects.length) {
    els.projectList.innerHTML = `<div class="empty">No projects loaded.</div>`;
    return;
  }
  els.projectList.innerHTML = state.projects.map((project) => projectCard(project)).join("");
  els.projectList.querySelectorAll("[data-project-id]").forEach((node) => {
    node.addEventListener("click", () => selectProject(node.dataset.projectId));
  });
}

function renderProjectActions() {
  const project = selectedProject();
  if (!project) {
    els.projectActions.innerHTML = "";
    return;
  }
  const coreKey = getCoreKey(project);
  if (coreKey === "daily") {
    els.projectActions.innerHTML = [
      `<button class="primary" type="button" data-generate-daily="today">Daily Today</button>`,
      `<button type="button" data-generate-daily="tomorrow">Daily Tomorrow</button>`,
    ].join("");
    els.projectActions.querySelectorAll("[data-generate-daily]").forEach((node) => {
      node.addEventListener("click", () => generateDaily(node.dataset.generateDaily));
    });
    return;
  }
  if (coreKey === "home") {
    els.projectActions.innerHTML = [
      ["summer_chores", "Generate Summer chores"],
      ["fall_chores", "Generate Fall chores"],
      ["winter_chores", "Generate Winter chores"],
      ["spring_chores", "Generate Spring chores"],
      ["leaving_house", "Generate Leaving house"],
    ].map(([key, label]) => `<button type="button" data-generate="${key}">${label}</button>`).join("");
    els.projectActions.querySelectorAll("[data-generate]").forEach((node) => {
      node.addEventListener("click", () => generateSeasonal(node.dataset.generate));
    });
    return;
  }
  els.projectActions.innerHTML = "";
}

function renderTaskList(loading) {
  if (!state.selectedProjectId) {
    els.taskList.innerHTML = `<div class="empty">Select a project.</div>`;
    return;
  }
  const tasks = state.tasksByProject[state.selectedProjectId] || [];
  if (loading && !tasks.length) {
    els.taskList.innerHTML = `<div class="loading">Loading tasks...</div>`;
    return;
  }
  if (!tasks.length) {
    els.taskList.innerHTML = `<div class="empty">No tasks for this project.</div>`;
    return;
  }
  els.taskList.innerHTML = tasks.map((task) => taskCard(task)).join("");
  els.taskList.querySelectorAll("[data-task-id]").forEach((node) => {
    node.addEventListener("click", () => selectTask(node.dataset.taskId));
    node.addEventListener("dblclick", (event) => {
      event.preventDefault();
      const task = findTask(node.dataset.taskId);
      if (task) toggleTask(task);
    });
  });
  els.taskList.querySelectorAll("[data-toggle-task]").forEach((node) => {
    node.addEventListener("click", (event) => {
      event.stopPropagation();
      const task = findTask(node.dataset.toggleTask);
      if (task) toggleTask(task);
    });
  });
}

function renderTaskDetail() {
  if (!state.selectedTaskId) {
    els.taskDetail.innerHTML = `<div class="empty">Select a task.</div>`;
    return;
  }
  const task = state.taskDetails[state.selectedTaskId] || findTask(state.selectedTaskId);
  if (!task) {
    els.taskDetail.innerHTML = `<div class="empty">Task detail is not loaded.</div>`;
    return;
  }
  const subtasks = state.subtasksByTask[state.selectedTaskId] || [];
  els.taskDetail.innerHTML = `
    <h3 class="${isDone(task) ? "done" : ""}">${escapeHtml(getName(task))}</h3>
    ${descriptionHtml(task)}
    <div class="meta">Status: ${escapeHtml(getStatus(task))}</div>
    <div class="meta">Created: ${escapeHtml(getCreatedAt(task) || "unknown")}</div>
    ${getFinishedAt(task) ? `<div class="meta">Finished: ${escapeHtml(getFinishedAt(task))}</div>` : ""}
    <div class="row-actions">
      <button type="button" data-toggle-selected-task>${isDone(task) ? "Reopen" : "Done"}</button>
    </div>
    <div class="detail-section">
      <h4>SubTasks</h4>
      ${subtasks.length ? subtasks.map((subtask) => subtaskCard(subtask)).join("") : `<div class="empty">No subtasks.</div>`}
    </div>
  `;
  els.taskDetail.querySelector("[data-toggle-selected-task]")?.addEventListener("click", () => toggleTask(task));
  els.taskDetail.querySelectorAll("[data-subtask-id]").forEach((node) => {
    node.addEventListener("dblclick", () => {
      const subtask = findSubtask(node.dataset.subtaskId);
      if (subtask) toggleSubtask(subtask);
    });
  });
  els.taskDetail.querySelectorAll("[data-toggle-subtask]").forEach((node) => {
    node.addEventListener("click", (event) => {
      event.stopPropagation();
      const subtask = findSubtask(node.dataset.toggleSubtask);
      if (subtask) toggleSubtask(subtask);
    });
  });
}

function projectCard(project) {
  const selected = state.selectedProjectId === getId(project) ? " selected" : "";
  const tags = getTags(project).map((tag) => `<span class="tag">${escapeHtml(tag)}</span>`).join("");
  return `
    <div class="card clickable${selected}" data-project-id="${escapeAttr(getId(project))}">
      <div class="card-title">
        <span>${escapeHtml(getName(project))}</span>
        ${isCore(project) ? `<span class="marker">Core</span>` : ""}
      </div>
      ${descriptionHtml(project)}
      ${tags ? `<div class="tags">${tags}</div>` : ""}
    </div>
  `;
}

function taskCard(task) {
  const selected = state.selectedTaskId === getId(task) ? " selected" : "";
  return `
    <div class="card clickable${selected}" data-task-id="${escapeAttr(getId(task))}">
      <div class="card-title">
        <span class="${isDone(task) ? "done" : ""}">${escapeHtml(getName(task))}</span>
        <span class="marker">${escapeHtml(getStatus(task))}</span>
      </div>
      ${descriptionHtml(task)}
      ${getFinishedAt(task) ? `<div class="meta">Finished: ${escapeHtml(getFinishedAt(task))}</div>` : ""}
      <div class="row-actions">
        <button type="button" data-toggle-task="${escapeAttr(getId(task))}">${isDone(task) ? "Reopen" : "Done"}</button>
      </div>
    </div>
  `;
}

function subtaskCard(subtask) {
  return `
    <div class="card clickable" data-subtask-id="${escapeAttr(getId(subtask))}">
      <div class="card-title">
        <span class="${isDone(subtask) ? "done" : ""}">${escapeHtml(getName(subtask))}</span>
        <span class="marker">${escapeHtml(getStatus(subtask))}</span>
      </div>
      ${descriptionHtml(subtask)}
      <div class="row-actions">
        <button type="button" data-toggle-subtask="${escapeAttr(getId(subtask))}">${isDone(subtask) ? "Reopen" : "Done"}</button>
      </div>
    </div>
  `;
}

function descriptionHtml(item) {
  const description = getDescription(item);
  return description ? `<div class="description">${escapeHtml(description)}</div>` : "";
}

function selectedProject() {
  return state.projects.find((project) => getId(project) === state.selectedProjectId) || null;
}

function findTask(taskId) {
  const tasks = state.selectedProjectId ? state.tasksByProject[state.selectedProjectId] || [] : [];
  return tasks.find((task) => getId(task) === taskId) || state.taskDetails[taskId] || null;
}

function findSubtask(subtaskId) {
  const subtasks = state.selectedTaskId ? state.subtasksByTask[state.selectedTaskId] || [] : [];
  return subtasks.find((subtask) => getId(subtask) === subtaskId) || null;
}

function replaceTask(updated) {
  const projectId = getProjectId(updated) || state.selectedProjectId;
  if (!projectId) return;
  state.tasksByProject[projectId] = (state.tasksByProject[projectId] || []).map((task) => (
    getId(task) === getId(updated) ? updated : task
  ));
}

function cacheTask(task, placement = "prepend") {
  const taskId = getId(task);
  const projectId = getProjectId(task) || state.selectedProjectId;
  if (!taskId || !projectId) return;
  const existing = (state.tasksByProject[projectId] || []).filter((item) => getId(item) !== taskId);
  state.tasksByProject[projectId] = placement === "append" ? [...existing, task] : [task, ...existing];
  state.taskDetails[taskId] = task;
  state.subtasksByTask[taskId] = state.subtasksByTask[taskId] || [];
}

function cacheSubtask(subtask, fallbackTaskId) {
  const subtaskId = getId(subtask);
  const taskId = getTaskId(subtask) || fallbackTaskId;
  if (!subtaskId || !taskId) return;
  const existing = (state.subtasksByTask[taskId] || []).filter((item) => getId(item) !== subtaskId);
  state.subtasksByTask[taskId] = [...existing, subtask];
}

function setStatus(status, message) {
  state.status = status;
  if (message) state.statusMessage = message;
}

function setMessage(message, kind) {
  state.message = message || "";
  state.messageKind = kind || "";
}

function readCache(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch (_) {
    return fallback;
  }
}

function writeCache(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function splitTags(value) {
  return String(value || "")
    .split(",")
    .map((tag) => tag.trim())
    .filter(Boolean);
}

function parseBulkTasks(text) {
  const entries = parseIndentedLines(text);
  const tasks = [];
  let currentTask = null;
  let currentSubtask = null;

  for (const entry of entries) {
    if (entry.depth === 0 || !currentTask) {
      currentTask = { name: entry.text, description: "", subtasks: [] };
      currentSubtask = null;
      tasks.push(currentTask);
      continue;
    }

    if (entry.depth === 1) {
      currentSubtask = { name: entry.text, description: "" };
      currentTask.subtasks.push(currentSubtask);
      continue;
    }

    if (currentSubtask) {
      currentSubtask.description = appendDescription(currentSubtask.description, entry.text);
    } else {
      currentTask.description = appendDescription(currentTask.description, entry.text);
    }
  }

  return tasks;
}

function parseBulkSubtasks(text) {
  const entries = parseIndentedLines(text);
  const subtasks = [];
  let currentSubtask = null;

  for (const entry of entries) {
    if (entry.depth === 0 || !currentSubtask) {
      currentSubtask = { name: entry.text, description: "" };
      subtasks.push(currentSubtask);
      continue;
    }

    currentSubtask.description = appendDescription(currentSubtask.description, entry.text);
  }

  return subtasks;
}

function parseIndentedLines(text) {
  const rows = String(text || "")
    .replaceAll("\t", "  ")
    .split(/\r?\n/)
    .map((raw) => {
      const indent = raw.match(/^ */)?.[0].length || 0;
      const textValue = stripListPrefix(raw.trim());
      return { indent, text: textValue };
    })
    .filter((row) => row.text);

  const indents = [...new Set(rows.map((row) => row.indent))].sort((a, b) => a - b);
  return rows.map((row) => ({
    depth: Math.max(0, indents.indexOf(row.indent)),
    text: row.text,
  }));
}

function stripListPrefix(value) {
  return String(value || "")
    .replace(/^[-*+]\s+\[[ xX]\]\s+/, "")
    .replace(/^\[[ xX]\]\s+/, "")
    .replace(/^[-*+]\s+/, "")
    .replace(/^\d+[.)]\s+/, "")
    .replace(/^[a-zA-Z][.)]\s+/, "")
    .trim();
}

function appendDescription(current, nextLine) {
  return current ? `${current}\n${nextLine}` : nextLine;
}

function todayName() {
  return dateNameFromOffset(0);
}

function tomorrowName() {
  return dateNameFromOffset(1);
}

function dateNameFromOffset(offsetDays) {
  const now = new Date();
  now.setDate(now.getDate() + offsetDays);
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function dailyNameForAlias(alias) {
  if (alias === "tomorrow") return tomorrowName();
  if (alias === "today") return todayName();
  return parseDailyTaskDate(alias) || todayName();
}

function dailyLabel(alias) {
  if (alias === "tomorrow") return "tomorrow's Daily list";
  if (alias === "today") return "today's Daily list";
  return `the Daily list for ${dailyNameForAlias(alias)}`;
}

function parseDailyTaskDate(value) {
  const text = String(value || "").trim().slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : null;
}

function normalizeCarryOverName(value) {
  return String(value || "").trim().toLowerCase().replace(/\s+/g, " ");
}

function toggledStatus(status) {
  return status === "done" ? "todo" : "done";
}

function isDone(item) {
  return getStatus(item) === "done";
}

function getId(item) {
  return item?.id || "";
}

function getName(item) {
  return item?.name || "";
}

function getDescription(item) {
  return item?.description || "";
}

function getStatus(item) {
  return item?.status || "todo";
}

function getTags(project) {
  return Array.isArray(project?.tags) ? project.tags : [];
}

function isCore(project) {
  return Boolean(project?.is_core ?? project?.isCore);
}

function getCoreKey(project) {
  return project?.core_key ?? project?.coreKey ?? null;
}

function getProjectId(task) {
  return task?.project_id ?? task?.projectId ?? "";
}

function getTaskId(subtask) {
  return subtask?.task_id ?? subtask?.taskId ?? "";
}

function getCreatedAt(item) {
  return item?.created_at ?? item?.createdAt ?? "";
}

function getFinishedAt(item) {
  return item?.finished_at ?? item?.finishedAt ?? "";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttr(value) {
  return escapeHtml(value);
}
