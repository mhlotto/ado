package domain

import "time"

type Template struct {
	ID             string         `json:"id,omitempty"`
	TemplateKey    string         `json:"template_key"`
	Name           string         `json:"name"`
	Description    string         `json:"description"`
	ProjectID      string         `json:"project_id,omitempty"`
	ProjectCoreKey string         `json:"project_core_key"`
	IsSystem       bool           `json:"is_system"`
	CreatedAt      time.Time      `json:"created_at,omitempty"`
	UpdatedAt      time.Time      `json:"updated_at,omitempty"`
	Items          []TemplateItem `json:"items"`
}

type TemplateItem struct {
	ID          string    `json:"id,omitempty"`
	TemplateID  string    `json:"template_id,omitempty"`
	Name        string    `json:"name"`
	Description string    `json:"description"`
	Position    int       `json:"position"`
	CreatedAt   time.Time `json:"created_at,omitempty"`
	UpdatedAt   time.Time `json:"updated_at,omitempty"`
}
