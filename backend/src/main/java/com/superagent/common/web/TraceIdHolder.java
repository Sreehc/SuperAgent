package com.superagent.common.web;

import java.util.UUID;

public final class TraceIdHolder {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceIdHolder() {
    }

    public static String getOrCreate() {
        String current = TRACE_ID.get();
        if (current != null) {
            return current;
        }

        String generated = "trc_" + UUID.randomUUID().toString().replace("-", "");
        TRACE_ID.set(generated);
        return generated;
    }

    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
