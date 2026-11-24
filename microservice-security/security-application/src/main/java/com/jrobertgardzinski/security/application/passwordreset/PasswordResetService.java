package com.jrobertgardzinski.security.application.passwordreset;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.InvalidEmailException;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.BrokenRule;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.token.PasswordResetToken;
import com.jrobertgardzinski.security.system.passwordreset.RequestPasswordReset;
import com.jrobertgardzinski.security.system.passwordreset.ResetPassword;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;

import java.util.List;

/**
 * The bridge for a forgotten password: asking for the link, and setting a new password with the
 * token it carried. Strings in, plain values out.
 */
public final class PasswordResetService {

    private final RequestPasswordReset requestPasswordReset;
    private final ResetPassword resetPassword;
    private final SourceThrottle throttle;
    private final TransactionBoundary transactionBoundary;

    public PasswordResetService(
                    RequestPasswordReset requestPasswordReset,
                    ResetPassword resetPassword,
                    SourceThrottle throttle,
                    TransactionBoundary transactionBoundary) {
        this.requestPasswordReset = requestPasswordReset;
        this.resetPassword = resetPassword;
        this.throttle = throttle;
        this.transactionBoundary = transactionBoundary;
    }

    /**
     * Every variant but {@link Throttled} is named for whoever reads the outcome; the wire must
     * answer them alike, or it tells a stranger which addresses are registered.
     */
    public sealed interface RequestOutcome {

        record LinkSent() implements RequestOutcome {}

        record UserNotFound() implements RequestOutcome {}

        /** Not an address the domain can construct, so nobody's: nothing was looked up or mailed. */
        record UnreadableEmail() implements RequestOutcome {}

        record Throttled(long retryAfterSeconds) implements RequestOutcome {}
    }

    public sealed interface ResetOutcome {

        record PasswordReset(String email) implements ResetOutcome {}

        /** The rules the new password broke; the token is NOT spent on a refused password. */
        record Rejected(List<BrokenRule> passwordErrors) implements ResetOutcome {}

        record TokenNotFound() implements ResetOutcome {}

        record TokenExpired() implements ResetOutcome {}

        /** The link was good, and the account it was issued for is gone. */
        record UserNotFound() implements ResetOutcome {}

        /** Missing or blank: a token that cannot even be constructed is simply not a valid token. */
        record UnreadableToken() implements ResetOutcome {}
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

        RequestPasswordReset.Result result = transactionBoundary.execute(
                () -> requestPasswordReset.execute(address));

        return switch (result) {
            case RequestPasswordReset.Result.LinkSent linkSent ->
                    new RequestOutcome.LinkSent();
            case RequestPasswordReset.Result.UserNotFound userNotFound ->
                    new RequestOutcome.UserNotFound();
        };
    }

    public ResetOutcome reset(String token, String newPassword) {

        PasswordResetToken resetToken;
        try {
            resetToken = new PasswordResetToken(token);
        } catch (IllegalArgumentException missingOrBlank) {
            return new ResetOutcome.UnreadableToken();
        }

        // the password is a candidate, not an object: one that does not construct is a broken rule
        // the judge reports
        ResetPassword.Result result = transactionBoundary.execute(
                () -> resetPassword.execute(
                        resetToken,
                        () -> PlaintextPassword.of(newPassword)));

        return switch (result) {
            case ResetPassword.Result.PasswordReset passwordReset ->
                    new ResetOutcome.PasswordReset(
                            passwordReset.email().value());
            case ResetPassword.Result.Rejected rejected ->
                    new ResetOutcome.Rejected(
                            BrokenRule.ofPassword(rejected.passwordErrors(), rejected.policy()));
            case ResetPassword.Result.TokenNotFound tokenNotFound ->
                    new ResetOutcome.TokenNotFound();
            case ResetPassword.Result.TokenExpired tokenExpired ->
                    new ResetOutcome.TokenExpired();
            case ResetPassword.Result.UserNotFound userNotFound ->
                    new ResetOutcome.UserNotFound();
        };
    }
}
