package db

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"strings"
	"time"

	"ado/internal/domain"

	"github.com/jackc/pgx/v5/pgtype"
)

type Store struct {
	db *sql.DB
}

func NewStore(db *sql.DB) *Store {
	return &Store{db: db}
}

func (s *Store) ListProjects(ctx context.Context) ([]domain.Project, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT id::text, name, description, to_json(tags)::text, is_core, core_key, created_at, updated_at, deleted_at FROM projects WHERE deleted_at IS NULL ORDER BY is_core DESC, name`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []domain.Project{}
	for rows.Next() {
		p, err := scanProject(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

func (s *Store) CreateProject(ctx context.Context, p domain.Project) (domain.Project, error) {
	row := s.db.QueryRowContext(ctx, `INSERT INTO projects (name, description, tags) VALUES ($1, $2, $3)
		RETURNING id::text, name, description, to_json(tags)::text, is_core, core_key, created_at, updated_at, deleted_at`, p.Name, p.Description, pgtype.FlatArray[string](p.Tags))
	out, err := scanProject(row)
	return out, mapSQLError(err, "project name already exists")
}

func (s *Store) GetProject(ctx context.Context, id string) (domain.Project, error) {
	row := s.db.QueryRowContext(ctx, `SELECT id::text, name, description, to_json(tags)::text, is_core, core_key, created_at, updated_at, deleted_at FROM projects WHERE id = $1 AND deleted_at IS NULL`, id)
	p, err := scanProject(row)
	if errors.Is(err, sql.ErrNoRows) {
		return domain.Project{}, domain.NotFound("project not found")
	}
	return p, err
}

func (s *Store) UpdateProject(ctx context.Context, id string, patch domain.ProjectPatch) (domain.Project, error) {
	p, err := s.GetProject(ctx, id)
	if err != nil {
		return domain.Project{}, err
	}
	if patch.Name != nil {
		p.Name = *patch.Name
	}
	if patch.Description != nil {
		p.Description = *patch.Description
	}
	if patch.Tags != nil {
		p.Tags = append([]string(nil), (*patch.Tags)...)
	}
	row := s.db.QueryRowContext(ctx, `UPDATE projects SET name = $2, description = $3, tags = $4, updated_at = now()
		WHERE id = $1 AND deleted_at IS NULL
		RETURNING id::text, name, description, to_json(tags)::text, is_core, core_key, created_at, updated_at, deleted_at`, id, p.Name, p.Description, pgtype.FlatArray[string](p.Tags))
	out, err := scanProject(row)
	return out, mapSQLError(err, "project name already exists")
}

func (s *Store) DeleteProject(ctx context.Context, id string) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	res, err := tx.ExecContext(ctx, `UPDATE projects SET deleted_at = now(), updated_at = now() WHERE id = $1 AND deleted_at IS NULL`, id)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return domain.NotFound("project not found")
	}
	if _, err := tx.ExecContext(ctx, `UPDATE subtasks SET deleted_at = now(), updated_at = now()
		WHERE task_id IN (SELECT id FROM tasks WHERE project_id = $1) AND deleted_at IS NULL`, id); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `UPDATE tasks SET deleted_at = now(), updated_at = now() WHERE project_id = $1 AND deleted_at IS NULL`, id); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) ListTasks(ctx context.Context, projectID string) ([]domain.Task, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT id::text, project_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at
		FROM tasks WHERE project_id = $1 AND deleted_at IS NULL ORDER BY created_at`, projectID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []domain.Task{}
	for rows.Next() {
		t, err := scanTask(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, t)
	}
	return out, rows.Err()
}

func (s *Store) CreateTask(ctx context.Context, t domain.Task) (domain.Task, error) {
	row := s.db.QueryRowContext(ctx, `INSERT INTO tasks (project_id, name, description, status) VALUES ($1, $2, $3, $4)
		RETURNING id::text, project_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at`, t.ProjectID, t.Name, t.Description, t.Status)
	out, err := scanTask(row)
	return out, mapSQLError(err, "task could not be created")
}

func (s *Store) GetTask(ctx context.Context, id string) (domain.Task, error) {
	row := s.db.QueryRowContext(ctx, `SELECT id::text, project_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at FROM tasks WHERE id = $1 AND deleted_at IS NULL`, id)
	t, err := scanTask(row)
	if errors.Is(err, sql.ErrNoRows) {
		return domain.Task{}, domain.NotFound("task not found")
	}
	return t, err
}

func (s *Store) UpdateTask(ctx context.Context, id string, patch domain.TaskPatch) (domain.Task, error) {
	t, err := s.GetTask(ctx, id)
	if err != nil {
		return domain.Task{}, err
	}
	if patch.ProjectID != nil {
		t.ProjectID = *patch.ProjectID
	}
	if patch.Name != nil {
		t.Name = *patch.Name
	}
	if patch.Description != nil {
		t.Description = *patch.Description
	}
	if patch.Status != nil {
		t.Status = *patch.Status
	}
	if patch.FinishedAt != nil {
		t.FinishedAt = patch.FinishedAt
	}
	if patch.ClearFinishedAt {
		t.FinishedAt = nil
	}
	row := s.db.QueryRowContext(ctx, `UPDATE tasks SET project_id = $2, name = $3, description = $4, status = $5, finished_at = $6, updated_at = now()
		WHERE id = $1 AND deleted_at IS NULL
		RETURNING id::text, project_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at`, id, t.ProjectID, t.Name, t.Description, t.Status, t.FinishedAt)
	return scanTask(row)
}

func (s *Store) DeleteTask(ctx context.Context, id string) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	res, err := tx.ExecContext(ctx, `UPDATE tasks SET deleted_at = now(), updated_at = now() WHERE id = $1 AND deleted_at IS NULL`, id)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return domain.NotFound("task not found")
	}
	if _, err := tx.ExecContext(ctx, `UPDATE subtasks SET deleted_at = now(), updated_at = now() WHERE task_id = $1 AND deleted_at IS NULL`, id); err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) FindTaskByProjectName(ctx context.Context, projectID string, name string) (*domain.Task, error) {
	row := s.db.QueryRowContext(ctx, `SELECT id::text, project_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at
		FROM tasks WHERE project_id = $1 AND name = $2 AND deleted_at IS NULL`, projectID, name)
	t, err := scanTask(row)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &t, nil
}

func (s *Store) CreateTaskWithSubtasks(ctx context.Context, t domain.Task, items []domain.TemplateItem) (domain.Task, int, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return domain.Task{}, 0, err
	}
	defer tx.Rollback()
	row := tx.QueryRowContext(ctx, `INSERT INTO tasks (project_id, name, description, status) VALUES ($1, $2, $3, $4)
		RETURNING id::text, project_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at`, t.ProjectID, t.Name, t.Description, domain.StatusTodo)
	created, err := scanTask(row)
	if err != nil {
		return domain.Task{}, 0, err
	}
	for _, item := range items {
		if _, err := tx.ExecContext(ctx, `INSERT INTO subtasks (task_id, name, description, status) VALUES ($1, $2, $3, 'todo')`, created.ID, item.Name, item.Description); err != nil {
			return domain.Task{}, 0, err
		}
	}
	if err := tx.Commit(); err != nil {
		return domain.Task{}, 0, err
	}
	return created, len(items), nil
}

func (s *Store) ListSubTasks(ctx context.Context, taskID string) ([]domain.SubTask, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT id::text, task_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at
		FROM subtasks WHERE task_id = $1 AND deleted_at IS NULL ORDER BY created_at`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []domain.SubTask{}
	for rows.Next() {
		st, err := scanSubTask(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, st)
	}
	return out, rows.Err()
}

func (s *Store) CreateSubTask(ctx context.Context, st domain.SubTask) (domain.SubTask, error) {
	row := s.db.QueryRowContext(ctx, `INSERT INTO subtasks (task_id, name, description, status) VALUES ($1, $2, $3, $4)
		RETURNING id::text, task_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at`, st.TaskID, st.Name, st.Description, st.Status)
	out, err := scanSubTask(row)
	return out, mapSQLError(err, "subtask could not be created")
}

func (s *Store) GetSubTask(ctx context.Context, id string) (domain.SubTask, error) {
	row := s.db.QueryRowContext(ctx, `SELECT id::text, task_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at FROM subtasks WHERE id = $1 AND deleted_at IS NULL`, id)
	st, err := scanSubTask(row)
	if errors.Is(err, sql.ErrNoRows) {
		return domain.SubTask{}, domain.NotFound("subtask not found")
	}
	return st, err
}

func (s *Store) UpdateSubTask(ctx context.Context, id string, patch domain.SubTaskPatch) (domain.SubTask, error) {
	st, err := s.GetSubTask(ctx, id)
	if err != nil {
		return domain.SubTask{}, err
	}
	if patch.TaskID != nil {
		st.TaskID = *patch.TaskID
	}
	if patch.Name != nil {
		st.Name = *patch.Name
	}
	if patch.Description != nil {
		st.Description = *patch.Description
	}
	if patch.Status != nil {
		st.Status = *patch.Status
	}
	if patch.FinishedAt != nil {
		st.FinishedAt = patch.FinishedAt
	}
	if patch.ClearFinishedAt {
		st.FinishedAt = nil
	}
	row := s.db.QueryRowContext(ctx, `UPDATE subtasks SET task_id = $2, name = $3, description = $4, status = $5, finished_at = $6, updated_at = now()
		WHERE id = $1 AND deleted_at IS NULL
		RETURNING id::text, task_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at`, id, st.TaskID, st.Name, st.Description, st.Status, st.FinishedAt)
	return scanSubTask(row)
}

func (s *Store) DeleteSubTask(ctx context.Context, id string) error {
	res, err := s.db.ExecContext(ctx, `UPDATE subtasks SET deleted_at = now(), updated_at = now() WHERE id = $1 AND deleted_at IS NULL`, id)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return domain.NotFound("subtask not found")
	}
	return nil
}

func (s *Store) ListTemplates(ctx context.Context) ([]domain.Template, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT tt.id::text, tt.project_id::text, tt.template_key, tt.name, tt.description, p.core_key, tt.is_system, tt.created_at, tt.updated_at
		FROM task_templates tt JOIN projects p ON p.id = tt.project_id ORDER BY tt.template_key`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []domain.Template{}
	for rows.Next() {
		tpl, err := scanTemplateBase(rows)
		if err != nil {
			return nil, err
		}
		items, err := s.templateItems(ctx, tpl.ID)
		if err != nil {
			return nil, err
		}
		tpl.Items = items
		out = append(out, tpl)
	}
	return out, rows.Err()
}

func (s *Store) GetTemplate(ctx context.Context, key string) (domain.Template, error) {
	row := s.db.QueryRowContext(ctx, `SELECT tt.id::text, tt.project_id::text, tt.template_key, tt.name, tt.description, p.core_key, tt.is_system, tt.created_at, tt.updated_at
		FROM task_templates tt JOIN projects p ON p.id = tt.project_id WHERE tt.template_key = $1`, key)
	tpl, err := scanTemplateBase(row)
	if errors.Is(err, sql.ErrNoRows) {
		return domain.Template{}, domain.NotFound("template not found")
	}
	if err != nil {
		return domain.Template{}, err
	}
	tpl.Items, err = s.templateItems(ctx, tpl.ID)
	return tpl, err
}

func (s *Store) ReplaceTemplateItems(ctx context.Context, key string, items []domain.TemplateItem) (domain.Template, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return domain.Template{}, err
	}
	defer tx.Rollback()
	var templateID string
	if err := tx.QueryRowContext(ctx, `SELECT id::text FROM task_templates WHERE template_key = $1`, key).Scan(&templateID); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return domain.Template{}, domain.NotFound("template not found")
		}
		return domain.Template{}, err
	}
	if _, err := tx.ExecContext(ctx, `DELETE FROM task_template_items WHERE template_id = $1`, templateID); err != nil {
		return domain.Template{}, err
	}
	for i, item := range items {
		if item.Position == 0 && i != 0 {
			item.Position = i
		}
		if _, err := tx.ExecContext(ctx, `INSERT INTO task_template_items (template_id, name, description, position) VALUES ($1, $2, $3, $4)`, templateID, item.Name, item.Description, item.Position); err != nil {
			return domain.Template{}, err
		}
	}
	if _, err := tx.ExecContext(ctx, `UPDATE task_templates SET updated_at = now() WHERE id = $1`, templateID); err != nil {
		return domain.Template{}, err
	}
	if err := tx.Commit(); err != nil {
		return domain.Template{}, err
	}
	return s.GetTemplate(ctx, key)
}

func (s *Store) ListProjectsForSync(ctx context.Context, updatedSince *time.Time) ([]domain.Project, error) {
	query := `SELECT id::text, name, description, to_json(tags)::text, is_core, core_key, created_at, updated_at, deleted_at FROM projects WHERE deleted_at IS NULL ORDER BY is_core DESC, name`
	args := []any{}
	if updatedSince != nil {
		query = `SELECT id::text, name, description, to_json(tags)::text, is_core, core_key, created_at, updated_at, deleted_at FROM projects WHERE updated_at > $1 OR deleted_at > $1 ORDER BY updated_at, name`
		args = append(args, *updatedSince)
	}
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []domain.Project{}
	for rows.Next() {
		p, err := scanProject(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

func (s *Store) ListTasksForSync(ctx context.Context, updatedSince *time.Time) ([]domain.Task, error) {
	query := `SELECT id::text, project_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at FROM tasks WHERE deleted_at IS NULL ORDER BY created_at`
	args := []any{}
	if updatedSince != nil {
		query = `SELECT id::text, project_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at FROM tasks WHERE updated_at > $1 OR deleted_at > $1 ORDER BY updated_at, created_at`
		args = append(args, *updatedSince)
	}
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []domain.Task{}
	for rows.Next() {
		t, err := scanTask(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, t)
	}
	return out, rows.Err()
}

func (s *Store) ListSubTasksForSync(ctx context.Context, updatedSince *time.Time) ([]domain.SubTask, error) {
	query := `SELECT id::text, task_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at FROM subtasks WHERE deleted_at IS NULL ORDER BY created_at`
	args := []any{}
	if updatedSince != nil {
		query = `SELECT id::text, task_id::text, name, description, status::text, created_at, finished_at, updated_at, deleted_at FROM subtasks WHERE updated_at > $1 OR deleted_at > $1 ORDER BY updated_at, created_at`
		args = append(args, *updatedSince)
	}
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []domain.SubTask{}
	for rows.Next() {
		st, err := scanSubTask(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, st)
	}
	return out, rows.Err()
}

func (s *Store) ListTemplatesForSync(ctx context.Context, updatedSince *time.Time) ([]domain.Template, error) {
	query := `SELECT tt.id::text, tt.project_id::text, tt.template_key, tt.name, tt.description, p.core_key, tt.is_system, tt.created_at, tt.updated_at
		FROM task_templates tt JOIN projects p ON p.id = tt.project_id ORDER BY tt.template_key`
	args := []any{}
	if updatedSince != nil {
		query = `SELECT tt.id::text, tt.project_id::text, tt.template_key, tt.name, tt.description, p.core_key, tt.is_system, tt.created_at, tt.updated_at
			FROM task_templates tt JOIN projects p ON p.id = tt.project_id WHERE tt.updated_at > $1 ORDER BY tt.updated_at, tt.template_key`
		args = append(args, *updatedSince)
	}
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []domain.Template{}
	for rows.Next() {
		tpl, err := scanTemplateBase(rows)
		if err != nil {
			return nil, err
		}
		items, err := s.templateItems(ctx, tpl.ID)
		if err != nil {
			return nil, err
		}
		tpl.Items = items
		out = append(out, tpl)
	}
	return out, rows.Err()
}

func (s *Store) templateItems(ctx context.Context, templateID string) ([]domain.TemplateItem, error) {
	rows, err := s.db.QueryContext(ctx, `SELECT id::text, template_id::text, name, description, position, created_at, updated_at
		FROM task_template_items WHERE template_id = $1 ORDER BY position, created_at`, templateID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []domain.TemplateItem{}
	for rows.Next() {
		var item domain.TemplateItem
		if err := rows.Scan(&item.ID, &item.TemplateID, &item.Name, &item.Description, &item.Position, &item.CreatedAt, &item.UpdatedAt); err != nil {
			return nil, err
		}
		out = append(out, item)
	}
	return out, rows.Err()
}

type scanner interface {
	Scan(dest ...any) error
}

func scanProject(row scanner) (domain.Project, error) {
	var p domain.Project
	var coreKey sql.NullString
	var tagsJSON string
	if err := row.Scan(&p.ID, &p.Name, &p.Description, &tagsJSON, &p.IsCore, &coreKey, &p.CreatedAt, &p.UpdatedAt, &p.DeletedAt); err != nil {
		return domain.Project{}, err
	}
	if err := json.Unmarshal([]byte(tagsJSON), &p.Tags); err != nil {
		return domain.Project{}, err
	}
	if coreKey.Valid {
		p.CoreKey = &coreKey.String
	}
	if p.Tags == nil {
		p.Tags = []string{}
	}
	return p, nil
}

func scanTask(row scanner) (domain.Task, error) {
	var t domain.Task
	if err := row.Scan(&t.ID, &t.ProjectID, &t.Name, &t.Description, &t.Status, &t.CreatedAt, &t.FinishedAt, &t.UpdatedAt, &t.DeletedAt); err != nil {
		return domain.Task{}, err
	}
	return t, nil
}

func scanSubTask(row scanner) (domain.SubTask, error) {
	var st domain.SubTask
	if err := row.Scan(&st.ID, &st.TaskID, &st.Name, &st.Description, &st.Status, &st.CreatedAt, &st.FinishedAt, &st.UpdatedAt, &st.DeletedAt); err != nil {
		return domain.SubTask{}, err
	}
	return st, nil
}

func scanTemplateBase(row scanner) (domain.Template, error) {
	var t domain.Template
	if err := row.Scan(&t.ID, &t.ProjectID, &t.TemplateKey, &t.Name, &t.Description, &t.ProjectCoreKey, &t.IsSystem, &t.CreatedAt, &t.UpdatedAt); err != nil {
		return domain.Template{}, err
	}
	return t, nil
}

func mapSQLError(err error, conflictMessage string) error {
	if err == nil {
		return nil
	}
	if errors.Is(err, sql.ErrNoRows) {
		return domain.NotFound("not found")
	}
	msg := err.Error()
	if strings.Contains(msg, "duplicate key") || strings.Contains(msg, "unique constraint") {
		return domain.Conflict(conflictMessage)
	}
	if strings.Contains(msg, "foreign key") {
		return domain.NotFound("related object not found")
	}
	return err
}
