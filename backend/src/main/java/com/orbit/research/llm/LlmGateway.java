package com.orbit.research.llm;

import com.orbit.research.common.ApiException;
import com.orbit.research.common.Json;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** OpenAI-compatible SSE transport. Only the application executor may resolve approved Skill calls. */
@Service
public class LlmGateway {
    public record ToolCall(String id, String name, String arguments) {
        public Map<String, Object> message() { return Map.of("id", id, "type", "function", "function", Map.of("name", name, "arguments", arguments)); }
    }
    public record Result(Long promptTokens, Long completionTokens, String finishReason, List<ToolCall> toolCalls) {}
    private static class ToolParts {
        final StringBuilder id = new StringBuilder(), name = new StringBuilder(), arguments = new StringBuilder();
        void add(JsonNode value) {
            if (value.hasNonNull("id")) id.append(value.path("id").asString());
            if (value.path("function").hasNonNull("name")) name.append(value.path("function").path("name").asString());
            if (value.path("function").hasNonNull("arguments")) arguments.append(value.path("function").path("arguments").asString());
            if (id.length() > 128 || name.length() > 64 || arguments.length() > 2000) throw new ApiException(502, "TOOL_ARGUMENT_LIMIT", "模型工具参数超过限制。");
        }
        ToolCall complete() {
            if (id.isEmpty() || name.isEmpty() || arguments.isEmpty()) throw new ApiException(502, "TOOL_PROTOCOL_ERROR", "模型工具调用缺少必要参数。");
            return new ToolCall(id.toString(), name.toString(), arguments.toString());
        }
    }
    private final LlmProperties settings;
    private final Json json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public LlmGateway(LlmProperties settings, Json json) { this.settings = settings; this.json = json; }
    public void requireConfigured() { requireConfigured(settings); }
    public void requireConfigured(LlmProperties settings) {
        if (!settings.configured()) throw new ApiException(503, "LLM_NOT_CONFIGURED", "请在后端配置模型 API 地址、模型名与密钥。");
        if (settings.timeoutSeconds() < 1 || settings.timeoutSeconds() > 300 || settings.maxOutputTokens() < 1 || settings.maxOutputTokens() > 16384) throw new ApiException(503, "LLM_CONFIG_INVALID", "模型调用限额配置不正确。");
        if (!Set.of("max_tokens", "max_completion_tokens").contains(settings.tokenParameter())) throw new ApiException(503, "LLM_CONFIG_INVALID", "模型输出长度参数配置不正确。");
        endpoint(settings);
    }
    public static URI endpoint(LlmProperties settings) {
        try {
            URI base = URI.create(settings.baseUrl());
            if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !("https".equals(base.getScheme()) || settings.allowPrivateHosts() && "http".equals(base.getScheme()))) throw new IllegalArgumentException();
            if (!settings.allowPrivateHosts()) for (InetAddress ip : InetAddress.getAllByName(base.getHost())) {
                if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress() || ip.isSiteLocalAddress() || ip.isMulticastAddress()
                    || ip.getHostAddress().startsWith("fc") || ip.getHostAddress().startsWith("fd")) throw new IllegalArgumentException();
            }
            return URI.create(settings.baseUrl().replaceAll("/+$", "") + "/chat/completions");
        } catch (Exception error) { throw new ApiException(503, "LLM_CONFIG_INVALID", "模型地址无效、无法解析，或未显式允许本地地址。"); }
    }
    public Result stream(List<Map<String, String>> messages, Consumer<String> delta, CallControl control) {
        return stream(settings, messages, List.of(), delta, control);
    }
    public Result stream(LlmProperties settings, List<? extends Map<String, ?>> messages, List<Map<String, Object>> tools, Consumer<String> delta, CallControl control) {
        return stream(settings,messages,tools,delta,control,"");
    }
    public Result stream(LlmProperties settings, List<? extends Map<String, ?>> messages, List<Map<String, Object>> tools, Consumer<String> delta, CallControl control,String reasoningEffort) {
        requireConfigured(settings);
        ModelCapabilities.validate(settings,reasoningEffort);
        if (control.stopped()) throw new java.util.concurrent.CancellationException();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model()); body.put("messages", messages); body.put("stream", true); body.put(settings.tokenParameter(), settings.maxOutputTokens());
        ModelCapabilities.apply(settings,reasoningEffort,body);
        if (settings.includeUsage()) body.put("stream_options", Map.of("include_usage", true));
        if (!tools.isEmpty()) { body.put("tools", tools); body.put("tool_choice", "auto"); body.put("parallel_tool_calls", false); }
        HttpRequest request = HttpRequest.newBuilder(endpoint(settings)).timeout(Duration.ofSeconds(settings.timeoutSeconds()))
            .header("Authorization", "Bearer " + settings.apiKey()).header("Content-Type", "application/json")
            .header("Accept", "text/event-stream").POST(HttpRequest.BodyPublishers.ofString(json.write(body), StandardCharsets.UTF_8)).build();
        try {
            var future = http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()); control.future(future);
            var response = future.get(settings.timeoutSeconds(), TimeUnit.SECONDS); control.body(response.body());
            if (response.statusCode() != 200) throw new ApiException(502, "LLM_UPSTREAM_" + response.statusCode(), "模型服务返回错误，请检查后端配置、额度或供应商状态。");
            if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT).startsWith("text/event-stream")) throw new ApiException(502, "LLM_PROTOCOL_ERROR", "模型服务没有返回兼容的 SSE 数据流。");
            Long prompt = null, completion = null; String finish = null;
            Map<Integer, ToolParts> toolParts = new TreeMap<>();
            try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line; StringBuilder event = new StringBuilder(); int total = 0; long reasoningChars=0;
                while (!control.stopped() && (line = boundedLine(reader)) != null) {
                    total += line.length();
                    if (total > 16_000_000) throw new ApiException(502, "LLM_RESPONSE_LIMIT", "模型响应超过本次限制。");
                    if (line.startsWith("data:")) { if (!event.isEmpty()) event.append('\n'); event.append(line.substring(5).stripLeading()); }
                    if (!line.isEmpty() || event.isEmpty()) continue;
                    String data = event.toString(); event.setLength(0);
                    if ("[DONE]".equals(data)) {
                        if (finish == null) throw new ApiException(502, "LLM_INCOMPLETE", "模型流缺少完成状态。");
                        var calls = toolParts.values().stream().map(ToolParts::complete).toList();
                        if (finish.equals("tool_calls") != !calls.isEmpty()) throw new ApiException(502, "TOOL_PROTOCOL_ERROR", "模型工具调用未完整结束。");
                        return new Result(prompt, completion, finish, calls);
                    }
                    JsonNode value = json.read(data);
                    if (value.hasNonNull("error")) throw new ApiException(502, "LLM_STREAM_ERROR", "模型在生成途中返回错误。");
                    JsonNode usage = value.path("usage");
                    if (usage.path("prompt_tokens").isIntegralNumber()) prompt = Math.max(0, usage.path("prompt_tokens").asLong());
                    if (usage.path("completion_tokens").isIntegralNumber()) completion = Math.max(0, usage.path("completion_tokens").asLong());
                    JsonNode choice = value.path("choices").path(0), content = choice.path("delta").path("content");
                    var reasoning=choice.path("delta").path("reasoning_content");if(reasoning.isString())reasoningChars+=reasoning.asString().length();
                    if(reasoningChars>12_000_000)throw new ApiException(502,"LLM_REASONING_LIMIT","思考流超过本次安全限额，请降低思考档位后重试。");
                    if (content.isString() && !content.asString().isEmpty()) delta.accept(content.asString());
                    JsonNode calls = choice.path("delta").path("tool_calls");
                    if (calls.isArray()) for (JsonNode call : calls) {
                        if (tools.isEmpty()) throw new ApiException(502, "TOOL_NOT_ALLOWED", "当前请求未允许模型调用 Skill。");
                        int index = call.path("index").asInt(-1);
                        if (index < 0 || index >= 8) throw new ApiException(502, "TOOL_CALL_LIMIT", "模型工具调用数量超过限制。");
                        toolParts.computeIfAbsent(index, ignored -> new ToolParts()).add(call);
                    }
                    if (choice.path("delta").hasNonNull("refusal")) throw new ApiException(422, "MODEL_REFUSED", "模型未能回答本次请求，请调整问题。");
                    if (choice.hasNonNull("finish_reason")) {
                        finish = choice.path("finish_reason").asString();
                        if (!Set.of("stop", "length", "tool_calls").contains(finish)) throw new ApiException(502, "LLM_UNSUPPORTED_FINISH", "模型返回了不支持的完成状态。");
                    }
                }
            }
            throw new ApiException(502, "LLM_INCOMPLETE", "模型连接中断，未完成内容已保留在任务记录中。");
        } catch (ApiException error) { throw error; }
        catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ApiException(502, "LLM_CONNECTION_FAILED", "模型连接失败或超时，请检查供应商配置后重试。");
        } finally { control.closeBody(); }
    }
    private static String boundedLine(BufferedReader reader) throws java.io.IOException {
        StringBuilder value = new StringBuilder(); int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') value.append((char) c);
            if (value.length() > 65536) throw new java.io.IOException("SSE line limit");
        }
        return c == -1 && value.isEmpty() ? null : value.toString();
    }
}
