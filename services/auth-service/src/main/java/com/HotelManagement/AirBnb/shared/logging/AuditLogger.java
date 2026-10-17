package com.HotelManagement.AirBnb.shared.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Business and security audit trail (logger "AUDIT" → audit.log, kept separate from the application log).
 * One line per event: {@code event=BOOKING_CANCELLED outcome=SUCCESS actor=42 bookingId=7 by=guest}. The actor is the
 * authenticated user of the current request (or "system" for background jobs). Never pass passwords, tokens,
 * signatures or card data. Static, so any class can call {@code AuditLogger.success(...)} without injection.
 */
public final class AuditLogger {

    private AuditLogger() {
    }

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    public static void success(String event, Object... keyValues) {
        write(event, "SUCCESS", keyValues);
    }

    public static void failure(String event, Object... keyValues) {
        write(event, "FAILURE", keyValues);
    }

    private static void write(String event, String outcome, Object... keyValues) {
        StringBuilder line = new StringBuilder("event=").append(event).append(" outcome=").append(outcome);
        String user = MDC.get(RequestLoggingFilter.MDC_USER_ID);
        String requestId = MDC.get(RequestLoggingFilter.MDC_REQUEST_ID);
        String actor = user != null ? user
                : requestId == null || requestId.startsWith("job-") ? "system" : "anonymous";
        line.append(" actor=").append(actor);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            line.append(' ').append(keyValues[i]).append('=').append(quote(keyValues[i + 1]));
        }
        audit.info(line.toString());
    }

    private static String quote(Object value) {
        String s = String.valueOf(value).replaceAll("[\\r\\n]", " ");
        return s.contains(" ") || s.isEmpty() ? "\"" + s.replace("\"", "'") + "\"" : s;
    }

    /** Runs a background job step with its own request id ("job-<name>-…") so its log lines can be traced. */
    public static void withJobId(String job, Runnable task) {
        String previous = MDC.get(RequestLoggingFilter.MDC_REQUEST_ID);
        MDC.put(RequestLoggingFilter.MDC_REQUEST_ID, "job-" + job + "-" + Long.toString(System.nanoTime(), 36));
        try {
            task.run();
        } finally {
            if (previous == null) {
                MDC.remove(RequestLoggingFilter.MDC_REQUEST_ID);
            } else {
                MDC.put(RequestLoggingFilter.MDC_REQUEST_ID, previous);
            }
        }
    }
}
