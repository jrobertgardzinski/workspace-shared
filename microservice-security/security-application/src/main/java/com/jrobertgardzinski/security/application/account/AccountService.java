package com.jrobertgardzinski.security.application.account;

import com.jrobertgardzinski.email.config.EmailPolicy;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.BrokenRule;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.port.RegistrationNoticeNotifier;
import com.jrobertgardzinski.security.domain.vo.Caller;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.StepUpAction;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.security.system.account.ChangePassword;
import com.jrobertgardzinski.security.system.account.ConfirmEmailChange;
import com.jrobertgardzinski.security.system.account.RequestEmailChange;
import com.jrobertgardzinski.security.system.mfa.SessionElevation;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;

import java.util.List;

/**
 * The bridge for what a signed-in person changes about their own account: the password and the
 * address. Strings in, plain values out.
 */
public final class AccountService {

    private final ChangePassword changePassword;
    private final RequestEmailChange requestEmailChange;
    private final ConfirmEmailChange confirmEmailChange;
    private final RegistrationNoticeNotifier registrationNoticeNotifier;
    private final SessionElevation sessionElevation;
    private final EmailPolicy emailPolicy;
    private final SourceThrottle changePasswordThrottle;
    private final TransactionBoundary transactionBoundary;

    public AccountService(
                    ChangePassword changePassword,
                    RequestEmailChange requestEmailChange,
                    ConfirmEmailChange confirmEmailChange,
                    RegistrationNoticeNotifier registrationNoticeNotifier,
                    SessionElevation sessionElevation,
                    EmailPolicy emailPolicy,
                    SourceThrottle changePasswordThrottle,
                    TransactionBoundary transactionBoundary) {
        this.changePassword = changePassword;
        this.requestEmailChange = requestEmailChange;
        this.confirmEmailChange = confirmEmailChange;
        this.registrationNoticeNotifier = registrationNoticeNotifier;
        this.sessionElevation = sessionElevation;
        this.emailPolicy = emailPolicy;
        this.changePasswordThrottle = changePasswordThrottle;
        this.transactionBoundary = transactionBoundary;
    }

    public sealed interface ChangePasswordOutcome {

        record Changed() implements ChangePasswordOutcome {}

        record WrongPassword() implements ChangePasswordOutcome {}

        /** A live token, and no account under its address: deleted while the token still worked. */
        record AccountGone() implements ChangePasswordOutcome {}

        /** The rules the new password broke. */
        record Rejected(List<BrokenRule> passwordErrors) implements ChangePasswordOutcome {}

        /** The current or the new password is missing or blank: nothing to check, nothing to set. */
        record MissingPassword() implements ChangePasswordOutcome {}

        record Throttled(long retryAfterSeconds) implements ChangePasswordOutcome {}
    }

    public sealed interface RequestEmailChangeOutcome {

        record Requested() implements RequestEmailChangeOutcome {}

        /** Not an address the domain can construct. */
        record InvalidEmail() implements RequestEmailChangeOutcome {}

        /** The caller has not just re-proved themselves for this action, named as the wire spells it. */
        record StepUpRequired(String action) implements RequestEmailChangeOutcome {}

        /** The rules the new address broke. */
        record Rejected(List<BrokenRule> emailErrors) implements RequestEmailChangeOutcome {}

        /**
         * Somebody holds the address. Named apart from {@link Requested} for whoever reads the
         * outcome; the wire must answer both alike. The address owner has been told by mail.
         */
        record EmailTaken() implements RequestEmailChangeOutcome {}
    }

    public sealed interface ConfirmEmailChangeOutcome {

        record EmailChanged(String newEmail) implements ConfirmEmailChangeOutcome {}

        /** The token was good and the world moved: somebody registered the address meanwhile. */
        record EmailTaken() implements ConfirmEmailChangeOutcome {}

        record TokenNotFound() implements ConfirmEmailChangeOutcome {}

        record TokenExpired() implements ConfirmEmailChangeOutcome {}

        /** The account is being deleted; a move now would hide it from the deletion. */
        record AccountClosing() implements ConfirmEmailChangeOutcome {}

        /** Missing or blank: a token that cannot even be constructed is simply not a valid token. */
        record UnreadableToken() implements ConfirmEmailChangeOutcome {}
    }

    public ChangePasswordOutcome changePassword(
                    String callerEmail,
                    String currentPassword,
                    String newPassword,
                    String ipAddress) {

        // Authorized is not the same as unlimited. Checking the current password runs a full hash
        // and answers whether the guess was right, so with a stolen access token this is a password
        // oracle — and a hit is a takeover, because changing the password revokes every session
        SourceThrottle.Decision decision = changePasswordThrottle.check(
                new IpAddress(ipAddress));

        if (
                !decision.allowed()
        ) {
            return new ChangePasswordOutcome.Throttled(
                    decision.retryAfterSeconds());
        }

        if (
                isMissing(currentPassword) ||
                isMissing(newPassword)
        ) {
            return new ChangePasswordOutcome.MissingPassword();
        }

        Caller caller = callerOf(callerEmail);

        ChangePassword.Result result = transactionBoundary.execute(
                () -> changePassword.execute(
                        caller,
                        () -> PlaintextPassword.of(currentPassword),
                        () -> PlaintextPassword.of(newPassword)));

        return switch (result) {
            case ChangePassword.Result.Changed changed ->
                    new ChangePasswordOutcome.Changed();
            case ChangePassword.Result.WrongPassword wrongPassword ->
                    new ChangePasswordOutcome.WrongPassword();
            case ChangePassword.Result.AccountGone accountGone ->
                    new ChangePasswordOutcome.AccountGone();
            case ChangePassword.Result.Rejected rejected ->
                    new ChangePasswordOutcome.Rejected(
                            BrokenRule.ofPassword(rejected.passwordErrors(), rejected.policy()));
        };
    }

    /** {@code accessToken} is the bearer token as sent, or null when there was none. */
    public RequestEmailChangeOutcome requestEmailChange(String callerEmail, String accessToken, String newEmail) {

        Caller caller = callerOf(callerEmail);

        // the address is read BEFORE the elevation is spent: the elevation is one-shot, and a typo
        // in the new address used to cost the whole step-up chain and then answer "invalid"
        Email newAddress;
        try {
            newAddress = Email.of(newEmail);
        } catch (IllegalArgumentException invalid) {
            return new RequestEmailChangeOutcome.InvalidEmail();
        }

        // moving the address moves the account, and the confirmation lands in the NEW mailbox — so
        // a thief with a live session could walk off with the whole account and the owner would
        // learn about it from a notice. Guarded where the change STARTS; the confirmation itself
        // still needs the token mailed to that new address.
        if (
                accessToken == null ||
                !sessionElevation.consume(accessToken, StepUpAction.CHANGE_EMAIL)
        ) {
            return new RequestEmailChangeOutcome.StepUpRequired(
                    StepUpAction.CHANGE_EMAIL.wire());
        }

        RequestEmailChange.Result result = transactionBoundary.execute(
                () -> requestEmailChange.execute(caller, newAddress));

        return switch (result) {
            case RequestEmailChange.Result.Requested requested ->
                    new RequestEmailChangeOutcome.Requested();
            // the policy's refusal is NOT quiet: it is about the address the caller typed, not
            // about who else holds it, so it says which rule was broken — exactly as registration
            case RequestEmailChange.Result.Rejected rejected ->
                    new RequestEmailChangeOutcome.Rejected(
                            BrokenRule.ofEmail(rejected.emailErrors(), emailPolicy));
            case RequestEmailChange.Result.EmailTaken emailTaken -> {
                // quiet refusal: the caller sees a fresh request; the address owner is told by mail
                transactionBoundary.execute(() -> {
                    registrationNoticeNotifier.sendAlreadyRegistered(newAddress);
                    return null;
                });
                yield new RequestEmailChangeOutcome.EmailTaken();
            }
        };
    }

    public ConfirmEmailChangeOutcome confirmEmailChange(String token) {

        VerificationToken changeToken;
        try {
            changeToken = new VerificationToken(token);
        } catch (IllegalArgumentException missingOrBlank) {
            return new ConfirmEmailChangeOutcome.UnreadableToken();
        }

        // nobody else is told: the rest of the estate keys a person's rows on their id, and shows
        // the address by asking this service
        ConfirmEmailChange.Result result = transactionBoundary.execute(() -> confirmEmailChange.execute(changeToken));

        return switch (result) {
            case ConfirmEmailChange.Result.EmailChanged emailChanged ->
                    new ConfirmEmailChangeOutcome.EmailChanged(
                            emailChanged.newEmail().value());
            case ConfirmEmailChange.Result.EmailTaken emailTaken ->
                    new ConfirmEmailChangeOutcome.EmailTaken();
            case ConfirmEmailChange.Result.TokenNotFound tokenNotFound ->
                    new ConfirmEmailChangeOutcome.TokenNotFound();
            case ConfirmEmailChange.Result.TokenExpired tokenExpired ->
                    new ConfirmEmailChangeOutcome.TokenExpired();
            case ConfirmEmailChange.Result.AccountClosing accountClosing ->
                    new ConfirmEmailChangeOutcome.AccountClosing();
        };
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    private static Caller callerOf(String callerEmail) {
        return new Caller(
                Email.of(callerEmail));
    }
}
