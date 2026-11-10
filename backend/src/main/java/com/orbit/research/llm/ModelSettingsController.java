package com.orbit.research.llm;

import com.orbit.research.auth.Accounts;
import com.orbit.research.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/v1/settings")
public class ModelSettingsController {
    private final Accounts accounts; private final ModelSettings settings; private final LlmGateway gateway;
    private final Set<String> testing = ConcurrentHashMap.newKeySet();
    public ModelSettingsController(Accounts accounts, ModelSettings settings, LlmGateway gateway) { this.accounts = accounts; this.settings = settings; this.gateway = gateway; }
    @GetMapping("/model") public ModelSettings.View get(Authentication auth) { return settings.get(accounts.owner(auth)); }
    @PutMapping("/model") public ModelSettings.View save(Authentication auth, @Valid @RequestBody ModelSettings.Input body) { return settings.save(accounts.owner(auth), body); }
    @GetMapping("/preferences") public ModelSettings.Preference preference(Authentication auth) { return settings.preference(accounts.owner(auth)); }
    @PutMapping("/preferences") public ModelSettings.Preference savePreference(Authentication auth, @Valid @RequestBody ModelSettings.Preference body) { return settings.savePreference(accounts.owner(auth), body); }
    @PostMapping("/model/test") public Map<String, Object> test(Authentication auth) {
        String owner = accounts.owner(auth);
        if (!testing.add(owner)) throw new ApiException(409, "TEST_BUSY", "连接测试正在进行。");
        CallControl control = new CallControl();
        try {
            var c = settings.effective(owner);
            var probe = new LlmProperties(c.baseUrl(), c.apiKey(), c.model(), c.allowPrivateHosts(), Math.min(30, c.timeoutSeconds()), Math.min(16, c.maxOutputTokens()), c.includeUsage(), c.tokenParameter());
            ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
            var deadline = timer.schedule(() -> control.cancel(true), Math.min(30, c.timeoutSeconds()), TimeUnit.SECONDS);
            try {
                StringBuilder answer = new StringBuilder();
                gateway.stream(probe, List.of(Map.of("role", "user", "content", "Reply OK.")), List.of(), text -> { if (answer.length() > 2000) throw new IllegalArgumentException(); answer.append(text); }, control);
                if (answer.isEmpty()) throw new ApiException(502, "EMPTY_ANSWER", "服务连接成功，但模型未返回文字。");
                return Map.of("connected", true, "model", c.model());
            } finally { deadline.cancel(false); timer.shutdownNow(); }
        } finally { control.cancel(false); testing.remove(owner); }
    }
}
