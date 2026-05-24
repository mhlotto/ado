package domain

import "fmt"

const (
	CodeValidation = "validation_error"
	CodeNotFound   = "not_found"
	CodeConflict   = "conflict"
	CodeInternal   = "internal_error"
)

type Error struct {
	Code       string
	Message    string
	HTTPStatus int
}

func (e *Error) Error() string {
	return fmt.Sprintf("%s: %s", e.Code, e.Message)
}

func Validation(message string) *Error {
	return &Error{Code: CodeValidation, Message: message, HTTPStatus: 400}
}

func NotFound(message string) *Error {
	return &Error{Code: CodeNotFound, Message: message, HTTPStatus: 404}
}

func Conflict(message string) *Error {
	return &Error{Code: CodeConflict, Message: message, HTTPStatus: 409}
}
