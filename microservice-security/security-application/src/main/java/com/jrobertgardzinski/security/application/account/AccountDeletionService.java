package com.jrobertgardzinski.security.application.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.AccountClosure;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.security.domain.vo.PurgeChoices;
import com.jrobertgardzinski.security.domain.vo.Role;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.system.account.StartAccountDeletion;
import com.jrobertgardzinski.security.system.mfa.SessionElevation;
import com.jrobertgardzinski.security.system.roles.RequireRole;

import java.util.Map;

/**
 * The bridge for the ONE door out of an account, for the person whose account it is and for an
 * administrator closing somebody else's. Which of the two it is comes from the request itself, the
 * address it names against the address of the caller, and everything else follows from that
 * comparison: what must be proven, and whether the caller may say what happens to the content.
 *
 * <ul>
 *   <li><strong>Your own account.</strong> A fresh step-up ({@link StepUpAction#DELETE_ACCOUNT}),
 *       and then everything you ever posted goes. This request IS the right to erasure, and the
 *       grounds on which content may survive such a request are enumerated by law, so any
 *       conditions the request carries are not even read.</li>
 *   <li><strong>Somebody else's.</strong> The ADMIN role on top of the step-up
 *       ({@link StepUpAction#ADMIN_DELETE_ACCOUNT}), because this destroys another person's
 *       account and their content on one press. Nobody is exercising a right here, it is a ban or
 *       house rules, so the caller MAY state a rule per content axis, and it rides the saga.</li>
 * </ul>
 *
 * <p>Closing is a saga: {@link StartAccountDeletion} locks the account at once (sessions revoked,
 * sign-in refused) and asks the content services to purge; their confirmation, not this request,
 * deletes the account for good.
 */
public final class AccountDeletionService {

    private final StartAccountDeletion startAccountDeletion;
    private final UserRepository userRepository;
    private final RequireRole requireRole;
    private final SessionElevation sessionElevation;
    private final TransactionBoundary transactionBoundary;

    public AccountDeletionService(
                    StartAccountDeletion startAccountDeletion,
                    UserRepository userRepository,
                    RequireRole requireRole,
                    SessionElevation sessionElevation,
                    TransactionBoundary transactionBoundary) {
        this.startAccountDeletion = startAccountDeletion;
        this.userRepository = userRepository;
        this.requireRole = requireRole;
        this.sessionElevation = sessionElevation;
        this.transactionBoundary = transactionBoundary;
    }

    public sealed interface StartOutcome {

        /** The caller's own account is locked and its deletion is under way. */
        record OwnAccountClosing() implements StartOutcome {}

        /**
         * An administrator closed somebody else's account. Named apart from
         * {@link OwnAccountClosing} for whoever reads the outcome, and carrying both addresses for
         * the audit line; the wire must answer both alike.
         */
        record SomebodyElsesClosing(String user, String administrator) implements StartOutcome {}

        /** Not an address the domain can construct. */
        record InvalidEmail() implements StartOutcome {}

        record NotAnAdmin() implements StartOutcome {}

        /** Too many axes, or an axis or a rule too long to travel. */
        record InvalidPurgeChoices() implements StartOutcome {}

        /** Nobody holds the address an administrator named. */
        record NoSuchUser() implements StartOutcome {}

        /** The caller has not just re-proved themselves for this action, named as the wire spells it. */
        record StepUpRequired(String action) implements StartOutcome {}
    }

    /**
     * {@code accessToken} is the bearer token as sent, or null when there was none. {@code purge}
     * is an administrator's decision per content axis ({@code memes -> KEEP_POPULAR_ANONYMIZED:100}),
     * or null or empty for each service's default. Both the axis names and the rules stay opaque
     * here: their vocabulary belongs to the content services, and identity only ferries the map.
     */
    public StartOutcome start(String callerEmail, String accessToken, String userEmail, Map<String, String> purge) {

        Email user;
        try {
            user = Email.of(userEmail);
        } catch (IllegalArgumentException invalid) {
            return new StartOutcome.InvalidEmail();
        }

        Caller caller = callerOf(callerEmail);

        // the same account, by the same rule registration uses to decide two addresses are one
        // person: a caller whose token spells their address differently from the request is still
        // closing their OWN account, and must not be sent down the administrator's road
        boolean ownAccount = NormalizedEmail.of(user).equals(NormalizedEmail.of(caller.email()));

        if (
                ownAccount
        ) {
            return closeOwnAccount(caller, accessToken);
        }

        return closeSomebodyElses(caller, accessToken, user, purge);
    }

    /**
     * The purge map is not read here, and that is the point rather than an omission: a request to
     * be forgotten states no conditions, so there are none to parse, validate or reject. An older
     * client that still sends them is answered with the deletion it asked for.
     */
    private StartOutcome closeOwnAccount(Caller caller, String accessToken) {

        // closing an account is irreversible: a live session is not enough, the caller must have
        // just stepped up (the thief of a live session would have to pass the chain too)
        if (
                accessToken == null ||
                !sessionElevation.consume(accessToken, StepUpAction.DELETE_ACCOUNT)
        ) {
            return new StartOutcome.StepUpRequired(
                    StepUpAction.DELETE_ACCOUNT.wire());
        }

        // the CALLER's address is what travels, never the request's: the comparison accepts a
        // different spelling of the same account, and everything downstream (the lock, the saga
        // row, the fact's key) addresses the user by the exact string the account is stored under,
        // which is the one the token carries
        start(AccountClosure.requestedByOwner(caller.email()));

        return new StartOutcome.OwnAccountClosing();
    }

    private StartOutcome closeSomebodyElses(Caller caller, String accessToken, Email user, Map<String, String> purge) {

        if (
                !isAdmin(caller)
        ) {
            return new StartOutcome.NotAnAdmin();
        }

        // an over-large or over-wide purge map is a bad request, not a server fault: refused here
        // so it never becomes an unpublishable outbox row, and before the elevation is spent
        PurgeChoices choices;
        try {
            choices = purgeChoices(purge);
        } catch (IllegalArgumentException oversized) {
            return new StartOutcome.InvalidPurgeChoices();
        }

        // AFTER the role check, so somebody who may not do this at all learns only that. A stolen
        // admin session must prove itself again before destroying a stranger's account, exactly as
        // it must before granting a role.
        if (
                accessToken == null ||
                !sessionElevation.consume(accessToken, StepUpAction.ADMIN_DELETE_ACCOUNT)
        ) {
            return new StartOutcome.StepUpRequired(
                    StepUpAction.ADMIN_DELETE_ACCOUNT.wire());
        }

        // asked before the lock, and only on this road: starting a saga for an address nobody holds
        // would announce a deletion fact the portal then purges content for, and tell an
        // administrator who mistyped that it worked. (On the other road the caller's own account
        // exists by definition: their token was issued for it.)
        if (
                userRepository.findBy(user).isEmpty()
        ) {
            return new StartOutcome.NoSuchUser();
        }

        start(AccountClosure.requestedByAdministrator(user, choices));

        return new StartOutcome.SomebodyElsesClosing(
                user.value(),
                caller.email().value());
    }

    private void start(AccountClosure closure) {
        transactionBoundary.execute(() -> {
            startAccountDeletion.execute(closure);
            return null;
        });
    }

    private static PurgeChoices purgeChoices(Map<String, String> purge) {
        if (
                purge == null ||
                purge.isEmpty()
        ) {
            return PurgeChoices.serviceDefaults();
        }

        return new PurgeChoices(purge);
    }

    /** Asked about ADMIN only, so whichever refusal comes back, the caller is not one. */
    private boolean isAdmin(Caller caller) {
        try {
            requireRole.check(caller, Role.ADMIN);
            return true;
        } catch (RequireRole.NotAUser | RequireRole.NotAModerator | RequireRole.NotAnAdmin refused) {
            return false;
        }
    }

    private static Caller callerOf(String callerEmail) {
        return new Caller(
                Email.of(callerEmail));
    }
}
