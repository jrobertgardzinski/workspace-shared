package com.jrobertgardzinski.closure;

import com.jrobertgardzinski.identity.UserId;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A participant's answer to the reversible step: its content is out of sight and this is how much
 * of it there was. The mirror of {@link ClosureCommand}, and here for the same reason — three
 * services were spelling the same five fields by hand, and the day the agreement gains a sixth,
 * one of them is the one that forgets it.
 *
 * <p>That is not a hypothetical. On 2026-09-24 the portal's one-process spec harness built this
 * message by string concatenation and left {@code reserved} out, so eight scenarios ran green
 * against a message the portal does not send. Nothing behaved differently, which is exactly why
 * nobody would have noticed.
 *
 * <p>The record carries the three things a participant decides. Everything else about the message
 * — its {@code type}, its envelope version, and the rule about a blank saga id — is the
 * agreement's and belongs to {@link #fields()}, so no caller has to remember any of it.
 *
 * @param sagaId   the closure this answers; blank or absent is handled by {@link #fields()}
 * @param userId   whose account is closing, by identity
 * @param reserved how many things this participant took out of sight. Nothing reads it yet: it is
 *                 written by all three participants and consumed by no service, which is
 *                 deliberate — it is the difference between "I hid forty" and "I found nothing of
 *                 theirs", and it exists so that an operator, an alert or a later consumer can
 *                 tell a real erasure from one that only looked like it
 */
public record ClosureConfirmation(String sagaId, UserId userId, int reserved) {

    /** Envelope version — fields are only ever ADDED within version 1 (workspace ADR 0004). */
    public static final int VERSION = 1;

    /** How many things were taken out of sight. Written by every participant, read by none yet. */
    public static final String RESERVED = "reserved";

    /**
     * The message, field by field, for whatever mapper the caller happens to hold. A map rather
     * than a JSON string because this library has no dependencies and no opinion about
     * serialisation — but the FIELD SET is not the caller's to choose, which is the whole point:
     * add a field here and all three participants gain it in the same commit.
     *
     * <p><strong>A blank saga id is worse than an absent one</strong>, and that rule lived in three
     * copies of the same comment until it lived here. The orchestrator drops a confirmation whose
     * saga id is present but unparseable — a deliberate poison-pill rule — while one with NO saga
     * id falls back to matching by the leaver's id. So a purge command that arrived without the field (an
     * older producer, a hand-published record) had its confirmation thrown away rather than
     * matched, and the saga waited out its timeout for an answer that had in fact come back.
     */
    public Map<String, Object> fields() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put(ClosureMessages.Field.TYPE, ClosureMessages.USER_CONTENT_PURGED);
        message.put(ClosureMessages.Field.USER_ID, userId.toString());
        message.put(RESERVED, reserved);
        message.put("version", VERSION);
        if (sagaId != null && !sagaId.isBlank()) {
            message.put(ClosureMessages.Field.SAGA_ID, sagaId);
        }
        return Map.copyOf(message);
    }
}
