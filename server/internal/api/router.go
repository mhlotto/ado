package api

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"os"
	"strings"

	"ado/internal/domain"
	"ado/internal/service"
)

type Server struct {
	services *service.Services
}

func NewRouter(services *service.Services) http.Handler {
	return withCORS(&Server{services: services})
}

func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.URL.Path == "/healthz" {
		if r.Method != http.MethodGet {
			writeError(w, domain.Validation("method not allowed"))
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
		return
	}

	if !strings.HasPrefix(r.URL.Path, "/api/v1") {
		writeError(w, domain.NotFound("route not found"))
		return
	}
	parts := splitPath(strings.TrimPrefix(r.URL.Path, "/api/v1"))
	if len(parts) == 0 {
		writeJSON(w, http.StatusOK, map[string]string{"name": "ado api", "version": "v1"})
		return
	}

	switch parts[0] {
	case "projects":
		s.routeProjects(w, r, parts)
	case "tasks":
		s.routeTasks(w, r, parts)
	case "subtasks":
		s.routeSubTasks(w, r, parts)
	case "templates":
		s.routeTemplates(w, r, parts)
	case "sync":
		s.routeSync(w, r, parts)
	default:
		writeError(w, domain.NotFound("route not found"))
	}
}

func splitPath(path string) []string {
	path = strings.Trim(path, "/")
	if path == "" {
		return nil
	}
	return strings.Split(path, "/")
}

func decodeJSON(r *http.Request, v any) error {
	defer r.Body.Close()
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	return dec.Decode(v)
}

func invalidJSON(r *http.Request, err error) error {
	log.Printf("invalid JSON request: method=%s path=%s error=%v", r.Method, r.URL.Path, err)
	return domain.Validation("invalid JSON request: " + err.Error())
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, err error) {
	var appErr *domain.Error
	if errors.As(err, &appErr) {
		writeJSON(w, appErr.HTTPStatus, map[string]any{
			"error": map[string]string{"code": appErr.Code, "message": appErr.Message},
		})
		return
	}
	writeJSON(w, http.StatusInternalServerError, map[string]any{
		"error": map[string]string{"code": domain.CodeInternal, "message": "internal error"},
	})
}

func methodNotAllowed(w http.ResponseWriter) {
	writeJSON(w, http.StatusMethodNotAllowed, map[string]any{
		"error": map[string]string{"code": "method_not_allowed", "message": "method not allowed"},
	})
}

func withCORS(next http.Handler) http.Handler {
	allowedOrigins := map[string]struct{}{
		"http://localhost:5173": {},
		"http://127.0.0.1:5173": {},
		"null":                  {},
	}
	if extra := os.Getenv("ADO_CORS_ORIGINS"); extra != "" {
		for _, origin := range strings.Split(extra, ",") {
			origin = strings.TrimSpace(origin)
			if origin != "" {
				allowedOrigins[origin] = struct{}{}
			}
		}
	}

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")
		if _, ok := allowedOrigins[origin]; ok {
			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Vary", "Origin")
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
			w.Header().Set("Access-Control-Allow-Private-Network", "true")
		}
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}
