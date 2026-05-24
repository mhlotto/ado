package service

import (
	"context"
	"time"

	"ado/internal/domain"
)

type SyncService struct {
	repo Repository
	now  func() time.Time
}

func (s *SyncService) Snapshot(ctx context.Context, updatedSince *time.Time) (domain.SyncSnapshot, error) {
	projects, err := s.repo.ListProjectsForSync(ctx, updatedSince)
	if err != nil {
		return domain.SyncSnapshot{}, err
	}
	tasks, err := s.repo.ListTasksForSync(ctx, updatedSince)
	if err != nil {
		return domain.SyncSnapshot{}, err
	}
	subtasks, err := s.repo.ListSubTasksForSync(ctx, updatedSince)
	if err != nil {
		return domain.SyncSnapshot{}, err
	}
	templates, err := s.repo.ListTemplatesForSync(ctx, updatedSince)
	if err != nil {
		return domain.SyncSnapshot{}, err
	}
	return domain.SyncSnapshot{
		ServerTime: s.now(),
		Projects:   projects,
		Tasks:      tasks,
		SubTasks:   subtasks,
		Templates:  templates,
	}, nil
}
