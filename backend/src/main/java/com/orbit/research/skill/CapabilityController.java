package com.orbit.research.skill;

import com.orbit.research.auth.Accounts;
import com.orbit.research.llm.ModelSettings;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1")
public class CapabilityController {
    private final Accounts accounts; private final Skills skills; private final ModelSettings models;
    public CapabilityController(Accounts accounts, Skills skills, ModelSettings models) { this.accounts = accounts; this.skills = skills; this.models = models; }
    public record Enabled(@NotNull Boolean enabled) {}
    @GetMapping("/skills") public Map<String, Object> skills(Authentication auth) { return Map.of("items", skills.list(accounts.owner(auth))); }
    @PatchMapping("/skills/{id}/enabled") public Skills.Skill enabled(Authentication auth, @PathVariable String id, @Valid @RequestBody Enabled body) { return skills.toggle(accounts.owner(auth), id, body.enabled()); }
    @GetMapping("/skills/{id}") public Skills.Skill get(Authentication auth, @PathVariable String id) { return skills.get(accounts.owner(auth), id); }
    @PostMapping("/skills") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public Skills.Skill create(Authentication auth, @Valid @RequestBody Skills.Input body) { return skills.create(accounts.owner(auth), body); }
    @PutMapping("/skills/{id}") public Skills.Skill update(Authentication auth, @PathVariable String id, @Valid @RequestBody Skills.Input body) { return skills.update(accounts.owner(auth), id, body); }
    @DeleteMapping("/skills/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void delete(Authentication auth, @PathVariable String id) { skills.delete(accounts.owner(auth), id); }
    @GetMapping("/providers") public Map<String, Object> providers(Authentication auth) { var c = models.get(accounts.owner(auth)); return Map.of("items", List.of(Map.of("id", "llm", "type", "openai-compatible", "configured", c.configured(), "model", c.model(), "configuration", c.source()))); }
}
