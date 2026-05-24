package api

import (
	"net/http"
	"strings"

	"ado/internal/domain"
	"ado/internal/service"
)

type projectRequest struct {
	Name        *string  `json:"name"`
	Description *string  `json:"description"`
	Tags        []string `json:"tags"`
}

func (s *Server) routeProjects(w http.ResponseWriter, r *http.Request, parts []string) {
	if len(parts) == 1 {
		switch r.Method {
		case http.MethodGet:
			projects, err := s.services.Projects.ListSummaries(r.Context())
			if err != nil {
				writeError(w, err)
				return
			}
			writeJSON(w, http.StatusOK, projects)
		case http.MethodPost:
			var req projectRequest
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
			p, err := s.services.Projects.Create(r.Context(), domain.Project{Name: name, Description: description, Tags: req.Tags})
			if err != nil {
				writeError(w, err)
				return
			}
			writeJSON(w, http.StatusCreated, p)
		default:
			methodNotAllowed(w)
		}
		return
	}

	if len(parts) == 2 {
		id := parts[1]
		switch r.Method {
		case http.MethodGet:
			opts, err := parseProjectDetailOptions(r)
			if err != nil {
				writeError(w, err)
				return
			}
			p, err := s.services.Projects.GetDetail(r.Context(), id, opts)
			if err != nil {
				writeError(w, err)
				return
			}
			writeJSON(w, http.StatusOK, p)
		case http.MethodPatch:
			var req projectRequest
			if err := decodeJSON(r, &req); err != nil {
				writeError(w, invalidJSON(r, err))
				return
			}
			var tags *[]string
			if req.Tags != nil {
				tags = &req.Tags
			}
			p, err := s.services.Projects.Update(r.Context(), id, domain.ProjectPatch{Name: req.Name, Description: req.Description, Tags: tags})
			if err != nil {
				writeError(w, err)
				return
			}
			writeJSON(w, http.StatusOK, p)
		case http.MethodDelete:
			if err := s.services.Projects.Delete(r.Context(), id); err != nil {
				writeError(w, err)
				return
			}
			w.WriteHeader(http.StatusNoContent)
		default:
			methodNotAllowed(w)
		}
		return
	}

	if len(parts) == 3 && parts[2] == "tasks" {
		s.routeProjectTasks(w, r, parts[1])
		return
	}

	writeError(w, domain.NotFound("route not found"))
}

func parseProjectDetailOptions(r *http.Request) (service.ProjectDetailOptions, error) {
	var opts service.ProjectDetailOptions
	raw := strings.TrimSpace(r.URL.Query().Get("include"))
	if raw == "" {
		return opts, nil
	}
	for _, part := range strings.Split(raw, ",") {
		switch strings.TrimSpace(part) {
		case "":
			continue
		case "tasks":
			opts.IncludeTasks = true
		case "subtasks":
			opts.IncludeSubTasks = true
		default:
			return opts, domain.Validation("include must contain only tasks or subtasks")
		}
	}
	return opts, nil
}
