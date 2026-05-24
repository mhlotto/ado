package api

import (
	"net/http"

	"ado/internal/domain"
	"ado/internal/service"
)

type templateUpdateRequest struct {
	Items []domain.TemplateItem `json:"items"`
}

func (s *Server) routeTemplates(w http.ResponseWriter, r *http.Request, parts []string) {
	if len(parts) == 1 {
		if r.Method != http.MethodGet {
			methodNotAllowed(w)
			return
		}
		templates, err := s.services.Templates.List(r.Context())
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, templates)
		return
	}

	if len(parts) == 2 {
		key := parts[1]
		switch r.Method {
		case http.MethodGet:
			template, err := s.services.Templates.Get(r.Context(), key)
			if err != nil {
				writeError(w, err)
				return
			}
			writeJSON(w, http.StatusOK, template)
		case http.MethodPatch:
			var req templateUpdateRequest
			if err := decodeJSON(r, &req); err != nil {
				writeError(w, invalidJSON(r, err))
				return
			}
			template, err := s.services.Templates.Update(r.Context(), key, req.Items)
			if err != nil {
				writeError(w, err)
				return
			}
			writeJSON(w, http.StatusOK, template)
		default:
			methodNotAllowed(w)
		}
		return
	}

	if len(parts) == 3 && parts[2] == "generate" {
		if r.Method != http.MethodPost {
			methodNotAllowed(w)
			return
		}
		var req service.GenerateRequest
		if err := decodeJSON(r, &req); err != nil {
			writeError(w, invalidJSON(r, err))
			return
		}
		generated, err := s.services.Templates.Generate(r.Context(), parts[1], req)
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusCreated, generated)
		return
	}

	writeError(w, domain.NotFound("route not found"))
}
