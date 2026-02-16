package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.model.GoldMailRecipient;
import com.xbleey.goldpricealert.repository.GoldMailRecipientStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class GoldMailRecipientService {

    // 常规邮箱格式校验，覆盖新增和修改场景
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$");
    private static final int EMAIL_MAX_LENGTH = 320;

    private final GoldMailRecipientStore recipientStore;
    private final Clock clock;

    public GoldMailRecipientService(GoldMailRecipientStore recipientStore, Clock clock) {
        this.recipientStore = recipientStore;
        this.clock = clock;
    }

    public List<GoldMailRecipient> listRecipients() {
        return recipientStore.findAll();
    }

    public List<String> listEnabledEmails() {
        return recipientStore.findEnabled().stream()
                .map(GoldMailRecipient::getEmail)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    public GoldMailRecipient getById(Long id) {
        validateId(id);
        return recipientStore.findById(id)
                .orElseThrow(() -> new NoSuchElementException("recipient not found: id=" + id));
    }

    public GoldMailRecipient create(String email, Boolean enabled) {
        String normalizedEmail = normalizeAndValidateEmail(email);
        ensureEmailNotExists(normalizedEmail, null);

        Instant now = clock.instant();
        GoldMailRecipient record = new GoldMailRecipient();
        record.setEmail(normalizedEmail);
        record.setEnabled(enabled == null ? Boolean.TRUE : enabled);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        try {
            return recipientStore.save(record);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("email already exists: " + normalizedEmail, ex);
        }
    }

    public GoldMailRecipient update(Long id, String email, Boolean enabled) {
        validateId(id);
        if (email == null && enabled == null) {
            throw new IllegalArgumentException("at least one field (email/enabled) must be provided");
        }

        GoldMailRecipient existing = recipientStore.findById(id)
                .orElseThrow(() -> new NoSuchElementException("recipient not found: id=" + id));

        if (email != null) {
            String normalizedEmail = normalizeAndValidateEmail(email);
            ensureEmailNotExists(normalizedEmail, id);
            existing.setEmail(normalizedEmail);
        }
        if (enabled != null) {
            existing.setEnabled(enabled);
        }
        existing.setUpdatedAt(clock.instant());

        try {
            recipientStore.update(existing);
            return existing;
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("email already exists: " + existing.getEmail(), ex);
        }
    }

    public boolean delete(Long id) {
        validateId(id);
        return recipientStore.deleteById(id) > 0;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id must be > 0");
        }
    }

    private void ensureEmailNotExists(String email, Long currentId) {
        recipientStore.findByEmail(email).ifPresent(found -> {
            if (currentId == null || !Objects.equals(currentId, found.getId())) {
                throw new IllegalArgumentException("email already exists: " + email);
            }
        });
    }

    private String normalizeAndValidateEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (normalized.length() > EMAIL_MAX_LENGTH) {
            throw new IllegalArgumentException("email length must be <= " + EMAIL_MAX_LENGTH);
        }
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid email format: " + normalized);
        }
        return normalized;
    }
}
