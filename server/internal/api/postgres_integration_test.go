package api_test

import (
	"context"
	"database/sql"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"ado/internal/api"
	storedb "ado/internal/db"
	"ado/internal/domain"
	"ado/internal/service"
)

func TestPostgresIntegrationMigrationsAndSeedData(t *testing.T) {
	conn, router := postgresIntegrationRouter(t)
	defer conn.Close()

	res := request(t, router, http.MethodGet, "/api/v1/projects", nil)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var projects []domain.Project
	decode(t, res, &projects)
	assertCoreProject(t, projects, "daily", "Daily")
	assertCoreProject(t, projects, "home", "Home")

	res = request(t, router, http.MethodGet, "/api/v1/templates", nil)
	if res.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", res.Code, res.Body.String())
	}
	var templates []domain.Template
	decode(t, res, &templates)
	if len(templates) != 6 {
		t.Fatalf("expected 6 seeded templates, got %d", len(templates))
	}
	assertTemplateItems(t, templates, "daily", []string{"review calendar", "set priorities"})
	assertTemplateItems(t, templates, "spring_chores", []string{"seasonal home check"})
	assertTemplateItems(t, templates, "leaving_house", []string{
		"Lights off",
		"Small appliances unplugged",
		"Refrigerator / freezer doors shut",
		"Oven / stove off",
		"Doors locked",
		"Garage door closed",
		"Alarm set",
	})

	var migrationCount int
	if err := conn.QueryRow(`SELECT count(*) FROM schema_migrations`).Scan(&migrationCount); err != nil {
		t.Fatalf("query schema_migrations: %v", err)
	}
	if migrationCount != 3 {
		t.Fatalf("expected 3 migrations, got %d", migrationCount)
	}
}

func TestPostgresIntegrationProjectTaskSubTaskFlow(t *testing.T) {
	conn, router := postgresIntegrationRouter(t)
	defer conn.Close()

	project := createProject(t, router, "Errands")
	if project.ID == "" || project.Tags == nil {
		t.Fatalf("expected project fields to scan correctly: %+v", project)
	}

	res := request(t, router, http.MethodPost, "/api/v1/projects", map[string]any{"name": "errands"})
	if res.Code != http.StatusConflict {
		t.Fatalf("expected duplicate project name conflict, got %d: %s", res.Code, res.Body.String())
	}

	res = request(t, router, http.MethodPatch, "/api/v1/projects/"+project.ID, map[string]any{"description": "Outside tasks", "tags": []string{"personal", "outside"}})
	if res.Code != http.StatusOK {
		t.Fatalf("expected project update 200, got %d: %s", res.Code, res.Body.String())
	}
	var updatedProject domain.Project
	decode(t, res, &updatedProject)
	if updatedProject.Description != "Outside tasks" || len(updatedProject.Tags) != 2 {
		t.Fatalf("unexpected updated project: %+v", updatedProject)
	}

	task := createTask(t, router, project.ID, "Buy groceries")
	subtask := createSubTask(t, router, task.ID, "Buy coffee")

	res = request(t, router, http.MethodPatch, "/api/v1/tasks/"+task.ID, map[string]any{"status": "done"})
	if res.Code != http.StatusOK {
		t.Fatalf("expected task done 200, got %d: %s", res.Code, res.Body.String())
	}
	var doneTask domain.Task
	decode(t, res, &doneTask)
	if doneTask.FinishedAt == nil {
		t.Fatalf("expected finished_at after done status")
	}

	res = request(t, router, http.MethodPatch, "/api/v1/tasks/"+task.ID, map[string]any{"status": "todo"})
	if res.Code != http.StatusOK {
		t.Fatalf("expected task reopen 200, got %d: %s", res.Code, res.Body.String())
	}
	var reopenedTask domain.Task
	decode(t, res, &reopenedTask)
	if reopenedTask.FinishedAt != nil {
		t.Fatalf("expected finished_at to clear after leaving done")
	}

	res = request(t, router, http.MethodPatch, "/api/v1/subtasks/"+subtask.ID, map[string]any{"status": "done"})
	if res.Code != http.StatusOK {
		t.Fatalf("expected subtask done 200, got %d: %s", res.Code, res.Body.String())
	}
	var doneSubTask domain.SubTask
	decode(t, res, &doneSubTask)
	if doneSubTask.FinishedAt == nil {
		t.Fatalf("expected subtask finished_at after done status")
	}

	res = request(t, router, http.MethodDelete, "/api/v1/tasks/"+task.ID, nil)
	if res.Code != http.StatusNoContent {
		t.Fatalf("expected task delete 204, got %d: %s", res.Code, res.Body.String())
	}
	assertSoftDeleted(t, conn, "tasks", task.ID)
	assertSoftDeleted(t, conn, "subtasks", subtask.ID)

	res = request(t, router, http.MethodGet, "/api/v1/tasks/"+task.ID, nil)
	if res.Code != http.StatusNotFound {
		t.Fatalf("expected deleted task to be hidden, got %d", res.Code)
	}
}

func TestPostgresIntegrationTemplateUpdateAndGeneration(t *testing.T) {
	conn, router := postgresIntegrationRouter(t)
	defer conn.Close()

	res := request(t, router, http.MethodPatch, "/api/v1/templates/daily", map[string]any{
		"items": []map[string]any{
			{"name": "review calendar", "description": "", "position": 0},
			{"name": "set priorities", "description": "", "position": 1},
			{"name": "check weather", "description": "", "position": 2},
		},
	})
	if res.Code != http.StatusOK {
		t.Fatalf("expected template update 200, got %d: %s", res.Code, res.Body.String())
	}
	var daily domain.Template
	decode(t, res, &daily)
	if len(daily.Items) != 3 || daily.Items[2].Name != "check weather" {
		t.Fatalf("unexpected daily template items: %+v", daily.Items)
	}

	res = request(t, router, http.MethodPost, "/api/v1/templates/daily/generate", map[string]any{"date": "2026-05-08"})
	if res.Code != http.StatusCreated {
		t.Fatalf("expected daily generation 201, got %d: %s", res.Code, res.Body.String())
	}
	var generated domain.GeneratedTask
	decode(t, res, &generated)
	if generated.Name != "2026-05-08 Friday" || generated.SubtasksCreated != 3 {
		t.Fatalf("unexpected generated daily task: %+v", generated)
	}
	var subtaskCount int
	if err := conn.QueryRow(`SELECT count(*) FROM subtasks WHERE task_id = $1 AND deleted_at IS NULL`, generated.TaskID).Scan(&subtaskCount); err != nil {
		t.Fatalf("count generated subtasks: %v", err)
	}
	if subtaskCount != 3 {
		t.Fatalf("expected 3 generated subtasks in database, got %d", subtaskCount)
	}

	res = request(t, router, http.MethodPost, "/api/v1/templates/daily/generate", map[string]any{"date": "2026-05-08"})
	if res.Code != http.StatusConflict {
		t.Fatalf("expected duplicate daily 409, got %d: %s", res.Code, res.Body.String())
	}

	res = request(t, router, http.MethodPost, "/api/v1/templates/spring_chores/generate", map[string]any{"year": 2026})
	if res.Code != http.StatusCreated {
		t.Fatalf("expected spring generation 201, got %d: %s", res.Code, res.Body.String())
	}
	var chore domain.GeneratedTask
	decode(t, res, &chore)
	if chore.Name != "Spring chores 2026" || chore.SubtasksCreated != 1 {
		t.Fatalf("unexpected generated chore task: %+v", chore)
	}

	res = request(t, router, http.MethodPost, "/api/v1/templates/leaving_house/generate", map[string]any{})
	if res.Code != http.StatusCreated {
		t.Fatalf("expected leaving house generation 201, got %d: %s", res.Code, res.Body.String())
	}
	var leaving domain.GeneratedTask
	decode(t, res, &leaving)
	if leaving.Name != "Leaving house" || leaving.SubtasksCreated != 7 {
		t.Fatalf("unexpected generated leaving house task: %+v", leaving)
	}
}

func postgresIntegrationRouter(t *testing.T) (*sql.DB, http.Handler) {
	t.Helper()

	databaseURL := os.Getenv("ADO_TEST_DATABASE_URL")
	if databaseURL == "" {
		t.Skip("set ADO_TEST_DATABASE_URL to run PostgreSQL integration tests")
	}
	if err := assertSafeTestDatabaseURL(databaseURL); err != nil {
		t.Fatal(err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	t.Cleanup(cancel)

	conn, err := storedb.Open(ctx, databaseURL)
	if err != nil {
		t.Fatalf("open test database: %v", err)
	}
	if err := resetPostgresTestDatabase(ctx, conn); err != nil {
		_ = conn.Close()
		t.Fatalf("reset test database: %v", err)
	}
	if err := storedb.RunMigrations(ctx, conn, migrationsDir(t)); err != nil {
		_ = conn.Close()
		t.Fatalf("run migrations: %v", err)
	}

	services := service.New(storedb.NewStore(conn))
	return conn, api.NewRouter(services)
}

func migrationsDir(t *testing.T) string {
	t.Helper()

	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("get working directory: %v", err)
	}
	for dir := cwd; ; dir = filepath.Dir(dir) {
		candidate := filepath.Join(dir, "migrations")
		if info, err := os.Stat(candidate); err == nil && info.IsDir() {
			return candidate
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			t.Fatalf("migrations directory not found from %s", cwd)
		}
	}
}

func assertSafeTestDatabaseURL(databaseURL string) error {
	parsed, err := url.Parse(databaseURL)
	if err != nil {
		return fmt.Errorf("parse ADO_TEST_DATABASE_URL: %w", err)
	}
	name := strings.Trim(parsed.Path, "/")
	if !strings.Contains(strings.ToLower(name), "test") {
		return fmt.Errorf("refusing to reset database %q; ADO_TEST_DATABASE_URL database name must contain \"test\"", name)
	}
	return nil
}

func resetPostgresTestDatabase(ctx context.Context, conn *sql.DB) error {
	if _, err := conn.ExecContext(ctx, `DROP SCHEMA IF EXISTS public CASCADE`); err != nil {
		return err
	}
	if _, err := conn.ExecContext(ctx, `CREATE SCHEMA public`); err != nil {
		return err
	}
	return nil
}

func assertCoreProject(t *testing.T, projects []domain.Project, key string, name string) {
	t.Helper()
	for _, project := range projects {
		if project.CoreKey != nil && *project.CoreKey == key {
			if !project.IsCore || project.Name != name {
				t.Fatalf("unexpected core project %q: %+v", key, project)
			}
			return
		}
	}
	t.Fatalf("core project %q not found in %+v", key, projects)
}

func assertTemplateItems(t *testing.T, templates []domain.Template, key string, names []string) {
	t.Helper()
	for _, template := range templates {
		if template.TemplateKey != key {
			continue
		}
		if len(template.Items) != len(names) {
			t.Fatalf("template %q expected %d items, got %d", key, len(names), len(template.Items))
		}
		for i, name := range names {
			if template.Items[i].Name != name {
				t.Fatalf("template %q item %d expected %q, got %q", key, i, name, template.Items[i].Name)
			}
		}
		return
	}
	t.Fatalf("template %q not found", key)
}

func assertSoftDeleted(t *testing.T, conn *sql.DB, table string, id string) {
	t.Helper()
	if table != "tasks" && table != "subtasks" {
		t.Fatalf("unsupported soft-delete table %q", table)
	}
	var deletedAt sql.NullTime
	query := fmt.Sprintf(`SELECT deleted_at FROM %s WHERE id = $1`, table)
	if err := conn.QueryRow(query, id).Scan(&deletedAt); err != nil {
		t.Fatalf("query soft delete for %s %s: %v", table, id, err)
	}
	if !deletedAt.Valid || deletedAt.Time.After(time.Now().Add(time.Minute)) {
		t.Fatalf("expected %s %s to be soft deleted, got %+v", table, id, deletedAt)
	}
}
