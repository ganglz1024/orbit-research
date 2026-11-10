package com.orbit.research.llm;

import com.orbit.research.common.ApiException;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class ModelSettings {
    public record Input(@NotBlank @Size(max=1000) String baseUrl, @NotBlank @Size(max=200) String model,
                        @Size(max=4096) @Pattern(regexp="[^\\r\\n]*") String apiKey, Boolean clearApiKey, @Min(5) @Max(300) int timeoutSeconds,
                        @Min(1) @Max(16384) int maxOutputTokens, boolean includeUsage,
                        @NotNull @Pattern(regexp="max_tokens|max_completion_tokens") String tokenParameter,
                        @Min(0) int revision) { public Input { if (clearApiKey == null) clearApiKey = false; } @Override public String toString() { return "ModelInput[redacted]"; } }
    public record View(String baseUrl, String model, boolean hasApiKey, boolean configured, int timeoutSeconds,
                       int maxOutputTokens, boolean includeUsage, String tokenParameter, int revision, String source, String updatedAt) {}
    public record Preference(@NotNull @Size(max=4000) String researchContext,
                             @NotBlank @Size(max=64) String defaultSkillId, boolean allowSkillTools,
                             @Pattern(regexp="demo|model") String assistantMode) { public Preference { if (assistantMode == null) assistantMode = "demo"; } }
    private final JdbcTemplate jdbc; private final LlmProperties environment; private final SecretVault vault;
    public ModelSettings(JdbcTemplate jdbc, LlmProperties environment, SecretVault vault) { this.jdbc = jdbc; this.environment = environment; this.vault = vault; }
    public LlmProperties effective(String owner) {
        return jdbc.query("SELECT * FROM model_configuration WHERE owner_id=?", (rs, row) -> new LlmProperties(rs.getString("base_url"), vault.decrypt(owner, rs.getString("api_key_cipher")), rs.getString("model"), environment.allowPrivateHosts(), rs.getInt("timeout_seconds"), rs.getInt("max_output_tokens"), rs.getBoolean("include_usage"), rs.getString("token_parameter")), owner).stream().findFirst().orElse(environment);
    }
    public View get(String owner) {
        return jdbc.query("SELECT * FROM model_configuration WHERE owner_id=?", (rs, row) -> {
            boolean key = !rs.getString("api_key_cipher").isBlank();
            return new View(rs.getString("base_url"), rs.getString("model"), key, key, rs.getInt("timeout_seconds"), rs.getInt("max_output_tokens"), rs.getBoolean("include_usage"), rs.getString("token_parameter"), rs.getInt("revision"), "account", rs.getString("updated_at"));
        }, owner).stream().findFirst().orElseGet(() -> new View(text(environment.baseUrl()), text(environment.model()), !text(environment.apiKey()).isBlank(), environment.configured(), environment.timeoutSeconds(), environment.maxOutputTokens(), environment.includeUsage(), environment.tokenParameter(), 0, "environment", ""));
    }
    private static String text(String value) { return value == null ? "" : value; }
    @Transactional public View save(String owner, Input input) {
        jdbc.queryForObject("SELECT id FROM orbit_user WHERE id=? FOR UPDATE", String.class, owner);
        View before = get(owner);
        if (before.revision() != input.revision()) throw new ApiException(409, "CONFIG_CONFLICT", "配置已更新，请重新加载后编辑。");
        if (input.clearApiKey() && input.apiKey() != null && !input.apiKey().isBlank()) throw new IllegalArgumentException();
        String nextKey = input.clearApiKey() ? "" : input.apiKey() == null || input.apiKey().isBlank() ? effective(owner).apiKey() : input.apiKey().trim();
        LlmProperties next = new LlmProperties(input.baseUrl().trim().replaceAll("/+$", ""), nextKey, input.model().trim(), environment.allowPrivateHosts(), input.timeoutSeconds(), input.maxOutputTokens(), input.includeUsage(), input.tokenParameter());
        LlmGateway.endpoint(next); // Validate the destination even when the key is intentionally cleared.
        String encrypted = vault.encrypt(owner, nextKey), now = Instant.now().toString();
        if (jdbc.update("UPDATE model_configuration SET base_url=?,model=?,api_key_cipher=?,timeout_seconds=?,max_output_tokens=?,include_usage=?,token_parameter=?,revision=?,updated_at=? WHERE owner_id=?", next.baseUrl(), next.model(), encrypted, next.timeoutSeconds(), next.maxOutputTokens(), next.includeUsage(), next.tokenParameter(), before.revision()+1, now, owner) == 0)
            jdbc.update("INSERT INTO model_configuration VALUES(?,?,?,?,?,?,?,?,?,?)", owner, next.baseUrl(), next.model(), encrypted, next.timeoutSeconds(), next.maxOutputTokens(), next.includeUsage(), next.tokenParameter(), 1, now);
        return get(owner);
    }
    public Preference preference(String owner) {
        return jdbc.query("SELECT * FROM research_preference WHERE owner_id=?", (rs, row) -> new Preference(rs.getString("research_context"), rs.getString("default_skill_id"), rs.getBoolean("allow_skill_tools"), rs.getString("assistant_mode")), owner).stream().findFirst().orElse(new Preference("", "chat", false, "demo"));
    }
    @Transactional public Preference savePreference(String owner, Preference input) {
        jdbc.queryForObject("SELECT id FROM orbit_user WHERE id=? FOR UPDATE", String.class, owner);
        if (!input.defaultSkillId().equals("chat") && jdbc.queryForObject("SELECT COUNT(*) FROM custom_skill WHERE id=? AND owner_id=? AND deleted=FALSE", Integer.class, input.defaultSkillId(), owner) == 0 && !java.util.Set.of("paper_read", "paper_compare").contains(input.defaultSkillId()) && !com.orbit.research.skill.AcademicResearchSkillCatalog.contains(input.defaultSkillId())) throw ApiException.notFound();
        if (jdbc.update("UPDATE research_preference SET research_context=?,default_skill_id=?,allow_skill_tools=?,assistant_mode=?,updated_at=? WHERE owner_id=?", input.researchContext(), input.defaultSkillId(), input.allowSkillTools(), input.assistantMode(), Instant.now().toString(), owner) == 0)
            jdbc.update("INSERT INTO research_preference VALUES(?,?,?,?,?,?)", owner, input.researchContext(), input.defaultSkillId(), input.allowSkillTools(), input.assistantMode(), Instant.now().toString());
        return preference(owner);
    }
}
