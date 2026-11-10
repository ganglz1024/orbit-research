package com.orbit.research.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final Accounts accounts;
    private final JdbcTemplate jdbc;
    public AuthController(Accounts accounts, JdbcTemplate jdbc) { this.accounts = accounts; this.jdbc = jdbc; }
    public record Setup(@NotBlank @Pattern(regexp="[A-Za-z0-9_.@-]{3,64}") String username,
                        @NotBlank @Size(min=12, max=72) String password) {}
    public record Password(@NotBlank @Size(max=72) String currentPassword, @NotBlank @Size(min=12,max=72) String newPassword) {}
    @GetMapping("/health") public Map<String, Object> health() {
        jdbc.queryForObject("SELECT 1", Integer.class);
        return Map.of("status", "UP", "service", "orbit-backend", "java", "17", "springBoot", "4.0.9");
    }
    @GetMapping("/setup") public Map<String, Boolean> setupStatus() { return Map.of("initialized", accounts.initialized()); }
    @PostMapping("/setup") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Boolean> setup(@Valid @RequestBody Setup body) {
        // BCrypt has a 72-byte limit, distinct from a Unicode character count.
        if (body.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException();
        accounts.setup(body.username(), body.password()); return Map.of("initialized", true);
    }
    @GetMapping("/auth/csrf") public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
    @GetMapping("/auth/me") public Map<String, String> me(Authentication auth) { return Map.of("id", accounts.owner(auth), "username", auth.getName()); }
    @PostMapping("/auth/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(Authentication auth, @Valid @RequestBody Password body, HttpServletRequest request) {
        if (body.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException();
        accounts.changePassword(auth, body.currentPassword(), body.newPassword());
        request.getSession().invalidate();
    }
}
