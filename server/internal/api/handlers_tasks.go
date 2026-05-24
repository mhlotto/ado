package api

import (
	"net/http"

	"ado/internal/domain"
)

type taskRequest struct {
	ProjectID      *string `json:"project_id"`
	ProjectIDCamel *string `json:"projectId"`
	Name           *string `json:"name"`
	Description    *string `json:"description"`
	Status         *string `json:"status"`
}

func (r taskRequest) parentProjectID() *string {
	if r.ProjectID != nil {
		return r.ProjectID
	}
	return r.ProjectIDCamel
}

func (s *Server) routeProjectTasks(w http.ResponseWriter, r *http.Request, projectID string) {
	switch r.Method {
	case http.MethodGet:
		tasks, err := s.services.Tasks.List(r.Context(), projectID)
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, tasks)
	case http.MethodPost:
		var req taskRequest
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
		task, err := s.services.Tasks.Create(r.Context(), domain.Task{ProjectID: projectID, Name: name, Description: description})
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusCreated, task)
	default:
		methodNotAllowed(w)
	}
}

func (s *Server) routeTasks(w http.ResponseWriter, r *http.Request, parts []string) {
	if len(parts) == 2 {
		id := parts[1]
		switch r.Method {
		case http.MethodGet:
			task, err := s.services.Tasks.Get(r.Context(), id)
			if err != nil {
				writeError(w, err)
				return
			}
			writeJSON(w, http.StatusOK, task)
		case http.MethodPatch:
			var req taskRequest
			if err := decodeJSON(r, &req); err != nil {
				writeError(w, invalidJSON(r, err))
				return
			}
			task, err := s.services.Tasks.Update(r.Context(), id, domain.TaskPatch{ProjectID: req.parentProjectID(), Name: req.Name, Description: req.Description, Status: req.Status})
			if err != nil {
				writeError(w, err)
				return
			}
			writeJSON(w, http.StatusOK, task)
		case http.MethodDelete:
			if err := s.services.Tasks.Delete(r.Context(), id); err != nil {
				writeError(w, err)
				return
			}
			w.WriteHeader(http.StatusNoContent)
		default:
			methodNotAllowed(w)
		}
		return
	}
	if len(parts) == 3 && parts[2] == "subtasks" {
		s.routeTaskSubTasks(w, r, parts[1])
		return
	}
	writeError(w, domain.NotFound("route not found"))
}
