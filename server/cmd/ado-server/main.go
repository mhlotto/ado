package main

import (
	"context"
	"flag"
	"log"
	"net/http"
	"os"
	"time"

	"ado/internal/api"
	"ado/internal/db"
	"ado/internal/service"
)

func main() {
	var migrateOnly bool
	flag.BoolVar(&migrateOnly, "migrate-only", false, "run database migrations and exit")
	flag.Parse()

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	databaseURL := env("DATABASE_URL", "postgres://ado:ado@localhost:5432/ado?sslmode=disable")
	conn, err := db.Open(ctx, databaseURL)
	if err != nil {
		log.Fatalf("open database: %v", err)
	}
	defer conn.Close()

	migrationsDir := env("ADO_MIGRATIONS_DIR", "migrations")
	if err := db.RunMigrations(ctx, conn, migrationsDir); err != nil {
		log.Fatalf("run migrations: %v", err)
	}
	if migrateOnly {
		log.Println("migrations complete")
		return
	}

	repo := db.NewStore(conn)
	services := service.New(repo)
	router := api.NewRouter(services)
	addr := ":" + env("PORT", "8989")

	server := &http.Server{
		Addr:              addr,
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
	}
	log.Printf("ado server listening on %s", addr)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("listen: %v", err)
	}
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
