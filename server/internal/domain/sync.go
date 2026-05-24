package domain

import "time"

type SyncSnapshot struct {
	ServerTime time.Time  `json:"server_time"`
	Projects   []Project  `json:"projects"`
	Tasks      []Task     `json:"tasks"`
	SubTasks   []SubTask  `json:"subtasks"`
	Templates  []Template `json:"templates"`
}
