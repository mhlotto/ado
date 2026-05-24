package service

import (
	"context"
	"sort"
	"strings"
	"sync"
	"time"

	"ado/internal/domain"

	"github.com/google/uuid"
)

type MemoryRepository struct {
	mu        sync.Mutex
	now       func() time.Time
	projects  map[string]domain.Project
	tasks     map[string]domain.Task
	subtasks  map[string]domain.SubTask
	templates map[string]domain.Template
}

func NewSeededMemoryRepository(now func() time.Time) *MemoryRepository {
	if now == nil {
		now = func() time.Time { return time.Now().UTC() }
	}
	r := &MemoryRepository{
		now:       now,
		projects:  map[string]domain.Project{},
		tasks:     map[string]domain.Task{},
		subtasks:  map[string]domain.SubTask{},
		templates: map[string]domain.Template{},
	}
	dailyKey, homeKey := "daily", "home"
	daily := domain.Project{ID: uuid.NewString(), Name: "Daily", Tags: []string{}, IsCore: true, CoreKey: &dailyKey, CreatedAt: now(), UpdatedAt: now()}
	home := domain.Project{ID: uuid.NewString(), Name: "Home", Tags: []string{}, IsCore: true, CoreKey: &homeKey, CreatedAt: now(), UpdatedAt: now()}
	r.projects[daily.ID] = daily
	r.projects[home.ID] = home
	r.templates["daily"] = seededTemplate(daily.ID, "daily", "Daily", "daily", []string{"review calendar", "set priorities"}, now)
	r.templates["summer_chores"] = seededTemplate(home.ID, "summer_chores", "Summer chores", "home", []string{"seasonal home check"}, now)
	r.templates["fall_chores"] = seededTemplate(home.ID, "fall_chores", "Fall chores", "home", []string{"seasonal home check"}, now)
	r.templates["winter_chores"] = seededTemplate(home.ID, "winter_chores", "Winter chores", "home", []string{"seasonal home check"}, now)
	r.templates["spring_chores"] = seededTemplate(home.ID, "spring_chores", "Spring chores", "home", []string{"seasonal home check"}, now)
	r.templates["leaving_house"] = seededTemplate(home.ID, "leaving_house", "Leaving house", "home", []string{
		"Lights off",
		"Small appliances unplugged",
		"Refrigerator / freezer doors shut",
		"Oven / stove off",
		"Doors locked",
		"Garage door closed",
		"Alarm set",
	}, now)
	return r
}

func seededTemplate(projectID, key, name, projectCoreKey string, itemNames []string, now func() time.Time) domain.Template {
	items := make([]domain.TemplateItem, 0, len(itemNames))
	for i, item := range itemNames {
		items = append(items, domain.TemplateItem{ID: uuid.NewString(), Name: item, Position: i, CreatedAt: now(), UpdatedAt: now()})
	}
	return domain.Template{ID: uuid.NewString(), ProjectID: projectID, TemplateKey: key, Name: name, ProjectCoreKey: projectCoreKey, IsSystem: true, CreatedAt: now(), UpdatedAt: now(), Items: items}
}

func (r *MemoryRepository) ListProjects(context.Context) ([]domain.Project, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := []domain.Project{}
	for _, p := range r.projects {
		if p.DeletedAt == nil {
			out = append(out, cloneProject(p))
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Name < out[j].Name })
	return out, nil
}

func (r *MemoryRepository) CreateProject(_ context.Context, p domain.Project) (domain.Project, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, existing := range r.projects {
		if existing.DeletedAt == nil && strings.EqualFold(existing.Name, p.Name) {
			return domain.Project{}, domain.Conflict("project name already exists")
		}
	}
	t := r.now()
	p.ID = uuid.NewString()
	p.Description = defaultString(p.Description)
	p.CreatedAt = t
	p.UpdatedAt = t
	r.projects[p.ID] = p
	return cloneProject(p), nil
}

func (r *MemoryRepository) GetProject(_ context.Context, id string) (domain.Project, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	p, ok := r.projects[id]
	if !ok || p.DeletedAt != nil {
		return domain.Project{}, domain.NotFound("project not found")
	}
	return cloneProject(p), nil
}

func (r *MemoryRepository) UpdateProject(_ context.Context, id string, patch domain.ProjectPatch) (domain.Project, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	p, ok := r.projects[id]
	if !ok || p.DeletedAt != nil {
		return domain.Project{}, domain.NotFound("project not found")
	}
	if patch.Name != nil {
		for _, existing := range r.projects {
			if existing.ID != id && existing.DeletedAt == nil && strings.EqualFold(existing.Name, *patch.Name) {
				return domain.Project{}, domain.Conflict("project name already exists")
			}
		}
		p.Name = *patch.Name
	}
	if patch.Description != nil {
		p.Description = *patch.Description
	}
	if patch.Tags != nil {
		p.Tags = append([]string(nil), (*patch.Tags)...)
	}
	p.UpdatedAt = r.now()
	r.projects[id] = p
	return cloneProject(p), nil
}

func (r *MemoryRepository) DeleteProject(_ context.Context, id string) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	p, ok := r.projects[id]
	if !ok || p.DeletedAt != nil {
		return domain.NotFound("project not found")
	}
	t := r.now()
	p.DeletedAt = &t
	p.UpdatedAt = t
	r.projects[id] = p
	for taskID, task := range r.tasks {
		if task.ProjectID == id && task.DeletedAt == nil {
			task.DeletedAt = &t
			task.UpdatedAt = t
			r.tasks[taskID] = task
			for subID, sub := range r.subtasks {
				if sub.TaskID == taskID && sub.DeletedAt == nil {
					sub.DeletedAt = &t
					sub.UpdatedAt = t
					r.subtasks[subID] = sub
				}
			}
		}
	}
	return nil
}

func (r *MemoryRepository) ListTasks(_ context.Context, projectID string) ([]domain.Task, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if p, ok := r.projects[projectID]; !ok || p.DeletedAt != nil {
		return nil, domain.NotFound("project not found")
	}
	out := []domain.Task{}
	for _, t := range r.tasks {
		if t.ProjectID == projectID && t.DeletedAt == nil {
			out = append(out, cloneTask(t))
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].CreatedAt.Before(out[j].CreatedAt) })
	return out, nil
}

func (r *MemoryRepository) CreateTask(_ context.Context, t domain.Task) (domain.Task, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if p, ok := r.projects[t.ProjectID]; !ok || p.DeletedAt != nil {
		return domain.Task{}, domain.NotFound("project not found")
	}
	n := r.now()
	t.ID = uuid.NewString()
	t.Description = defaultString(t.Description)
	if t.Status == "" {
		t.Status = domain.StatusTodo
	}
	t.CreatedAt = n
	t.UpdatedAt = n
	r.tasks[t.ID] = t
	return cloneTask(t), nil
}

func (r *MemoryRepository) GetTask(_ context.Context, id string) (domain.Task, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	t, ok := r.tasks[id]
	if !ok || t.DeletedAt != nil {
		return domain.Task{}, domain.NotFound("task not found")
	}
	return cloneTask(t), nil
}

func (r *MemoryRepository) UpdateTask(_ context.Context, id string, patch domain.TaskPatch) (domain.Task, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	t, ok := r.tasks[id]
	if !ok || t.DeletedAt != nil {
		return domain.Task{}, domain.NotFound("task not found")
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
	t.UpdatedAt = r.now()
	r.tasks[id] = t
	return cloneTask(t), nil
}

func (r *MemoryRepository) DeleteTask(_ context.Context, id string) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	t, ok := r.tasks[id]
	if !ok || t.DeletedAt != nil {
		return domain.NotFound("task not found")
	}
	n := r.now()
	t.DeletedAt = &n
	t.UpdatedAt = n
	r.tasks[id] = t
	for subID, sub := range r.subtasks {
		if sub.TaskID == id && sub.DeletedAt == nil {
			sub.DeletedAt = &n
			sub.UpdatedAt = n
			r.subtasks[subID] = sub
		}
	}
	return nil
}

func (r *MemoryRepository) FindTaskByProjectName(_ context.Context, projectID string, name string) (*domain.Task, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, t := range r.tasks {
		if t.ProjectID == projectID && t.DeletedAt == nil && t.Name == name {
			cloned := cloneTask(t)
			return &cloned, nil
		}
	}
	return nil, nil
}

func (r *MemoryRepository) CreateTaskWithSubtasks(_ context.Context, t domain.Task, items []domain.TemplateItem) (domain.Task, int, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if p, ok := r.projects[t.ProjectID]; !ok || p.DeletedAt != nil {
		return domain.Task{}, 0, domain.NotFound("project not found")
	}
	n := r.now()
	t.ID = uuid.NewString()
	t.Status = domain.StatusTodo
	t.CreatedAt = n
	t.UpdatedAt = n
	r.tasks[t.ID] = t
	for _, item := range items {
		sub := domain.SubTask{ID: uuid.NewString(), TaskID: t.ID, Name: item.Name, Description: item.Description, Status: domain.StatusTodo, CreatedAt: n, UpdatedAt: n}
		r.subtasks[sub.ID] = sub
	}
	return cloneTask(t), len(items), nil
}

func (r *MemoryRepository) ListSubTasks(_ context.Context, taskID string) ([]domain.SubTask, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if t, ok := r.tasks[taskID]; !ok || t.DeletedAt != nil {
		return nil, domain.NotFound("task not found")
	}
	out := []domain.SubTask{}
	for _, st := range r.subtasks {
		if st.TaskID == taskID && st.DeletedAt == nil {
			out = append(out, cloneSubTask(st))
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].CreatedAt.Before(out[j].CreatedAt) })
	return out, nil
}

func (r *MemoryRepository) CreateSubTask(_ context.Context, st domain.SubTask) (domain.SubTask, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if t, ok := r.tasks[st.TaskID]; !ok || t.DeletedAt != nil {
		return domain.SubTask{}, domain.NotFound("task not found")
	}
	n := r.now()
	st.ID = uuid.NewString()
	st.Description = defaultString(st.Description)
	if st.Status == "" {
		st.Status = domain.StatusTodo
	}
	st.CreatedAt = n
	st.UpdatedAt = n
	r.subtasks[st.ID] = st
	return cloneSubTask(st), nil
}

func (r *MemoryRepository) GetSubTask(_ context.Context, id string) (domain.SubTask, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	st, ok := r.subtasks[id]
	if !ok || st.DeletedAt != nil {
		return domain.SubTask{}, domain.NotFound("subtask not found")
	}
	return cloneSubTask(st), nil
}

func (r *MemoryRepository) UpdateSubTask(_ context.Context, id string, patch domain.SubTaskPatch) (domain.SubTask, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	st, ok := r.subtasks[id]
	if !ok || st.DeletedAt != nil {
		return domain.SubTask{}, domain.NotFound("subtask not found")
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
	st.UpdatedAt = r.now()
	r.subtasks[id] = st
	return cloneSubTask(st), nil
}

func (r *MemoryRepository) DeleteSubTask(_ context.Context, id string) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	st, ok := r.subtasks[id]
	if !ok || st.DeletedAt != nil {
		return domain.NotFound("subtask not found")
	}
	n := r.now()
	st.DeletedAt = &n
	st.UpdatedAt = n
	r.subtasks[id] = st
	return nil
}

func (r *MemoryRepository) ListTemplates(context.Context) ([]domain.Template, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := []domain.Template{}
	for _, tpl := range r.templates {
		out = append(out, cloneTemplate(tpl))
	}
	sort.Slice(out, func(i, j int) bool { return out[i].TemplateKey < out[j].TemplateKey })
	return out, nil
}

func (r *MemoryRepository) GetTemplate(_ context.Context, key string) (domain.Template, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	tpl, ok := r.templates[key]
	if !ok {
		return domain.Template{}, domain.NotFound("template not found")
	}
	return cloneTemplate(tpl), nil
}

func (r *MemoryRepository) ReplaceTemplateItems(_ context.Context, key string, items []domain.TemplateItem) (domain.Template, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	tpl, ok := r.templates[key]
	if !ok {
		return domain.Template{}, domain.NotFound("template not found")
	}
	n := r.now()
	tpl.Items = make([]domain.TemplateItem, 0, len(items))
	for i, item := range items {
		if item.Position == 0 && i != 0 {
			item.Position = i
		}
		item.ID = uuid.NewString()
		item.TemplateID = tpl.ID
		item.CreatedAt = n
		item.UpdatedAt = n
		tpl.Items = append(tpl.Items, item)
	}
	sort.Slice(tpl.Items, func(i, j int) bool { return tpl.Items[i].Position < tpl.Items[j].Position })
	tpl.UpdatedAt = n
	r.templates[key] = tpl
	return cloneTemplate(tpl), nil
}

func (r *MemoryRepository) ListProjectsForSync(_ context.Context, updatedSince *time.Time) ([]domain.Project, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := []domain.Project{}
	for _, p := range r.projects {
		if changedSince(p.UpdatedAt, p.DeletedAt, updatedSince) {
			out = append(out, cloneProject(p))
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Name < out[j].Name })
	return out, nil
}

func (r *MemoryRepository) ListTasksForSync(_ context.Context, updatedSince *time.Time) ([]domain.Task, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := []domain.Task{}
	for _, t := range r.tasks {
		if changedSince(t.UpdatedAt, t.DeletedAt, updatedSince) {
			out = append(out, cloneTask(t))
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].CreatedAt.Before(out[j].CreatedAt) })
	return out, nil
}

func (r *MemoryRepository) ListSubTasksForSync(_ context.Context, updatedSince *time.Time) ([]domain.SubTask, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := []domain.SubTask{}
	for _, st := range r.subtasks {
		if changedSince(st.UpdatedAt, st.DeletedAt, updatedSince) {
			out = append(out, cloneSubTask(st))
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].CreatedAt.Before(out[j].CreatedAt) })
	return out, nil
}

func (r *MemoryRepository) ListTemplatesForSync(_ context.Context, updatedSince *time.Time) ([]domain.Template, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := []domain.Template{}
	for _, tpl := range r.templates {
		if updatedSince == nil || tpl.UpdatedAt.After(*updatedSince) {
			out = append(out, cloneTemplate(tpl))
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].TemplateKey < out[j].TemplateKey })
	return out, nil
}

func defaultString(s string) string { return s }

func changedSince(updatedAt time.Time, deletedAt *time.Time, updatedSince *time.Time) bool {
	if updatedSince == nil {
		return deletedAt == nil
	}
	if updatedAt.After(*updatedSince) {
		return true
	}
	return deletedAt != nil && deletedAt.After(*updatedSince)
}

func cloneProject(p domain.Project) domain.Project {
	p.Tags = append([]string(nil), p.Tags...)
	return p
}

func cloneTask(t domain.Task) domain.Task { return t }

func cloneSubTask(st domain.SubTask) domain.SubTask { return st }

func cloneTemplate(t domain.Template) domain.Template {
	t.Items = append([]domain.TemplateItem(nil), t.Items...)
	return t
}
