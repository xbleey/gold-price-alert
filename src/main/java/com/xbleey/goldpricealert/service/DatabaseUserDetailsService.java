package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.model.AppUser;
import com.xbleey.goldpricealert.repository.AppUserStore;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserStore userStore;

    public DatabaseUserDetailsService(AppUserStore userStore) {
        this.userStore = userStore;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = normalizeUsername(username);
        AppUser user = userStore.findByUsername(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("user not found: " + normalizedUsername));
        String role = normalizeRole(user.getRole());
        return User.withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(new SimpleGrantedAuthority("ROLE_" + role))
                .disabled(!Boolean.TRUE.equals(user.getEnabled()))
                .build();
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("username must not be blank");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "USER";
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }
}
