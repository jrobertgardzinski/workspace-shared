package com.jrobertgardzinski.security.application.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.system.authentication.Authentication;
import com.jrobertgardzinski.security.system.authentication.ContinueAuthentication;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;

import java.time.LocalDateTime;

/**
 * The bridge for signing in: the password step, and the factor steps a sign-in with enrolled
 * factors goes through afterwards. The framework side hands over plain strings; this builds the
 * domain's request from them, runs the use case, and answers in plain values again.
 *
 * <p>Both steps share ONE per-source window. Per ticket the attempts are capped, but tickets are
 * not: somebody holding the password can open one after another (a correct password clears the
 * brute-force count rather than adding to it) and buy five guesses at the second factor each time,
 * and every ticket for a code factor mails the victim another code. So the loop is bounded by the
 * source, whichever half of it is being spun.
 */
public final class AuthenticationService {

    private final Authentication authentication;
    private final ContinueAuthentication continueAuthentication;
    private final SourceThrottle throttle;
    private final TransactionBoundary transactionBoundary;

    public AuthenticationService(
                    Authentication authentication,
                    ContinueAuthentication continueAuthentication,
                    SourceThrottle throttle,
                    TransactionBoundary transactionBoundary) {
        this.authentication = authentication;
        this.continueAuthentication = continueAuthentication;
        this.throttle = throttle;
        this.transactionBoundary = transactionBoundary;
    }

    public sealed interface Outcome {

        record Authenticated(String accessToken, String refreshToken) implements Outcome {}

        record Rejected() implements Outcome {}

        /**
         * The address or the password is not one the domain can construct. To the caller it is not
         * a different kind of failure, an address that cannot exist owns no account, but it cost no
         * lookup and therefore no brute-force count: there is no account here to guess at.
         */
        record UnreadableCredentials() implements Outcome {}

        /** Credentials were correct, but the e-mail address has not been verified yet. */
        record EmailNotVerified() implements Outcome {}

        /** Too many failures for this account from this source; the block stands until then. */
        record Blocked(LocalDateTime until) implements Outcome {}

        /**
         * The password passed, but the user has enrolled factors: no session yet. The client
         * presents proofs against {@code ticket}; the first factor's challenge (if any) is out.
         */
        record MfaRequired(String ticket, String nextFactor, String challengeData) implements Outcome {}

        /** This source has spent its window; no credential was looked at. */
        record Throttled(long retryAfterSeconds) implements Outcome {}
    }

    public sealed interface FactorOutcome {

        /** The chain completed: the same session a single-factor sign-in gets. */
        record Authenticated(String accessToken, String refreshToken) implements FactorOutcome {}

        /** The proof passed and another factor is due, against the same ticket. */
        record NextFactor(String ticket, String nextFactor, String challengeData) implements FactorOutcome {}

        record WrongProof(int attemptsLeft) implements FactorOutcome {}

        /** The ticket ran out of tries and is gone; the sign-in starts over. */
        record TooManyAttempts() implements FactorOutcome {}

        /** Unknown, expired, or its account stopped signing in while the chain was walked. */
        record InvalidTicket() implements FactorOutcome {}

        /** The ticket or the proof is missing or blank: nothing to present, nothing to judge. */
        record MissingTicketOrProof() implements FactorOutcome {}

        /** This source has spent its window; no proof was looked at. */
        record Throttled(long retryAfterSeconds) implements FactorOutcome {}
    }

    public Outcome authenticate(String email, String password, String ipAddress, String userAgent) {

        Source source = new Source(
                new IpAddress(ipAddress),
                userAgent);

        // the per-account guard counts FAILURES and a correct password clears them, so it does not
        // bound how many attempts one source may start — this does
        SourceThrottle.Decision decision = throttle.check(
                source.ipAddress());

        if (
                !decision.allowed()
        ) {
            return new Outcome.Throttled(
                    decision.retryAfterSeconds());
        }

        AuthenticationRequest request;
        try {
            request = new AuthenticationRequest(
                    source,
                    Email.of(email),
                    PlaintextPassword.of(password));
        } catch (IllegalArgumentException unreadable) {
            return new Outcome.UnreadableCredentials();
        }

        Authentication.Result result = transactionBoundary.execute(() -> authentication.execute(request));

        return switch (result) {
            case Authentication.Result.Authenticated authenticated ->
                    new Outcome.Authenticated(
                            authenticated.session().plainAccessToken(),
                            authenticated.session().plainRefreshToken());
            case Authentication.Result.Rejected rejected ->
                    new Outcome.Rejected();
            case Authentication.Result.EmailNotVerified notVerified ->
                    new Outcome.EmailNotVerified();
            case Authentication.Result.Blocked blocked ->
                    new Outcome.Blocked(
                            blocked.authenticationBlock().expiryDate());
            case Authentication.Result.MfaRequired mfaRequired ->
                    new Outcome.MfaRequired(
                            mfaRequired.ticket(),
                            mfaRequired.nextFactor().value(),
                            mfaRequired.challengeData());
        };
    }

    public FactorOutcome presentFactor(String ticket, String proof, String ipAddress) {

        SourceThrottle.Decision decision = throttle.check(
                new IpAddress(ipAddress));

        if (
                !decision.allowed()
        ) {
            return new FactorOutcome.Throttled(
                    decision.retryAfterSeconds());
        }

        if (
                isMissing(ticket) ||
                isMissing(proof)
        ) {
            return new FactorOutcome.MissingTicketOrProof();
        }

        ContinueAuthentication.Result result = transactionBoundary.execute(
                () -> continueAuthentication.execute(ticket, proof));

        return switch (result) {
            case ContinueAuthentication.Result.Completed completed ->
                    new FactorOutcome.Authenticated(
                            completed.session().plainAccessToken(),
                            completed.session().plainRefreshToken());
            case ContinueAuthentication.Result.NextFactor nextFactor ->
                    new FactorOutcome.NextFactor(
                            ticket,
                            nextFactor.type().value(),
                            nextFactor.challengeData());
            case ContinueAuthentication.Result.WrongProof wrongProof ->
                    new FactorOutcome.WrongProof(
                            wrongProof.attemptsLeft());
            case ContinueAuthentication.Result.TooManyAttempts tooManyAttempts ->
                    new FactorOutcome.TooManyAttempts();
            case ContinueAuthentication.Result.InvalidTicket invalidTicket ->
                    new FactorOutcome.InvalidTicket();
        };
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank();
    }
}
