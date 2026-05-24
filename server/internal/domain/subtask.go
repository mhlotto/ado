package domain

import "time"

type SubTask struct {
	ID          string     `json:"id"`
	TaskID      string     `json:"task_id"`
	Name        string     `json:"name"`
	Description string     `json:"description"`
	Status      string     `json:"status"`
	CreatedAt   time.Time  `json:"created_at"`
	FinishedAt  *time.Time `json:"finished_at,omitempty"`
	UpdatedAt   time.Time  `json:"updated_at"`
	DeletedAt   *time.Time `json:"deleted_at,omitempty"`
}

type SubTaskPatch struct {
	TaskID          *string
	Name            *string
	Description     *string
	Status          *string
	FinishedAt      *time.Time
	ClearFinishedAt bool
}
