package domain

import "time"

const (
	StatusTodo       = "todo"
	StatusInProgress = "in_progress"
	StatusDone       = "done"
	StatusArchived   = "archived"
)

var ValidStatuses = map[string]bool{
	StatusTodo:       true,
	StatusInProgress: true,
	StatusDone:       true,
	StatusArchived:   true,
}

type Task struct {
	ID          string     `json:"id"`
	ProjectID   string     `json:"project_id"`
	Name        string     `json:"name"`
	Description string     `json:"description"`
	Status      string     `json:"status"`
	CreatedAt   time.Time  `json:"created_at"`
	FinishedAt  *time.Time `json:"finished_at,omitempty"`
	UpdatedAt   time.Time  `json:"updated_at"`
	DeletedAt   *time.Time `json:"deleted_at,omitempty"`
}

type TaskPatch struct {
	ProjectID       *string
	Name            *string
	Description     *string
	Status          *string
	FinishedAt      *time.Time
	ClearFinishedAt bool
}

type GeneratedTask struct {
	TaskID          string `json:"task_id"`
	ProjectID       string `json:"project_id"`
	Name            string `json:"name"`
	SubtasksCreated int    `json:"subtasks_created"`
}
