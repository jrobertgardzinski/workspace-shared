package com.jrobertgardzinski.security.application.verification;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.InvalidEmailException;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import com.jrobertgardzinski.security.system.verification.RequestEmailVerification;
import com.jrobertgardzinski.security.system.verification.VerifyEmail;

/**
 * The bridge for proving that an address is one's own: asking for the link, and presenting the
 * token it carried. Strings in, plain values out.
 */
public final class VerificationService {

    private final RequestEmailVerification requestEmailVerification;
    private final VerifyEmail verifyEmail;
    private final SourceThrottle throttle;
    private final TransactionBoundary transactionBoundary;

    public VerificationService(
                    RequestEmailVerification requestEmailVerification,
                    VerifyEmail verifyEmail,
                    SourceThrottle throttle,
                    TransactionBoundary transactionBoundary) {
        this.requestEmailVerification = requestEmailVerification;
        this.verifyEmail = verifyEmail;
        this.throttle = throttle;
        this.transactionBoundary = transactionBoundary;
    }

    /**
     * Every variant but {@link Throttled} is named for whoever reads the outcome; the wire must
     * answer them alike, or it tells a stranger which addresses are registered and verified.
     */
    public sealed interface RequestOutcome {

        record LinkSent() implements RequestOutcome {}

        record AlreadyVerified() implements RequestOutcome {}

        /** Not an address the domain can construct, so nobody's: nothing was looked up or mailed. */
        record UnreadableEmail() implements RequestOutcome {}

        record Throttled(long retryAfterSeconds) implements RequestOutcome {}
    }

    public sealed interface VerifyOutcome {

        record Verified(String email) implements VerifyOutcome {}

        record TokenNotFound() implements VerifyOutcome {}

        record TokenExpired() implements VerifyOutcome {}

        /** Missing or blank: a token that cannot even be constructed is simply not a valid token. */
        record UnreadableToken() implements VerifyOutcome {}
    }

    public RequestOutcome requestLink(String email, String ipAddress) {

        SourceThrottle.Decision decision = throttle.check(
                new IpAddress(ipAddress));

        if (
                !decision.allowed()
        ) {
            return new RequestOutcome.Throttled(
                    decision.retryAfterSeconds());
        }

        Email address;
        try {
            address = Email.of(email);
        } catch (InvalidEmailException malformed) {
            return new RequestOutcome.UnreadableEmail();
        }

        RequestEmailVerification.Result result = transactionBoundary.execute(
                () -> requestEmailVerification.execute(address));

        return switch (result) {
            case RequestEmailVerification.Result.LinkSent linkSent ->
                    new RequestOutcome.LinkSent();
            case RequestEmailVerification.Result.AlreadyVerified alreadyVerified ->
                    new RequestOutcome.AlreadyVerified();
        };
    }

    public VerifyOutcome verify(String token) {

        VerificationToken verificationToken;
        try {
            verificationToken = new VerificationToken(token);
        } catch (IllegalArgumentException missingOrBlank) {
            return new VerifyOutcome.UnreadableToken();
        }

        VerifyEmail.Result result = transactionBoundary.execute(() -> verifyEmail.execute(verificationToken));

        return switch (result) {
            case VerifyEmail.Result.Verified verified ->
                    new VerifyOutcome.Verified(
                            verified.email().value());
            case VerifyEmail.Result.TokenNotFound tokenNotFound ->
                    new VerifyOutcome.TokenNotFound();
            case VerifyEmail.Result.TokenExpired tokenExpired ->
                    new VerifyOutcome.TokenExpired();
        };
    }
}
