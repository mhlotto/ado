package api_test

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"ado/internal/api"
	"ado/internal/domain"
	"ado/internal/service"
)

func TestHealthEndpoint(t *testing.T) {
	_, router := testRouter()
	res := request(t, router, http.MethodGet, "/healthz", nil)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
}

func TestCORSAllowsLocalWebClientOrigin(t *testing.T) {
	_, router := testRouter()
	req := httptest.NewRequest(http.MethodOptions, "/api/v1/projects", nil)
	req.Header.Set("Origin", "http://localhost:5173")
	req.Header.Set("Access-Control-Request-Method", "GET")
	res := httptest.NewRecorder()
	router.ServeHTTP(res, req)
	if res.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d: %s", res.Code, res.Body.String())
	}
	if got := res.Header().Get("Access-Control-Allow-Origin"); got != "http://localhost:5173" {
		t.Fatalf("expected localhost CORS origin, got %q", got)
	}
	if got := res.Header().Get("Access-Control-Allow-Methods"); !strings.Contains(got, http.MethodPatch) {
		t.Fatalf("expected PATCH in allowed methods, got %q", got)
	}
	if got := res.Header().Get("Access-Control-Allow-Private-Network"); got != "true" {
		t.Fatalf("expected private-network CORS allowance, got %q", got)
	}
}

func TestCORSAllowsLocalFileOrigin(t *testing.T) {
	_, router := testRouter()
	req := httptest.NewRequest(http.MethodOptions, "/api/v1/projects", nil)
	req.Header.Set("Origin", "null")
	req.Header.Set("Access-Control-Request-Method", "GET")
	res := httptest.NewRecorder()
	router.ServeHTTP(res, req)
	if res.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d: %s", res.Code, res.Body.String())
	}
	if got := res.Header().Get("Access-Control-Allow-Origin"); got != "null" {
		t.Fatalf("expected file-origin CORS origin, got %q", got)
	}
}

func TestProjectCreation(t *testing.T) {
	_, router := testRouter()
	res := request(t, router, http.MethodPost, "/api/v1/projects", map[string]any{"name": "Errands", "description": "Outside", "tags": []string{"personal"}})
	if res.Code != http.StatusCreated {
		t.Fatalf("expected 201, got %d: %s", res.Code, res.Body.String())
	}
	var p domain.Project
	decode(t, res, &p)
	if p.Name != "Errands" || len(p.Tags) != 1 {
		t.Fatalf("unexpected project: %+v", p)
	}
}

func TestProjectUpdate(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	res := request(t, router, http.MethodPatch, "/api/v1/projects/"+p.ID, map[string]any{"name": "House", "tags": []string{"home"}})
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var updated domain.Project
	decode(t, res, &updated)
	if updated.Name != "House" || updated.Tags[0] != "home" {
		t.Fatalf("unexpected update: %+v", updated)
	}
}

func TestProjectListIncludesTaskCountsAndTemplateActions(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	todo := createTask(t, router, p.ID, "Buy groceries")
	done := createTask(t, router, p.ID, "Drop mail")
	request(t, router, http.MethodPatch, "/api/v1/tasks/"+done.ID, map[string]any{"status": "done"})

	res := request(t, router, http.MethodGet, "/api/v1/projects", nil)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var projects []domain.ProjectSummary
	decode(t, res, &projects)
	errands := findProjectSummary(t, projects, p.ID)
	if errands.TaskCounts.Total != 2 || errands.TaskCounts.Open != 1 || errands.TaskCounts.Done != 1 {
		t.Fatalf("unexpected errands counts: %+v; todo=%s", errands.TaskCounts, todo.ID)
	}
	if len(errands.TemplateActions) != 0 {
		t.Fatalf("user project should not have template actions: %+v", errands.TemplateActions)
	}
	daily := findProjectSummaryByCoreKey(t, projects, "daily")
	if !hasTemplateAction(daily.TemplateActions, "daily") {
		t.Fatalf("daily project missing daily template action: %+v", daily.TemplateActions)
	}
	home := findProjectSummaryByCoreKey(t, projects, "home")
	if len(home.TemplateActions) != 5 || !hasTemplateAction(home.TemplateActions, "spring_chores") || !hasTemplateAction(home.TemplateActions, "leaving_house") {
		t.Fatalf("home project missing template actions: %+v", home.TemplateActions)
	}
}

func TestProjectDeletion(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	res := request(t, router, http.MethodDelete, "/api/v1/projects/"+p.ID, nil)
	if res.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d: %s", res.Code, res.Body.String())
	}
	res = request(t, router, http.MethodGet, "/api/v1/projects/"+p.ID, nil)
	if res.Code != http.StatusNotFound {
		t.Fatalf("expected 404 after delete, got %d", res.Code)
	}
}

func TestCoreProjectCannotBeRenamed(t *testing.T) {
	_, router := testRouter()
	daily := coreProject(t, router, "daily")
	res := request(t, router, http.MethodPatch, "/api/v1/projects/"+daily.ID, map[string]any{"name": "Everyday"})
	if res.Code != http.StatusConflict {
		t.Fatalf("expected 409, got %d: %s", res.Code, res.Body.String())
	}
}

func TestCoreProjectCannotBeDeleted(t *testing.T) {
	_, router := testRouter()
	home := coreProject(t, router, "home")
	res := request(t, router, http.MethodDelete, "/api/v1/projects/"+home.ID, nil)
	if res.Code != http.StatusConflict {
		t.Fatalf("expected 409, got %d: %s", res.Code, res.Body.String())
	}
}

func TestTaskCreation(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	task := createTask(t, router, p.ID, "Buy groceries")
	if task.ProjectID != p.ID || task.Status != domain.StatusTodo {
		t.Fatalf("unexpected task: %+v", task)
	}
}

func TestTaskUpdate(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	task := createTask(t, router, p.ID, "Buy groceries")
	res := request(t, router, http.MethodPatch, "/api/v1/tasks/"+task.ID, map[string]any{"name": "Buy coffee", "status": "in_progress"})
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var updated domain.Task
	decode(t, res, &updated)
	if updated.Name != "Buy coffee" || updated.Status != domain.StatusInProgress {
		t.Fatalf("unexpected task update: %+v", updated)
	}
}

func TestTaskMoveToProject(t *testing.T) {
	_, router := testRouter()
	fromProject := createProject(t, router, "Errands")
	toProject := createProject(t, router, "Home projects")
	task := createTask(t, router, fromProject.ID, "Buy groceries")
	res := request(t, router, http.MethodPatch, "/api/v1/tasks/"+task.ID, map[string]any{"project_id": toProject.ID})
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var updated domain.Task
	decode(t, res, &updated)
	if updated.ProjectID != toProject.ID {
		t.Fatalf("expected task to move to %s, got %+v", toProject.ID, updated)
	}
	res = request(t, router, http.MethodGet, "/api/v1/projects/"+fromProject.ID+"/tasks", nil)
	var fromTasks []domain.Task
	decode(t, res, &fromTasks)
	if len(fromTasks) != 0 {
		t.Fatalf("expected original project to be empty, got %+v", fromTasks)
	}
	res = request(t, router, http.MethodGet, "/api/v1/projects/"+toProject.ID+"/tasks", nil)
	var toTasks []domain.Task
	decode(t, res, &toTasks)
	if len(toTasks) != 1 || toTasks[0].ID != task.ID {
		t.Fatalf("expected moved task in destination project, got %+v", toTasks)
	}
}

func TestTaskMoveAcceptsCamelCaseProjectID(t *testing.T) {
	_, router := testRouter()
	fromProject := createProject(t, router, "Errands")
	toProject := createProject(t, router, "Home projects")
	task := createTask(t, router, fromProject.ID, "Buy groceries")
	res := request(t, router, http.MethodPatch, "/api/v1/tasks/"+task.ID, map[string]any{"projectId": toProject.ID})
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var updated domain.Task
	decode(t, res, &updated)
	if updated.ProjectID != toProject.ID {
		t.Fatalf("expected task to move to %s, got %+v", toProject.ID, updated)
	}
}

func TestProjectDetailIncludesTaskCounts(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	todo := createTask(t, router, p.ID, "Buy groceries")
	inProgress := createTask(t, router, p.ID, "Pick up package")
	done := createTask(t, router, p.ID, "Drop mail")
	archived := createTask(t, router, p.ID, "Old errand")

	request(t, router, http.MethodPatch, "/api/v1/tasks/"+inProgress.ID, map[string]any{"status": "in_progress"})
	request(t, router, http.MethodPatch, "/api/v1/tasks/"+done.ID, map[string]any{"status": "done"})
	request(t, router, http.MethodPatch, "/api/v1/tasks/"+archived.ID, map[string]any{"status": "archived"})

	res := request(t, router, http.MethodGet, "/api/v1/projects/"+p.ID, nil)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var detail domain.ProjectDetail
	decode(t, res, &detail)
	if detail.ID != p.ID || detail.TaskCounts.Total != 4 || detail.TaskCounts.Open != 2 || detail.TaskCounts.Todo != 1 || detail.TaskCounts.InProgress != 1 || detail.TaskCounts.Done != 1 || detail.TaskCounts.Archived != 1 {
		t.Fatalf("unexpected project detail counts: %+v; todo task=%s", detail, todo.ID)
	}
	if detail.Tasks != nil {
		t.Fatalf("expected tasks to be omitted without include, got %+v", detail.Tasks)
	}
}

func TestProjectDetailIncludesTemplateActionsForCoreProject(t *testing.T) {
	_, router := testRouter()
	dailyProject := coreProject(t, router, "daily")
	res := request(t, router, http.MethodGet, "/api/v1/projects/"+dailyProject.ID, nil)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var detail domain.ProjectDetail
	decode(t, res, &detail)
	if !hasTemplateAction(detail.TemplateActions, "daily") {
		t.Fatalf("daily project detail missing daily action: %+v", detail.TemplateActions)
	}
}

func TestProjectDetailCanIncludeTasksAndSubTasks(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	task := createTask(t, router, p.ID, "Buy groceries")
	createTask(t, router, p.ID, "Pick up package")
	subtask := createSubTask(t, router, task.ID, "Buy coffee")

	res := request(t, router, http.MethodGet, "/api/v1/projects/"+p.ID+"?include=tasks,subtasks", nil)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var detail domain.ProjectDetail
	decode(t, res, &detail)
	if len(detail.Tasks) != 2 {
		t.Fatalf("expected 2 nested tasks, got %+v", detail.Tasks)
	}
	nested := findNestedTask(t, detail.Tasks, task.ID)
	if len(nested.SubTasks) != 1 || nested.SubTasks[0].ID != subtask.ID {
		t.Fatalf("expected nested subtask %+v, got %+v", subtask, nested.SubTasks)
	}
}

func TestProjectDetailIncludeSubTasksImpliesTasks(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	task := createTask(t, router, p.ID, "Buy groceries")
	subtask := createSubTask(t, router, task.ID, "Buy coffee")

	res := request(t, router, http.MethodGet, "/api/v1/projects/"+p.ID+"?include=subtasks", nil)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var detail domain.ProjectDetail
	decode(t, res, &detail)
	nested := findNestedTask(t, detail.Tasks, task.ID)
	if len(nested.SubTasks) != 1 || nested.SubTasks[0].ID != subtask.ID {
		t.Fatalf("expected nested subtask %+v, got %+v", subtask, nested.SubTasks)
	}
}

func TestProjectDetailRejectsUnknownInclude(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	res := request(t, router, http.MethodGet, "/api/v1/projects/"+p.ID+"?include=tasks,widgets", nil)
	if res.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d: %s", res.Code, res.Body.String())
	}
}

func TestSettingTaskStatusDoneSetsFinishedAt(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	task := createTask(t, router, p.ID, "Buy groceries")
	res := request(t, router, http.MethodPatch, "/api/v1/tasks/"+task.ID, map[string]any{"status": "done"})
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var updated domain.Task
	decode(t, res, &updated)
	if updated.FinishedAt == nil {
		t.Fatalf("expected finished_at to be set")
	}
}

func TestMovingTaskOutOfDoneClearsFinishedAt(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	task := createTask(t, router, p.ID, "Buy groceries")
	res := request(t, router, http.MethodPatch, "/api/v1/tasks/"+task.ID, map[string]any{"status": "done"})
	var done domain.Task
	decode(t, res, &done)
	res = request(t, router, http.MethodPatch, "/api/v1/tasks/"+done.ID, map[string]any{"status": "todo"})
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var reopened domain.Task
	decode(t, res, &reopened)
	if reopened.FinishedAt != nil {
		t.Fatalf("expected finished_at to be cleared")
	}
}

func TestSubTaskCreation(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	task := createTask(t, router, p.ID, "Buy groceries")
	subtask := createSubTask(t, router, task.ID, "Buy coffee")
	if subtask.TaskID != task.ID || subtask.Status != domain.StatusTodo {
		t.Fatalf("unexpected subtask: %+v", subtask)
	}
}

func TestSubTaskUpdate(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	task := createTask(t, router, p.ID, "Buy groceries")
	subtask := createSubTask(t, router, task.ID, "Buy coffee")
	res := request(t, router, http.MethodPatch, "/api/v1/subtasks/"+subtask.ID, map[string]any{"name": "Buy beans", "status": "done"})
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var updated domain.SubTask
	decode(t, res, &updated)
	if updated.Name != "Buy beans" || updated.FinishedAt == nil {
		t.Fatalf("unexpected subtask update: %+v", updated)
	}
}

func TestSubTaskMoveToTask(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	fromTask := createTask(t, router, p.ID, "Buy groceries")
	toTask := createTask(t, router, p.ID, "Make coffee")
	subtask := createSubTask(t, router, fromTask.ID, "Buy beans")
	res := request(t, router, http.MethodPatch, "/api/v1/subtasks/"+subtask.ID, map[string]any{"task_id": toTask.ID})
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var updated domain.SubTask
	decode(t, res, &updated)
	if updated.TaskID != toTask.ID {
		t.Fatalf("expected subtask to move to %s, got %+v", toTask.ID, updated)
	}
	res = request(t, router, http.MethodGet, "/api/v1/tasks/"+fromTask.ID+"/subtasks", nil)
	var fromSubTasks []domain.SubTask
	decode(t, res, &fromSubTasks)
	if len(fromSubTasks) != 0 {
		t.Fatalf("expected original task to have no subtasks, got %+v", fromSubTasks)
	}
	res = request(t, router, http.MethodGet, "/api/v1/tasks/"+toTask.ID+"/subtasks", nil)
	var toSubTasks []domain.SubTask
	decode(t, res, &toSubTasks)
	if len(toSubTasks) != 1 || toSubTasks[0].ID != subtask.ID {
		t.Fatalf("expected moved subtask in destination task, got %+v", toSubTasks)
	}
}

func TestSubTaskMoveAcceptsCamelCaseTaskID(t *testing.T) {
	_, router := testRouter()
	p := createProject(t, router, "Errands")
	fromTask := createTask(t, router, p.ID, "Buy groceries")
	toTask := createTask(t, router, p.ID, "Make coffee")
	subtask := createSubTask(t, router, fromTask.ID, "Buy beans")
	res := request(t, router, http.MethodPatch, "/api/v1/subtasks/"+subtask.ID, map[string]any{"taskId": toTask.ID})
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var updated domain.SubTask
	decode(t, res, &updated)
	if updated.TaskID != toTask.ID {
		t.Fatalf("expected subtask to move to %s, got %+v", toTask.ID, updated)
	}
}

func TestTemplateRetrieval(t *testing.T) {
	_, router := testRouter()
	res := request(t, router, http.MethodGet, "/api/v1/templates/daily", nil)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var tpl domain.Template
	decode(t, res, &tpl)
	if tpl.TemplateKey != "daily" || len(tpl.Items) != 2 {
		t.Fatalf("unexpected template: %+v", tpl)
	}
}

func TestTemplateUpdate(t *testing.T) {
	_, router := testRouter()
	res := request(t, router, http.MethodPatch, "/api/v1/templates/daily", map[string]any{
		"items": []map[string]any{
			{"name": "review calendar", "position": 0},
			{"name": "set priorities", "position": 1},
		},
	})
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var tpl domain.Template
	decode(t, res, &tpl)
	if len(tpl.Items) != 2 || tpl.Items[1].Name != "set priorities" {
		t.Fatalf("unexpected template update: %+v", tpl.Items)
	}
}

func TestDailyTaskGeneration(t *testing.T) {
	_, router := testRouter()
	res := request(t, router, http.MethodPost, "/api/v1/templates/daily/generate", map[string]any{"date": "2026-05-08"})
	if res.Code != http.StatusCreated {
		t.Fatalf("expected 201, got %d: %s", res.Code, res.Body.String())
	}
	var generated domain.GeneratedTask
	decode(t, res, &generated)
	if generated.Name != "2026-05-08 Friday" || generated.SubtasksCreated != 2 {
		t.Fatalf("unexpected generated task: %+v", generated)
	}
}

func TestDuplicateDailyGenerationReturnsConflict(t *testing.T) {
	_, router := testRouter()
	body := map[string]any{"date": "2026-05-08"}
	first := request(t, router, http.MethodPost, "/api/v1/templates/daily/generate", body)
	if first.Code != http.StatusCreated {
		t.Fatalf("expected first generation to succeed, got %d", first.Code)
	}
	second := request(t, router, http.MethodPost, "/api/v1/templates/daily/generate", body)
	if second.Code != http.StatusConflict {
		t.Fatalf("expected 409, got %d: %s", second.Code, second.Body.String())
	}
}

func TestDailyGenerationConflictsWithLegacyDateOnlyName(t *testing.T) {
	_, router := testRouter()
	daily := coreProject(t, router, "daily")
	createTask(t, router, daily.ID, "2026-05-08")

	res := request(t, router, http.MethodPost, "/api/v1/templates/daily/generate", map[string]any{"date": "2026-05-08"})
	if res.Code != http.StatusConflict {
		t.Fatalf("expected 409, got %d: %s", res.Code, res.Body.String())
	}
}

func TestSeasonalChoreGeneration(t *testing.T) {
	_, router := testRouter()
	res := request(t, router, http.MethodPost, "/api/v1/templates/spring_chores/generate", map[string]any{"year": 2026})
	if res.Code != http.StatusCreated {
		t.Fatalf("expected 201, got %d: %s", res.Code, res.Body.String())
	}
	var generated domain.GeneratedTask
	decode(t, res, &generated)
	if generated.Name != "Spring chores 2026" || generated.SubtasksCreated != 1 {
		t.Fatalf("unexpected generated task: %+v", generated)
	}
}

func TestLeavingHouseGeneration(t *testing.T) {
	_, router := testRouter()
	res := request(t, router, http.MethodPost, "/api/v1/templates/leaving_house/generate", map[string]any{})
	if res.Code != http.StatusCreated {
		t.Fatalf("expected 201, got %d: %s", res.Code, res.Body.String())
	}
	var generated domain.GeneratedTask
	decode(t, res, &generated)
	if generated.Name != "Leaving house" || generated.SubtasksCreated != 7 {
		t.Fatalf("unexpected generated task: %+v", generated)
	}
}

func TestSyncSnapshotReturnsHydrationData(t *testing.T) {
	_, router := testRouter()
	project := createProject(t, router, "Errands")
	task := createTask(t, router, project.ID, "Buy groceries")
	subtask := createSubTask(t, router, task.ID, "Buy coffee")

	res := request(t, router, http.MethodGet, "/api/v1/sync/snapshot", nil)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var snapshot domain.SyncSnapshot
	decode(t, res, &snapshot)
	if snapshot.ServerTime.IsZero() {
		t.Fatalf("expected server_time")
	}
	if !snapshotHasProject(snapshot, project.ID) || !snapshotHasTask(snapshot, task.ID) || !snapshotHasSubTask(snapshot, subtask.ID) {
		t.Fatalf("snapshot missing created objects: %+v", snapshot)
	}
	if !snapshotHasTemplate(snapshot, "daily") || !snapshotHasTemplate(snapshot, "spring_chores") || !snapshotHasTemplate(snapshot, "leaving_house") {
		t.Fatalf("snapshot missing seeded templates: %+v", snapshot.Templates)
	}
}

func TestSyncSnapshotUpdatedSinceIncludesDeletedRows(t *testing.T) {
	_, router := testRouter()
	project := createProject(t, router, "Errands")
	task := createTask(t, router, project.ID, "Buy groceries")
	subtask := createSubTask(t, router, task.ID, "Buy coffee")
	res := request(t, router, http.MethodDelete, "/api/v1/tasks/"+task.ID, nil)
	if res.Code != http.StatusNoContent {
		t.Fatalf("expected delete 204, got %d: %s", res.Code, res.Body.String())
	}

	res = request(t, router, http.MethodGet, "/api/v1/sync/snapshot?updated_since=2026-05-08T11:00:00Z", nil)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var snapshot domain.SyncSnapshot
	decode(t, res, &snapshot)
	deletedTask := findSnapshotTask(t, snapshot, task.ID)
	if deletedTask.DeletedAt == nil {
		t.Fatalf("expected deleted task in incremental snapshot: %+v", deletedTask)
	}
	deletedSubTask := findSnapshotSubTask(t, snapshot, subtask.ID)
	if deletedSubTask.DeletedAt == nil {
		t.Fatalf("expected deleted subtask in incremental snapshot: %+v", deletedSubTask)
	}
}

func TestSyncSnapshotRejectsInvalidUpdatedSince(t *testing.T) {
	_, router := testRouter()
	res := request(t, router, http.MethodGet, "/api/v1/sync/snapshot?updated_since=yesterday", nil)
	if res.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d: %s", res.Code, res.Body.String())
	}
}

func testRouter() (*service.Services, http.Handler) {
	now := func() time.Time { return time.Date(2026, 5, 8, 12, 0, 0, 0, time.UTC) }
	repo := service.NewSeededMemoryRepository(now)
	services := service.New(repo)
	return services, api.NewRouter(services)
}

func request(t *testing.T, router http.Handler, method, path string, body any) *httptest.ResponseRecorder {
	t.Helper()
	var buf bytes.Buffer
	if body != nil {
		if err := json.NewEncoder(&buf).Encode(body); err != nil {
			t.Fatal(err)
		}
	}
	req := httptest.NewRequest(method, path, &buf)
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	res := httptest.NewRecorder()
	router.ServeHTTP(res, req)
	return res
}

func decode(t *testing.T, res *httptest.ResponseRecorder, v any) {
	t.Helper()
	if err := json.NewDecoder(res.Body).Decode(v); err != nil {
		t.Fatalf("decode response: %v; body=%s", err, res.Body.String())
	}
}

func createProject(t *testing.T, router http.Handler, name string) domain.Project {
	t.Helper()
	res := request(t, router, http.MethodPost, "/api/v1/projects", map[string]any{"name": name})
	if res.Code != http.StatusCreated {
		t.Fatalf("create project failed: %d %s", res.Code, res.Body.String())
	}
	var p domain.Project
	decode(t, res, &p)
	return p
}

func createTask(t *testing.T, router http.Handler, projectID, name string) domain.Task {
	t.Helper()
	res := request(t, router, http.MethodPost, "/api/v1/projects/"+projectID+"/tasks", map[string]any{"name": name})
	if res.Code != http.StatusCreated {
		t.Fatalf("create task failed: %d %s", res.Code, res.Body.String())
	}
	var task domain.Task
	decode(t, res, &task)
	return task
}

func createSubTask(t *testing.T, router http.Handler, taskID, name string) domain.SubTask {
	t.Helper()
	res := request(t, router, http.MethodPost, "/api/v1/tasks/"+taskID+"/subtasks", map[string]any{"name": name})
	if res.Code != http.StatusCreated {
		t.Fatalf("create subtask failed: %d %s", res.Code, res.Body.String())
	}
	var subtask domain.SubTask
	decode(t, res, &subtask)
	return subtask
}

func coreProject(t *testing.T, router http.Handler, key string) domain.Project {
	t.Helper()
	res := request(t, router, http.MethodGet, "/api/v1/projects", nil)
	if res.Code != http.StatusOK {
		t.Fatalf("list projects failed: %d", res.Code)
	}
	var projects []domain.Project
	decode(t, res, &projects)
	for _, p := range projects {
		if p.CoreKey != nil && *p.CoreKey == key {
			return p
		}
	}
	t.Fatalf("core project %q not found", key)
	return domain.Project{}
}

func findProjectSummary(t *testing.T, projects []domain.ProjectSummary, id string) domain.ProjectSummary {
	t.Helper()
	for _, project := range projects {
		if project.ID == id {
			return project
		}
	}
	t.Fatalf("project summary %q not found in %+v", id, projects)
	return domain.ProjectSummary{}
}

func findProjectSummaryByCoreKey(t *testing.T, projects []domain.ProjectSummary, key string) domain.ProjectSummary {
	t.Helper()
	for _, project := range projects {
		if project.CoreKey != nil && *project.CoreKey == key {
			return project
		}
	}
	t.Fatalf("core project summary %q not found in %+v", key, projects)
	return domain.ProjectSummary{}
}

func hasTemplateAction(actions []domain.TemplateAction, key string) bool {
	for _, action := range actions {
		if action.TemplateKey == key && action.GenerateEndpoint == "/api/v1/templates/"+key+"/generate" {
			return true
		}
	}
	return false
}

func findNestedTask(t *testing.T, tasks []domain.TaskWithSubTasks, id string) domain.TaskWithSubTasks {
	t.Helper()
	for _, task := range tasks {
		if task.ID == id {
			return task
		}
	}
	t.Fatalf("nested task %q not found in %+v", id, tasks)
	return domain.TaskWithSubTasks{}
}

func snapshotHasProject(snapshot domain.SyncSnapshot, id string) bool {
	for _, project := range snapshot.Projects {
		if project.ID == id {
			return true
		}
	}
	return false
}

func snapshotHasTask(snapshot domain.SyncSnapshot, id string) bool {
	for _, task := range snapshot.Tasks {
		if task.ID == id {
			return true
		}
	}
	return false
}

func snapshotHasSubTask(snapshot domain.SyncSnapshot, id string) bool {
	for _, subtask := range snapshot.SubTasks {
		if subtask.ID == id {
			return true
		}
	}
	return false
}

func snapshotHasTemplate(snapshot domain.SyncSnapshot, key string) bool {
	for _, template := range snapshot.Templates {
		if template.TemplateKey == key {
			return true
		}
	}
	return false
}

func findSnapshotTask(t *testing.T, snapshot domain.SyncSnapshot, id string) domain.Task {
	t.Helper()
	for _, task := range snapshot.Tasks {
		if task.ID == id {
			return task
		}
	}
	t.Fatalf("snapshot task %q not found in %+v", id, snapshot.Tasks)
	return domain.Task{}
}

func findSnapshotSubTask(t *testing.T, snapshot domain.SyncSnapshot, id string) domain.SubTask {
	t.Helper()
	for _, subtask := range snapshot.SubTasks {
		if subtask.ID == id {
			return subtask
		}
	}
	t.Fatalf("snapshot subtask %q not found in %+v", id, snapshot.SubTasks)
	return domain.SubTask{}
}
