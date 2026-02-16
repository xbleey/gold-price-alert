package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.model.GoldMailRecipient;
import com.xbleey.goldpricealert.service.GoldMailRecipientService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/mail/recipients")
public class MailRecipientController {

    private final GoldMailRecipientService recipientService;

    public MailRecipientController(GoldMailRecipientService recipientService) {
        this.recipientService = recipientService;
    }

    @GetMapping
    public Map<String, Object> listRecipients() {
        List<GoldMailRecipient> records = recipientService.listRecipients();
        return Map.of(
                "total", records.size(),
                "records", records
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getRecipient(@PathVariable("id") Long id) {
        try {
            GoldMailRecipient record = recipientService.getById(id);
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
    public ResponseEntity<Map<String, Object>> createRecipient(
            @RequestBody(required = false) MailRecipientUpsertRequest request
    ) {
        if (request == null) {
            return badRequest("request body must not be null");
        }
        try {
            GoldMailRecipient created = recipientService.create(request.email(), request.enabled());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "created",
                    "record", created
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateRecipient(
            @PathVariable("id") Long id,
            @RequestBody(required = false) MailRecipientUpsertRequest request
    ) {
        if (request == null) {
            return badRequest("request body must not be null");
        }
        try {
            GoldMailRecipient updated = recipientService.update(id, request.email(), request.enabled());
            return ResponseEntity.ok(Map.of(
                    "status", "updated",
                    "record", updated
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        } catch (NoSuchElementException ex) {
            return notFound(ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteRecipient(@PathVariable("id") Long id) {
        try {
            boolean deleted = recipientService.delete(id);
            if (!deleted) {
                return notFound("recipient not found: id=" + id);
            }
            return ResponseEntity.ok(Map.of(
                    "status", "deleted",
                    "id", id
            ));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
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

    public record MailRecipientUpsertRequest(String email, Boolean enabled) {
    }
}
