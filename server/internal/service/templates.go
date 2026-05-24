package service

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"time"

	"ado/internal/domain"
)

type TemplateService struct {
	repo Repository
	now  func() time.Time
}

type GenerateRequest struct {
	Date string `json:"date"`
	Year int    `json:"year"`
}

func (s *TemplateService) List(ctx context.Context) ([]domain.Template, error) {
	return s.repo.ListTemplates(ctx)
}

func (s *TemplateService) Get(ctx context.Context, key string) (domain.Template, error) {
	return s.repo.GetTemplate(ctx, key)
}

func (s *TemplateService) Update(ctx context.Context, key string, items []domain.TemplateItem) (domain.Template, error) {
	for i := range items {
		items[i].Name = strings.TrimSpace(items[i].Name)
		if items[i].Name == "" {
			return domain.Template{}, domain.Validation("item name is required")
		}
	}
	return s.repo.ReplaceTemplateItems(ctx, key, items)
}

func (s *TemplateService) Generate(ctx context.Context, key string, req GenerateRequest) (domain.GeneratedTask, error) {
	tpl, err := s.repo.GetTemplate(ctx, key)
	if err != nil {
		return domain.GeneratedTask{}, err
	}
	name, duplicateDaily, err := s.generatedName(key, req)
	if err != nil {
		return domain.GeneratedTask{}, err
	}
	if duplicateDaily {
		for _, candidate := range s.dailyDuplicateNames(req, name) {
			existing, err := s.repo.FindTaskByProjectName(ctx, tpl.ProjectID, candidate)
			if err != nil {
				return domain.GeneratedTask{}, err
			}
			if existing != nil {
				return domain.GeneratedTask{}, domain.Conflict("daily task already exists: " + existing.ID)
			}
		}
	}
	task, count, err := s.repo.CreateTaskWithSubtasks(ctx, domain.Task{
		ProjectID: tpl.ProjectID,
		Name:      name,
		Status:    domain.StatusTodo,
	}, tpl.Items)
	if err != nil {
		return domain.GeneratedTask{}, err
	}
	return domain.GeneratedTask{
		TaskID:          task.ID,
		ProjectID:       task.ProjectID,
		Name:            task.Name,
		SubtasksCreated: count,
	}, nil
}

func (s *TemplateService) generatedName(key string, req GenerateRequest) (string, bool, error) {
	switch key {
	case "daily":
		d, err := s.resolveDate(req.Date)
		if err != nil {
			return "", false, err
		}
		return dailyTaskName(d), true, nil
	case "summer_chores":
		return fmt.Sprintf("Summer chores %d", s.resolveYear(req.Year)), false, nil
	case "fall_chores":
		return fmt.Sprintf("Fall chores %d", s.resolveYear(req.Year)), false, nil
	case "winter_chores":
		return fmt.Sprintf("Winter chores %d", s.resolveYear(req.Year)), false, nil
	case "spring_chores":
		return fmt.Sprintf("Spring chores %d", s.resolveYear(req.Year)), false, nil
	case "leaving_house":
		return "Leaving house", false, nil
	default:
		return "", false, domain.Validation("unsupported template")
	}
}

func (s *TemplateService) dailyDuplicateNames(req GenerateRequest, generatedName string) []string {
	names := []string{generatedName}
	d, err := s.resolveDate(req.Date)
	if err != nil {
		return names
	}
	legacyName := d.Format("2006-01-02")
	if legacyName != generatedName {
		names = append(names, legacyName)
	}
	return names
}

func (s *TemplateService) resolveDate(raw string) (time.Time, error) {
	base := s.now()
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "", "today":
		return dateOnly(base), nil
	case "tomorrow":
		return dateOnly(base.AddDate(0, 0, 1)), nil
	case "yesterday":
		return dateOnly(base.AddDate(0, 0, -1)), nil
	default:
		d, err := time.Parse("2006-01-02", raw)
		if err != nil {
			return time.Time{}, domain.Validation("date must be today, tomorrow, yesterday, or YYYY-MM-DD")
		}
		return d, nil
	}
}

func (s *TemplateService) resolveYear(year int) int {
	if year > 0 {
		return year
	}
	y, _ := strconv.Atoi(s.now().Format("2006"))
	return y
}

func dateOnly(t time.Time) time.Time {
	y, m, d := t.Date()
	return time.Date(y, m, d, 0, 0, 0, 0, time.UTC)
}

func dailyTaskName(t time.Time) string {
	return fmt.Sprintf("%s %s", t.Format("2006-01-02"), t.Weekday().String())
}
