package domain

import "time"

type Project struct {
	ID          string     `json:"id"`
	Name        string     `json:"name"`
	Description string     `json:"description"`
	Tags        []string   `json:"tags"`
	IsCore      bool       `json:"is_core"`
	CoreKey     *string    `json:"core_key,omitempty"`
	CreatedAt   time.Time  `json:"created_at"`
	UpdatedAt   time.Time  `json:"updated_at"`
	DeletedAt   *time.Time `json:"deleted_at,omitempty"`
}

type ProjectPatch struct {
	Name        *string
	Description *string
	Tags        *[]string
}

type ProjectSummary struct {
	Project
	TaskCounts      TaskCounts       `json:"task_counts"`
	TemplateActions []TemplateAction `json:"template_actions,omitempty"`
}

type ProjectDetail struct {
	Project
	TaskCounts      TaskCounts         `json:"task_counts"`
	TemplateActions []TemplateAction   `json:"template_actions,omitempty"`
	Tasks           []TaskWithSubTasks `json:"tasks,omitempty"`
}

type TaskCounts struct {
	Total      int `json:"total"`
	Open       int `json:"open"`
	Todo       int `json:"todo"`
	InProgress int `json:"in_progress"`
	Done       int `json:"done"`
	Archived   int `json:"archived"`
}

type TaskWithSubTasks struct {
	Task
	SubTasks []SubTask `json:"subtasks,omitempty"`
}

type TemplateAction struct {
	TemplateKey      string `json:"template_key"`
	Name             string `json:"name"`
	GenerateEndpoint string `json:"generate_endpoint"`
}
