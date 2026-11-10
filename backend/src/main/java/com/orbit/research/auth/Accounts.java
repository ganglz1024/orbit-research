package com.orbit.research.auth;

import com.orbit.research.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class Accounts implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public Accounts(JdbcTemplate jdbc, PasswordEncoder passwords) { this.jdbc = jdbc; this.passwords = passwords; }
    public boolean initialized() { return jdbc.queryForObject("SELECT COUNT(*) FROM orbit_user", Integer.class) > 0; }
    public synchronized void setup(String username, String password) {
        if (initialized()) throw new ApiException(409, "ALREADY_INITIALIZED", "个人账户已创建，请登录。");
        jdbc.update("INSERT INTO orbit_user(id,username,password_hash,created_at) VALUES(?,?,?,?)", "owner", username, passwords.encode(password), Instant.now().toString());
    }
    public void changePassword(Authentication auth, String current, String next) {
        String original = jdbc.queryForObject("SELECT password_hash FROM orbit_user WHERE username=?", String.class, auth.getName());
        if (!passwords.matches(current, original)) throw new ApiException(403, "PASSWORD_INCORRECT", "当前密码不正确。");
        jdbc.update("UPDATE orbit_user SET password_hash=? WHERE username=?", passwords.encode(next), auth.getName());
    }
    @Override public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String username) {
        return jdbc.query("SELECT username,password_hash FROM orbit_user WHERE username=?", (rs, row) ->
            User.withUsername(rs.getString(1)).password(rs.getString(2)).roles("USER").build(), username)
            .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }
    public String owner(Authentication authentication) {
        return jdbc.query("SELECT id FROM orbit_user WHERE username=?", (rs, row) -> rs.getString(1), authentication.getName())
            .stream().findFirst().orElseThrow(ApiException::notFound);
    }
}
