package com.orbit.research.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orbit.llm")
public record LlmProperties(String baseUrl, String apiKey, String model, boolean allowPrivateHosts,
                            int timeoutSeconds, int maxOutputTokens, boolean includeUsage, String tokenParameter) {
    public boolean configured() { return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank(); }
    @Override public String toString() { return "LlmProperties[configured=" + configured() + "]"; }
}
