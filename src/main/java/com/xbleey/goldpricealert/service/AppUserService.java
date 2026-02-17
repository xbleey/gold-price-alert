package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.model.AppUser;
import com.xbleey.goldpricealert.repository.AppUserStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AppUserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9._-]{3,64}$");
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "USER");
    private static final int PASSWORD_MIN_LENGTH = 4;
    private static final int PASSWORD_MAX_LENGTH = 128;

    private final AppUserStore userStore;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AppUserService(AppUserStore userStore, PasswordEncoder passwordEncoder, Clock clock) {
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public List<AppUser> listUsers() {
        return userStore.findAll();
    }

    public AppUser getById(Long id) {
        validateId(id);
        return userStore.findById(id)
                .orElseThrow(() -> new NoSuchElementException("user not found: id=" + id));
    }

    public AppUser create(String username, String rawPassword, String role, Boolean enabled) {
        String normalizedUsername = normalizeUsername(username);
        ensureUsernameNotExists(normalizedUsername, null);
        String normalizedRole = normalizeRole(role, "USER");
        String validatedPassword = validatePassword(rawPassword);

        Instant now = clock.instant();
        AppUser user = new AppUser();
        user.setUsername(normalizedUsername);
        user.setPassword(passwordEncoder.encode(validatedPassword));
        user.setRole(normalizedRole);
        user.setEnabled(enabled == null ? Boolean.TRUE : enabled);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            return userStore.save(user);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("username already exists: " + normalizedUsername, ex);
        }
    }

    public AppUser update(Long id, String username, String rawPassword, String role, Boolean enabled) {
        validateId(id);
        if (username == null && rawPassword == null && role == null && enabled == null) {
            throw new IllegalArgumentException("at least one field (username/password/role/enabled) must be provided");
        }

        AppUser existing = userStore.findById(id)
                .orElseThrow(() -> new NoSuchElementException("user not found: id=" + id));

        if (username != null) {
            String normalizedUsername = normalizeUsername(username);
            ensureUsernameNotExists(normalizedUsername, id);
            existing.setUsername(normalizedUsername);
        }
        if (rawPassword != null) {
            String validatedPassword = validatePassword(rawPassword);
            existing.setPassword(passwordEncoder.encode(validatedPassword));
        }
        if (role != null) {
            existing.setRole(normalizeRole(role, "USER"));
        }
        if (enabled != null) {
            existing.setEnabled(enabled);
        }
        existing.setUpdatedAt(clock.instant());

        try {
            userStore.update(existing);
            return existing;
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("username already exists: " + existing.getUsername(), ex);
        }
    }

    public boolean delete(Long id) {
        validateId(id);
        return userStore.deleteById(id) > 0;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id must be > 0");
        }
    }

    private void ensureUsernameNotExists(String username, Long currentId) {
        userStore.findByUsername(username).ifPresent(found -> {
            if (currentId == null || !Objects.equals(currentId, found.getId())) {
                throw new IllegalArgumentException("username already exists: " + username);
            }
        });
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            throw new IllegalArgumentException("username must not be null");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "username must match " + USERNAME_PATTERN.pattern() + " and length between 3 and 64"
            );
        }
        return normalized;
    }

    private String validatePassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "password length must be between " + PASSWORD_MIN_LENGTH + " and " + PASSWORD_MAX_LENGTH
            );
        }
        return password;
    }

    private String normalizeRole(String role, String defaultRole) {
        String value = role;
        if (value == null || value.isBlank()) {
            value = defaultRole;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("role must be one of " + ALLOWED_ROLES);
        }
        return normalized;
    }
}
