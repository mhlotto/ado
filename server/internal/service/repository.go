package service

import (
	"context"
	"time"

	"ado/internal/domain"
)

type Repository interface {
	ListProjects(context.Context) ([]domain.Project, error)
	CreateProject(context.Context, domain.Project) (domain.Project, error)
	GetProject(context.Context, string) (domain.Project, error)
	UpdateProject(context.Context, string, domain.ProjectPatch) (domain.Project, error)
	DeleteProject(context.Context, string) error

	ListTasks(context.Context, string) ([]domain.Task, error)
	CreateTask(context.Context, domain.Task) (domain.Task, error)
	GetTask(context.Context, string) (domain.Task, error)
	UpdateTask(context.Context, string, domain.TaskPatch) (domain.Task, error)
	DeleteTask(context.Context, string) error
	FindTaskByProjectName(context.Context, string, string) (*domain.Task, error)
	CreateTaskWithSubtasks(context.Context, domain.Task, []domain.TemplateItem) (domain.Task, int, error)

	ListSubTasks(context.Context, string) ([]domain.SubTask, error)
	CreateSubTask(context.Context, domain.SubTask) (domain.SubTask, error)
	GetSubTask(context.Context, string) (domain.SubTask, error)
	UpdateSubTask(context.Context, string, domain.SubTaskPatch) (domain.SubTask, error)
	DeleteSubTask(context.Context, string) error

	ListTemplates(context.Context) ([]domain.Template, error)
	GetTemplate(context.Context, string) (domain.Template, error)
	ReplaceTemplateItems(context.Context, string, []domain.TemplateItem) (domain.Template, error)

	ListProjectsForSync(context.Context, *time.Time) ([]domain.Project, error)
	ListTasksForSync(context.Context, *time.Time) ([]domain.Task, error)
	ListSubTasksForSync(context.Context, *time.Time) ([]domain.SubTask, error)
	ListTemplatesForSync(context.Context, *time.Time) ([]domain.Template, error)
}

type Services struct {
	Projects  *ProjectService
	Tasks     *TaskService
	SubTasks  *SubTaskService
	Templates *TemplateService
	Sync      *SyncService
	Now       func() time.Time
}

func New(repo Repository) *Services {
	now := func() time.Time { return time.Now().UTC() }
	s := &Services{Now: now}
	s.Projects = &ProjectService{repo: repo}
	s.Tasks = &TaskService{repo: repo, now: now}
	s.SubTasks = &SubTaskService{repo: repo, now: now}
	s.Templates = &TemplateService{repo: repo, now: now}
	s.Sync = &SyncService{repo: repo, now: now}
	return s
}
