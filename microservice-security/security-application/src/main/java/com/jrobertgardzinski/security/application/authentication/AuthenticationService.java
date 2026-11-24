package com.jrobertgardzinski.security.application.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.system.authentication.Authentication;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;

import java.time.LocalDateTime;

/**
 * The bridge for signing in with a password. The framework side hands over plain strings; this
 * builds the domain's request from them, runs the use case, and answers in plain values again.
 */
public final class AuthenticationService {

    private final Authentication authentication;
    private final SourceThrottle throttle;
    private final TransactionBoundary transactionBoundary;

    public AuthenticationService(
                    Authentication authentication,
                    SourceThrottle throttle,
                    TransactionBoundary transactionBoundary) {
        this.authentication = authentication;
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
}
