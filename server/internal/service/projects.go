package service

import (
	"context"
	"strings"

	"ado/internal/domain"
)

type ProjectService struct {
	repo Repository
}

type ProjectDetailOptions struct {
	IncludeTasks    bool
	IncludeSubTasks bool
}

func (s *ProjectService) List(ctx context.Context) ([]domain.Project, error) {
	return s.repo.ListProjects(ctx)
}

func (s *ProjectService) ListSummaries(ctx context.Context) ([]domain.ProjectSummary, error) {
	projects, err := s.repo.ListProjects(ctx)
	if err != nil {
		return nil, err
	}
	templates, err := s.repo.ListTemplates(ctx)
	if err != nil {
		return nil, err
	}
	actionsByCoreKey := templateActionsByCoreKey(templates)
	summaries := make([]domain.ProjectSummary, 0, len(projects))
	for _, project := range projects {
		tasks, err := s.repo.ListTasks(ctx, project.ID)
		if err != nil {
			return nil, err
		}
		summary := domain.ProjectSummary{
			Project:    project,
			TaskCounts: countTasks(tasks),
		}
		if project.CoreKey != nil {
			summary.TemplateActions = actionsByCoreKey[*project.CoreKey]
		}
		summaries = append(summaries, summary)
	}
	return summaries, nil
}

func (s *ProjectService) Create(ctx context.Context, p domain.Project) (domain.Project, error) {
	p.Name = strings.TrimSpace(p.Name)
	if p.Name == "" {
		return domain.Project{}, domain.Validation("name is required")
	}
	if p.Tags == nil {
		p.Tags = []string{}
	}
	return s.repo.CreateProject(ctx, p)
}

func (s *ProjectService) Get(ctx context.Context, id string) (domain.Project, error) {
	if strings.TrimSpace(id) == "" {
		return domain.Project{}, domain.Validation("project_id is required")
	}
	return s.repo.GetProject(ctx, id)
}

func (s *ProjectService) GetDetail(ctx context.Context, id string, opts ProjectDetailOptions) (domain.ProjectDetail, error) {
	project, err := s.Get(ctx, id)
	if err != nil {
		return domain.ProjectDetail{}, err
	}
	tasks, err := s.repo.ListTasks(ctx, id)
	if err != nil {
		return domain.ProjectDetail{}, err
	}

	detail := domain.ProjectDetail{
		Project:    project,
		TaskCounts: countTasks(tasks),
	}
	if project.CoreKey != nil {
		templates, err := s.repo.ListTemplates(ctx)
		if err != nil {
			return domain.ProjectDetail{}, err
		}
		detail.TemplateActions = templateActionsByCoreKey(templates)[*project.CoreKey]
	}
	if !opts.IncludeTasks && opts.IncludeSubTasks {
		opts.IncludeTasks = true
	}
	if opts.IncludeTasks {
		detail.Tasks = make([]domain.TaskWithSubTasks, 0, len(tasks))
		for _, task := range tasks {
			withSubTasks := domain.TaskWithSubTasks{Task: task}
			if opts.IncludeSubTasks {
				subtasks, err := s.repo.ListSubTasks(ctx, task.ID)
				if err != nil {
					return domain.ProjectDetail{}, err
				}
				withSubTasks.SubTasks = subtasks
			}
			detail.Tasks = append(detail.Tasks, withSubTasks)
		}
	}
	return detail, nil
}

func templateActionsByCoreKey(templates []domain.Template) map[string][]domain.TemplateAction {
	out := map[string][]domain.TemplateAction{}
	for _, template := range templates {
		if template.ProjectCoreKey == "" {
			continue
		}
		out[template.ProjectCoreKey] = append(out[template.ProjectCoreKey], domain.TemplateAction{
			TemplateKey:      template.TemplateKey,
			Name:             template.Name,
			GenerateEndpoint: "/api/v1/templates/" + template.TemplateKey + "/generate",
		})
	}
	return out
}

func (s *ProjectService) Update(ctx context.Context, id string, patch domain.ProjectPatch) (domain.Project, error) {
	p, err := s.repo.GetProject(ctx, id)
	if err != nil {
		return domain.Project{}, err
	}
	if patch.Name != nil {
		name := strings.TrimSpace(*patch.Name)
		if name == "" {
			return domain.Project{}, domain.Validation("name is required")
		}
		if p.IsCore && name != p.Name {
			return domain.Project{}, domain.Conflict("core projects cannot be renamed")
		}
		patch.Name = &name
	}
	return s.repo.UpdateProject(ctx, id, patch)
}

func (s *ProjectService) Delete(ctx context.Context, id string) error {
	p, err := s.repo.GetProject(ctx, id)
	if err != nil {
		return err
	}
	if p.IsCore {
		return domain.Conflict("core projects cannot be deleted")
	}
	return s.repo.DeleteProject(ctx, id)
}

func countTasks(tasks []domain.Task) domain.TaskCounts {
	counts := domain.TaskCounts{Total: len(tasks)}
	for _, task := range tasks {
		switch task.Status {
		case domain.StatusTodo:
			counts.Todo++
			counts.Open++
		case domain.StatusInProgress:
			counts.InProgress++
			counts.Open++
		case domain.StatusDone:
			counts.Done++
		case domain.StatusArchived:
			counts.Archived++
		}
	}
	return counts
}
