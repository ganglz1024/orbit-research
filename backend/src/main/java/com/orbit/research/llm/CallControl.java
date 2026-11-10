package com.orbit.research.llm;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class CallControl {
    private final AtomicBoolean stopped = new AtomicBoolean();
    private volatile boolean timedOut;
    private volatile CompletableFuture<?> future;
    private volatile InputStream body;
    public boolean stopped() { return stopped.get(); }
    public boolean timedOut() { return timedOut; }
    public synchronized void future(CompletableFuture<?> value) { future = value; if (stopped()) value.cancel(true); }
    public synchronized void body(InputStream value) { body = value; if (stopped()) closeBody(); }
    public synchronized void cancel(boolean timeout) {
        timedOut = timeout; stopped.set(true);
        if (future != null) future.cancel(true);
        closeBody();
    }
    public synchronized void closeBody() { if (body != null) { try { body.close(); } catch (java.io.IOException ignored) {} body = null; } }
}
