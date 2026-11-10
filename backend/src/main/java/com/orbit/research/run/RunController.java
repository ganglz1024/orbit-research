package com.orbit.research.run;

import com.orbit.research.auth.Accounts;
import com.orbit.research.common.ApiException;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@RestController
@RequestMapping("/api/v1")
public class RunController {
    private final Accounts accounts; private final Runs runs; private final RunStore store;
    private final ScheduledExecutorService streams = Executors.newScheduledThreadPool(2);
    private final AtomicInteger subscriptions = new AtomicInteger();
    public RunController(Accounts accounts, Runs runs, RunStore store) { this.accounts = accounts; this.runs = runs; this.store = store; }
    @PostMapping("/conversations/{id}/runs") public ResponseEntity<?> create(Authentication auth, @PathVariable String id, @RequestHeader(name="Idempotency-Key", required=false) String key, @Valid @RequestBody Runs.Input body) {
        var run = runs.create(accounts.owner(auth), id, key, body); return ResponseEntity.accepted().body(Map.of("runId", run.id(), "run", run));
    }
    @GetMapping("/runs/{id}") public RunStore.Run get(Authentication auth, @PathVariable String id) { return store.get(accounts.owner(auth), id); }
    @PostMapping("/runs/{id}/cancel") public RunStore.Run cancel(Authentication auth, @PathVariable String id) { return runs.cancel(accounts.owner(auth), id); }
    @GetMapping(value="/runs/{id}/events", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(Authentication auth, @PathVariable String id, @RequestParam(defaultValue="0") long after,
                                             @RequestHeader(name="Last-Event-ID", required=false) Long lastId) {
        String owner = accounts.owner(auth); store.get(owner, id); long start = lastId == null ? after : lastId;
        if (start < 0) throw new IllegalArgumentException();
        if (subscriptions.incrementAndGet() > 4) { subscriptions.decrementAndGet(); throw new ApiException(429, "SSE_LIMIT", "最多同时打开 4 条任务事件连接。"); }
        SseEmitter emitter = new SseEmitter(300000L); AtomicBoolean closed = new AtomicBoolean(); AtomicLong cursor = new AtomicLong(start), lastHeartbeat = new AtomicLong(System.nanoTime());
        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        Runnable cleanup = () -> { if (closed.compareAndSet(false, true)) { subscriptions.decrementAndGet(); var task = future.get(); if (task != null) task.cancel(false); } };
        emitter.onCompletion(cleanup); emitter.onTimeout(() -> { cleanup.run(); emitter.complete(); }); emitter.onError(error -> cleanup.run());
        future.set(streams.scheduleWithFixedDelay(() -> {
            if (closed.get()) return;
            try {
                var events = store.events(owner, id, cursor.get());
                for (var event : events) { emitter.send(SseEmitter.event().id(String.valueOf(event.eventId())).name(event.type()).data(event)); cursor.set(event.eventId()); }
                // A second read after the terminal state closes the final-event race.
                if (RunStore.terminal(store.get(owner, id).status()) && store.events(owner, id, cursor.get()).isEmpty()) { cleanup.run(); emitter.complete(); }
                else if (System.nanoTime() - lastHeartbeat.get() > TimeUnit.SECONDS.toNanos(10)) { emitter.send(SseEmitter.event().comment("heartbeat")); lastHeartbeat.set(System.nanoTime()); }
            } catch (Exception error) { cleanup.run(); emitter.completeWithError(error); }
        }, 20, 250, TimeUnit.MILLISECONDS));
        if (closed.get()) future.get().cancel(false);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering", "no").body(emitter);
    }
    @PreDestroy public void close() { streams.shutdownNow(); }
}
