package service

import (
	"context"
	"strings"
	"time"

	"ado/internal/domain"
)

type SubTaskService struct {
	repo Repository
	now  func() time.Time
}

func (s *SubTaskService) List(ctx context.Context, taskID string) ([]domain.SubTask, error) {
	if strings.TrimSpace(taskID) == "" {
		return nil, domain.Validation("task_id is required")
	}
	if _, err := s.repo.GetTask(ctx, taskID); err != nil {
		return nil, err
	}
	return s.repo.ListSubTasks(ctx, taskID)
}

func (s *SubTaskService) Create(ctx context.Context, st domain.SubTask) (domain.SubTask, error) {
	st.TaskID = strings.TrimSpace(st.TaskID)
	st.Name = strings.TrimSpace(st.Name)
	if st.TaskID == "" {
		return domain.SubTask{}, domain.Validation("task_id is required")
	}
	if st.Name == "" {
		return domain.SubTask{}, domain.Validation("name is required")
	}
	if st.Status == "" {
		st.Status = domain.StatusTodo
	}
	if !domain.ValidStatuses[st.Status] {
		return domain.SubTask{}, domain.Validation("invalid status")
	}
	if _, err := s.repo.GetTask(ctx, st.TaskID); err != nil {
		return domain.SubTask{}, err
	}
	return s.repo.CreateSubTask(ctx, st)
}

func (s *SubTaskService) Get(ctx context.Context, id string) (domain.SubTask, error) {
	return s.repo.GetSubTask(ctx, id)
}

func (s *SubTaskService) Update(ctx context.Context, id string, patch domain.SubTaskPatch) (domain.SubTask, error) {
	current, err := s.repo.GetSubTask(ctx, id)
	if err != nil {
		return domain.SubTask{}, err
	}
	if patch.TaskID != nil {
		taskID := strings.TrimSpace(*patch.TaskID)
		if taskID == "" {
			return domain.SubTask{}, domain.Validation("task_id is required")
		}
		if _, err := s.repo.GetTask(ctx, taskID); err != nil {
			return domain.SubTask{}, err
		}
		patch.TaskID = &taskID
	}
	if patch.Name != nil {
		name := strings.TrimSpace(*patch.Name)
		if name == "" {
			return domain.SubTask{}, domain.Validation("name is required")
		}
		patch.Name = &name
	}
	if patch.Status != nil {
		status := strings.TrimSpace(*patch.Status)
		if !domain.ValidStatuses[status] {
			return domain.SubTask{}, domain.Validation("invalid status")
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
	return s.repo.UpdateSubTask(ctx, id, patch)
}

func (s *SubTaskService) Delete(ctx context.Context, id string) error {
	if _, err := s.repo.GetSubTask(ctx, id); err != nil {
		return err
	}
	return s.repo.DeleteSubTask(ctx, id)
}
