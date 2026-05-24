package api

import (
	"net/http"
	"time"

	"ado/internal/domain"
)

func (s *Server) routeSync(w http.ResponseWriter, r *http.Request, parts []string) {
	if len(parts) == 2 && parts[1] == "status" && r.Method == http.MethodGet {
		writeJSON(w, http.StatusOK, map[string]string{"status": "server_canonical"})
		return
	}
	if len(parts) == 2 && parts[1] == "snapshot" && r.Method == http.MethodGet {
		updatedSince, err := parseUpdatedSince(r)
		if err != nil {
			writeError(w, err)
			return
		}
		snapshot, err := s.services.Sync.Snapshot(r.Context(), updatedSince)
		if err != nil {
			writeError(w, err)
			return
		}
		writeJSON(w, http.StatusOK, snapshot)
		return
	}
	writeError(w, domain.NotFound("route not found"))
}

func parseUpdatedSince(r *http.Request) (*time.Time, error) {
	raw := r.URL.Query().Get("updated_since")
	if raw == "" {
		return nil, nil
	}
	t, err := time.Parse(time.RFC3339, raw)
	if err != nil {
		return nil, domain.Validation("updated_since must be an RFC3339 timestamp")
	}
	return &t, nil
}
