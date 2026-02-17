package com.xbleey.goldpricealert.controller;

import com.xbleey.goldpricealert.model.AppUser;
import com.xbleey.goldpricealert.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void listUsersReturnsRecordsWithoutPassword() {
        AppUserService userService = mock(AppUserService.class);
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("{bcrypt}hidden");
        user.setRole("ADMIN");
        user.setEnabled(true);
        user.setCreatedAt(Instant.parse("2026-02-17T12:00:00Z"));
        user.setUpdatedAt(Instant.parse("2026-02-17T12:00:00Z"));
        when(userService.listUsers()).thenReturn(List.of(user));
        UserController controller = new UserController(userService);

        Map<String, Object> response = controller.listUsers();

        assertThat(response).containsEntry("total", 1);
        @SuppressWarnings("unchecked")
        List<UserController.UserResponse> records = (List<UserController.UserResponse>) response.get("records");
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().username()).isEqualTo("admin");
    }

    @Test
    void createUserReturnsCreated() {
        AppUserService userService = mock(AppUserService.class);
        AppUser created = new AppUser();
        created.setId(2L);
        created.setUsername("user");
        created.setRole("USER");
        created.setEnabled(true);
        created.setCreatedAt(Instant.parse("2026-02-17T12:00:00Z"));
        created.setUpdatedAt(Instant.parse("2026-02-17T12:00:00Z"));
        when(userService.create("user", "pwd123", "USER", true)).thenReturn(created);
        UserController controller = new UserController(userService);

        ResponseEntity<Map<String, Object>> response = controller.createUser(
                new UserController.UserUpsertRequest("user", "pwd123", "USER", true)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("status", "created");
        verify(userService).create("user", "pwd123", "USER", true);
    }

    @Test
    void deleteUserReturnsNotFoundWhenMissing() {
        AppUserService userService = mock(AppUserService.class);
        when(userService.delete(3L)).thenReturn(false);
        UserController controller = new UserController(userService);

        ResponseEntity<Map<String, Object>> response = controller.deleteUser(3L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", "not_found");
    }
}
