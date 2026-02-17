package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.model.AppUser;
import com.xbleey.goldpricealert.repository.AppUserStore;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTest {

    @Test
    void loadUserShouldReturnUserDetails() {
        AppUserStore store = mock(AppUserStore.class);
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("{noop}admin");
        user.setRole("ADMIN");
        user.setEnabled(true);
        when(store.findByUsername("admin")).thenReturn(Optional.of(user));
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(store);

        UserDetails userDetails = service.loadUserByUsername("Admin");

        assertThat(userDetails.getUsername()).isEqualTo("admin");
        assertThat(userDetails.getPassword()).isEqualTo("{noop}admin");
        assertThat(userDetails.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    void loadUserShouldThrowWhenNotFound() {
        AppUserStore store = mock(AppUserStore.class);
        when(store.findByUsername("unknown")).thenReturn(Optional.empty());
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(store);

        assertThatThrownBy(() -> service.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("user not found");
    }
}
