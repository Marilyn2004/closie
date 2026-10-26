package com.fashionsearch.controller;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<Map<String, String>> badRequest(Exception ex) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message",
                ex instanceof IllegalArgumentException && ex.getMessage() != null
                    ? ex.getMessage()
                    : "Please check the request fields."));
  }

  @ExceptionHandler(com.fashionsearch.service.ImportBusyException.class)
  ResponseEntity<Map<String, String>> busy() {
    return ResponseEntity.status(409)
        .body(Map.of("message", "A catalog refresh is already running. Please wait."));
  }
}
