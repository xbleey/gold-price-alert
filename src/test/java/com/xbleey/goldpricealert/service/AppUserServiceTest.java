package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.model.AppUser;
import com.xbleey.goldpricealert.repository.AppUserStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-17T12:00:00Z");

    @Test
    void createShouldNormalizeUsernameEncodePasswordAndPersist() {
        AppUserStore store = mock(AppUserStore.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(store.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("admin")).thenReturn("{bcrypt}encoded-password");
        when(store.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0, AppUser.class);
            user.setId(1L);
            return user;
        });
        AppUserService service = new AppUserService(store, passwordEncoder, clock);

        AppUser created = service.create(" Admin ", "admin", "ADMIN", true);

        assertThat(created.getId()).isEqualTo(1L);
        assertThat(created.getUsername()).isEqualTo("admin");
        assertThat(created.getPassword()).isEqualTo("{bcrypt}encoded-password");
        assertThat(created.getRole()).isEqualTo("ADMIN");
        assertThat(created.getEnabled()).isTrue();
        assertThat(created.getCreatedAt()).isEqualTo(NOW);
        assertThat(created.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void createShouldRejectDuplicateUsername() {
        AppUserStore store = mock(AppUserStore.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AppUser existing = new AppUser();
        existing.setId(1L);
        existing.setUsername("admin");
        when(store.findByUsername("admin")).thenReturn(Optional.of(existing));
        AppUserService service = new AppUserService(store, passwordEncoder, clock);

        assertThatThrownBy(() -> service.create("admin", "admin", "ADMIN", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username already exists");
    }

    @Test
    void updateShouldApplyProvidedFields() {
        AppUserStore store = mock(AppUserStore.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        AppUser existing = new AppUser();
        existing.setId(2L);
        existing.setUsername("user");
        existing.setPassword("{bcrypt}old");
        existing.setRole("USER");
        existing.setEnabled(true);
        when(store.findById(2L)).thenReturn(Optional.of(existing));
        when(store.findByUsername("new-user")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("new-password")).thenReturn("{bcrypt}new");
        AppUserService service = new AppUserService(store, passwordEncoder, clock);

        AppUser updated = service.update(2L, "new-user", "new-password", "ADMIN", false);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(store).update(captor.capture());
        AppUser persisted = captor.getValue();
        assertThat(updated).isSameAs(existing);
        assertThat(persisted.getUsername()).isEqualTo("new-user");
        assertThat(persisted.getPassword()).isEqualTo("{bcrypt}new");
        assertThat(persisted.getRole()).isEqualTo("ADMIN");
        assertThat(persisted.getEnabled()).isFalse();
        assertThat(persisted.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void deleteShouldDelegateToStore() {
        AppUserStore store = mock(AppUserStore.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(store.deleteById(3L)).thenReturn(1);
        AppUserService service = new AppUserService(store, passwordEncoder, clock);

        boolean deleted = service.delete(3L);

        assertThat(deleted).isTrue();
        verify(store).deleteById(3L);
    }
}
