package api

import (
	"net/http"

	"ado/internal/domain"
)

type subTaskRequest struct {
	TaskID      *string `json:"task_id"`
	TaskIDCamel *string `json:"taskId"`
	Name        *string `json:"name"`
	Description *string `json:"description"`
	Status      *string `json:"status"`
}

func (r subTaskRequest) parentTaskID() *string {
	if r.TaskID != nil {
		return r.TaskID
	}
	return r.TaskIDCamel
}

func (s *Server) routeTaskSubTasks(w http.ResponseWriter, r *http.Request, taskID string) {
	switch r.Method {
	case http.MethodGet:
		subtasks, err := s.services.SubTasks.List(r.Context(), taskID)
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, subtasks)
	case http.MethodPost:
		var req subTaskRequest
		if err := decodeJSON(r, &req); err != nil {
			writeError(w, invalidJSON(r, err))
			return
		}
		name := ""
		if req.Name != nil {
			name = *req.Name
		}
		description := ""
		if req.Description != nil {
			description = *req.Description
		}
		subtask, err := s.services.SubTasks.Create(r.Context(), domain.SubTask{TaskID: taskID, Name: name, Description: description})
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusCreated, subtask)
	default:
		methodNotAllowed(w)
	}
}

func (s *Server) routeSubTasks(w http.ResponseWriter, r *http.Request, parts []string) {
	if len(parts) != 2 {
		writeError(w, domain.NotFound("route not found"))
		return
	}
	id := parts[1]
	switch r.Method {
	case http.MethodGet:
		subtask, err := s.services.SubTasks.Get(r.Context(), id)
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, subtask)
	case http.MethodPatch:
		var req subTaskRequest
		if err := decodeJSON(r, &req); err != nil {
			writeError(w, invalidJSON(r, err))
			return
		}
		subtask, err := s.services.SubTasks.Update(r.Context(), id, domain.SubTaskPatch{TaskID: req.parentTaskID(), Name: req.Name, Description: req.Description, Status: req.Status})
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, subtask)
	case http.MethodDelete:
		if err := s.services.SubTasks.Delete(r.Context(), id); err != nil {
			writeError(w, err)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	default:
		methodNotAllowed(w)
	}
}
