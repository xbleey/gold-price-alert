package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.model.AppUser;
import com.xbleey.goldpricealert.repository.AppUserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin";

    private final AppUserStore userStore;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminAccountInitializer(AppUserStore userStore, PasswordEncoder passwordEncoder, Clock clock) {
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userStore.findByUsername(DEFAULT_ADMIN_USERNAME).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        AppUser admin = new AppUser();
        admin.setUsername(DEFAULT_ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
        admin.setRole("ADMIN");
        admin.setEnabled(Boolean.TRUE);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        try {
            userStore.save(admin);
            log.info("Bootstrap admin account created: username={}", DEFAULT_ADMIN_USERNAME);
        } catch (DuplicateKeyException ex) {
            log.info("Bootstrap admin account already exists: username={}", DEFAULT_ADMIN_USERNAME);
        }
    }
}
