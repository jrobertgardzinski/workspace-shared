package com.jrobertgardzinski.closure;

/**
 * Every message the account-closure saga sends, by the name it goes by on the wire — and, in the
 * order below, the whole conversation.
 *
 * <ol>
 *   <li>Identity states {@link #ACCOUNT_DELETION_REQUESTED}: an account is closing, here is who
 *       asked and under which conditions.</li>
 *   <li>The orchestrator commands each participant to {@link #PURGE_USER_CONTENT} — mark and hide,
 *       reversibly, because nothing may be destroyed while the saga can still fail.</li>
 *   <li>Each participant answers {@link #USER_CONTENT_PURGED} once its content is out of sight.</li>
 *   <li>With every participant in, the orchestrator commands {@link #ERASE_USER_CONTENT} and tells
 *       identity {@link #PORTAL_CONTENT_PURGED}; having given up, it commands
 *       {@link #RESTORE_USER_CONTENT} and says {@link #PORTAL_PURGE_FAILED} instead.</li>
 * </ol>
 *
 * <p>Only {@link #PURGE_USER_CONTENT} is answered. {@link #ERASE_USER_CONTENT} and
 * {@link #RESTORE_USER_CONTENT} are the orchestrator ending the case: a participant carries them
 * out and says nothing back.
 *
 * <p>These are constants rather than an enum on purpose: they cross five services and three
 * frameworks, they are read out of JSON, and a consumer that meets a name from a newer producer
 * must be able to ignore it rather than fail to deserialise a value object. What this type buys is
 * that nobody spells one of them twice.
 */
public final class ClosureMessages {

    /** Identity → the orchestrator: an account is closing. Carries {@code initiatedBy}. */
    public static final String ACCOUNT_DELETION_REQUESTED = "ACCOUNT_DELETION_REQUESTED";

    /** The orchestrator → a participant: hide this person's content, reversibly. */
    public static final String PURGE_USER_CONTENT = "PURGE_USER_CONTENT";

    /** The orchestrator → a participant: the closure stands, destroy what you marked. */
    public static final String ERASE_USER_CONTENT = "ERASE_USER_CONTENT";

    /** The orchestrator → a participant: the closure failed, put back what you marked. */
    public static final String RESTORE_USER_CONTENT = "RESTORE_USER_CONTENT";

    /** A participant → the orchestrator: my part is marked and hidden. */
    public static final String USER_CONTENT_PURGED = "USER_CONTENT_PURGED";

    /** The orchestrator → identity: every participant confirmed; the account may go. */
    public static final String PORTAL_CONTENT_PURGED = "PORTAL_CONTENT_PURGED";

    /** The orchestrator → identity: this was given up on; the account must be unlocked. */
    public static final String PORTAL_PURGE_FAILED = "PORTAL_PURGE_FAILED";

    private ClosureMessages() {
    }

    /** The fields the saga's messages carry that more than one service reads by name. */
    public static final class Field {

        /** The message's own name — the one every consumer switches on. */
        public static final String TYPE = "type";

        /** Whose account is closing, by address — on identity's fact and the verdicts back to it. */
        public static final String EMAIL = "email";

        /** Whose account is closing, by identity: security's id. The key of every command and confirmation. */
        public static final String USER_ID = "userId";

        /** Identity's handle on the closure, echoed by the verdict so a late one settles the right case. */
        public static final String SAGA_ID = "sagaId";

        /** {@link ClosureInitiator}, as a word. Stated ALWAYS, including for the ordinary value. */
        public static final String INITIATED_BY = "initiatedBy";

        /**
         * The leaver's stored choices, keyed by participant — present only when there are any, and
         * honoured only under {@link ClosureInitiator#ADMIN}.
         */
        public static final String POLICY = "policy";

        private Field() {
        }
    }
}
