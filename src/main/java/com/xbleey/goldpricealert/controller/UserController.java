package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.model.AppUser;
import com.xbleey.goldpricealert.service.AppUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/users")
public class UserController {

    private final AppUserService userService;

    public UserController(AppUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Map<String, Object> listUsers() {
        List<UserResponse> records = userService.listUsers().stream()
                .map(this::toResponse)
                .toList();
        return Map.of(
                "total", records.size(),
                "records", records
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable("id") Long id) {
        try {
            UserResponse record = toResponse(userService.getById(id));
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "record", record
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (NoSuchElementException ex) {
            return notFound(ex.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestBody(required = false) UserUpsertRequest request
    ) {
        if (request == null) {
            return badRequest("request body must not be null");
        }
        try {
            AppUser created = userService.create(
                    request.username(),
                    request.password(),
                    request.role(),
                    request.enabled()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "created",
                    "record", toResponse(created)
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable("id") Long id,
            @RequestBody(required = false) UserUpsertRequest request
    ) {
        if (request == null) {
            return badRequest("request body must not be null");
        }
        try {
            AppUser updated = userService.update(
                    id,
                    request.username(),
                    request.password(),
                    request.role(),
                    request.enabled()
            );
            return ResponseEntity.ok(Map.of(
                    "status", "updated",
                    "record", toResponse(updated)
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (NoSuchElementException ex) {
            return notFound(ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable("id") Long id) {
        try {
            boolean deleted = userService.delete(id);
            if (!deleted) {
                return notFound("user not found: id=" + id);
            }
            return ResponseEntity.ok(Map.of(
                    "status", "deleted",
                    "id", id
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                user.getEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return response(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    private ResponseEntity<Map<String, Object>> notFound(String message) {
        return response(HttpStatus.NOT_FOUND, "not_found", message);
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    public record UserUpsertRequest(
            String username,
            String password,
            String role,
            Boolean enabled
    ) {
    }

    public record UserResponse(
            Long id,
            String username,
            String role,
            Boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
