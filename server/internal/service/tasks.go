package service

import (
	"context"
	"strings"
	"time"

	"ado/internal/domain"
)

type TaskService struct {
	repo Repository
	now  func() time.Time
}

func (s *TaskService) List(ctx context.Context, projectID string) ([]domain.Task, error) {
	if strings.TrimSpace(projectID) == "" {
		return nil, domain.Validation("project_id is required")
	}
	if _, err := s.repo.GetProject(ctx, projectID); err != nil {
		return nil, err
	}
	return s.repo.ListTasks(ctx, projectID)
}

func (s *TaskService) Create(ctx context.Context, t domain.Task) (domain.Task, error) {
	t.ProjectID = strings.TrimSpace(t.ProjectID)
	t.Name = strings.TrimSpace(t.Name)
	if t.ProjectID == "" {
		return domain.Task{}, domain.Validation("project_id is required")
	}
	if t.Name == "" {
		return domain.Task{}, domain.Validation("name is required")
	}
	if t.Status == "" {
		t.Status = domain.StatusTodo
	}
	if !domain.ValidStatuses[t.Status] {
		return domain.Task{}, domain.Validation("invalid status")
	}
	if _, err := s.repo.GetProject(ctx, t.ProjectID); err != nil {
		return domain.Task{}, err
	}
	return s.repo.CreateTask(ctx, t)
}

func (s *TaskService) Get(ctx context.Context, id string) (domain.Task, error) {
	return s.repo.GetTask(ctx, id)
}

func (s *TaskService) Update(ctx context.Context, id string, patch domain.TaskPatch) (domain.Task, error) {
	current, err := s.repo.GetTask(ctx, id)
	if err != nil {
		return domain.Task{}, err
	}
	if patch.ProjectID != nil {
		projectID := strings.TrimSpace(*patch.ProjectID)
		if projectID == "" {
			return domain.Task{}, domain.Validation("project_id is required")
		}
		if _, err := s.repo.GetProject(ctx, projectID); err != nil {
			return domain.Task{}, err
		}
		patch.ProjectID = &projectID
	}
	if patch.Name != nil {
		name := strings.TrimSpace(*patch.Name)
		if name == "" {
			return domain.Task{}, domain.Validation("name is required")
		}
		patch.Name = &name
	}
	if patch.Status != nil {
		status := strings.TrimSpace(*patch.Status)
		if !domain.ValidStatuses[status] {
			return domain.Task{}, domain.Validation("invalid status")
		}
		patch.Status = &status
		if status == domain.StatusDone && current.Status != domain.StatusDone {
			t := s.now()
			patch.FinishedAt = &t
		}
		if status != domain.StatusDone {
			patch.ClearFinishedAt = true
		}
	}
	return s.repo.UpdateTask(ctx, id, patch)
}

func (s *TaskService) Delete(ctx context.Context, id string) error {
	if _, err := s.repo.GetTask(ctx, id); err != nil {
		return err
	}
	return s.repo.DeleteTask(ctx, id)
}
