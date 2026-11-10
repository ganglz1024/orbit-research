package com.orbit.research.run;

import com.orbit.research.common.ApiException;
import com.orbit.research.common.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class RunStore {
    public record Run(String id, String conversationId, String status, String answer, String errorCode, String createdAt, String updatedAt, Long promptTokens, Long completionTokens) {}
    public record Event(String runId, long eventId, String type, String timestamp, int schemaVersion, Object data) {}
    private final JdbcTemplate jdbc; private final Json json; private final TransactionTemplate tx;
    public RunStore(JdbcTemplate jdbc, Json json, TransactionTemplate tx) { this.jdbc = jdbc; this.json = json; this.tx = tx; }
    private Run map(ResultSet rs, int row) throws SQLException { return new Run(rs.getString("id"), rs.getString("conversation_id"), rs.getString("status"), rs.getString("answer"), rs.getString("error_code"), rs.getString("created_at"), rs.getString("updated_at"), (Long) rs.getObject("prompt_tokens", Long.class), (Long) rs.getObject("completion_tokens", Long.class)); }
    public Run get(String owner, String id) { return jdbc.query("SELECT * FROM run_record WHERE id=? AND owner_id=?", this::map, id, owner).stream().findFirst().orElseThrow(ApiException::notFound); }
    public Optional<Run> byKey(String owner, String key) { return jdbc.query("SELECT * FROM run_record WHERE owner_id=? AND idempotency_key=?", this::map, owner, key).stream().findFirst(); }
    public List<Event> events(String owner, String id, long after) {
        get(owner, id);
        if (after < 0) throw new IllegalArgumentException();
        return jdbc.query("SELECT * FROM run_event WHERE run_id=? AND event_id>? ORDER BY event_id LIMIT 100", (rs, row) -> new Event(id, rs.getLong("event_id"), rs.getString("event_type"), rs.getString("created_at"), 1, json.read(rs.getString("payload"))), id, after);
    }
    public void event(String id, String type, Object data) {
        tx.executeWithoutResult(status -> {
        jdbc.queryForObject("SELECT id FROM run_record WHERE id=? FOR UPDATE", String.class, id);
        Long next = jdbc.queryForObject("SELECT COALESCE(MAX(event_id),0)+1 FROM run_event WHERE run_id=?", Long.class, id);
        jdbc.update("INSERT INTO run_event VALUES(?,?,?,?,?)", id, next, type, json.write(data), Instant.now().toString());
        });
    }
    public boolean start(String owner, String id) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            if (jdbc.update("UPDATE run_record SET status='running',updated_at=? WHERE id=? AND status='queued'", Instant.now().toString(), id) == 0) return false;
            event(id, "run_started", Map.of("skillVersion", "1.0.0", "promptVersion", "research-1"));
            event(id, "step_started", Map.of("stepId", "llm", "tool", "openai-compatible.chat")); return true;
        }));
    }
    public boolean append(String id, String text, String complete) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            if (jdbc.update("UPDATE run_record SET answer=?,updated_at=? WHERE id=? AND status='running'", complete, Instant.now().toString(), id) == 0) return false;
            event(id, "answer_delta", Map.of("delta", text)); return true;
        }));
    }
    public void finish(String owner, String id, String state, String error, Long inputTokens, Long outputTokens, String finishReason) {
        tx.executeWithoutResult(status -> {
            Run before = get(owner, id);
            if (!Set.of("queued", "running").contains(before.status())) return;
            String now = Instant.now().toString();
            if (jdbc.update("UPDATE run_record SET status=?,error_code=?,prompt_tokens=?,completion_tokens=?,updated_at=? WHERE id=? AND status IN ('queued','running')", state, error, inputTokens, outputTokens, now, id) == 0) return;
            if ("succeeded".equals(state)) {
                jdbc.update("INSERT INTO chat_message VALUES(?,?,?,?,?,?)", UUID.randomUUID().toString(), before.conversationId(), id, "assistant", before.answer(), now);
                if ("length".equals(finishReason)) event(id, "warning", Map.of("code", "OUTPUT_TRUNCATED", "message", "回答已达到输出上限，可能尚未完整。"));
            }
            jdbc.update("UPDATE conversation SET updated_at=? WHERE id=?", now, before.conversationId());
            Map<String, Object> data = new LinkedHashMap<>(); data.put("status", state); data.put("code", error); data.put("usage", Map.of("known", inputTokens != null && outputTokens != null));
            String type = "succeeded".equals(state) ? "run_completed" : "cancelled".equals(state) ? "run_cancelled" : "run_failed";
            event(id, type, data);
        });
    }
    public static boolean terminal(String status) { return !Set.of("queued", "running").contains(status); }
}
