package com.jrobertgardzinski.security.application.federation;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.domain.vo.ProviderIdentity;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.system.federation.FederatedSignIn;

/**
 * The bridge for signing in with an identity a provider vouched for. The framework side has done
 * the OAuth dance and hands over what the provider asserted, as plain strings; this builds the
 * domain's identity from it, runs the use case, and answers in plain values again.
 */
public final class FederationService {

    private final FederatedSignIn federatedSignIn;
    private final TransactionBoundary transactionBoundary;

    public FederationService(
                    FederatedSignIn federatedSignIn,
                    TransactionBoundary transactionBoundary) {
        this.federatedSignIn = federatedSignIn;
        this.transactionBoundary = transactionBoundary;
    }

    public sealed interface SignInOutcome {

        record SignedIn(String accessToken, String refreshToken) implements SignInOutcome {}

        /**
         * The provider login proved link #1, but the account has enrolled factors: no session yet.
         * The client presents proofs against {@code ticket}, exactly as after a password.
         */
        record MfaRequired(String ticket, String nextFactor, String challengeData) implements SignInOutcome {}

        /** The provider did not vouch for the address, so it proves nothing about an inbox. */
        record EmailNotVouched() implements SignInOutcome {}

        /** The account is being deleted. */
        record AccountClosing() implements SignInOutcome {}

        /**
         * What the provider calls an e-mail address is not one the domain can construct. What a
         * provider sends is not ours to trust, and this is its mistake, not a fault of the service.
         */
        record UnreadableEmail() implements SignInOutcome {}
    }

    /**
     * {@code ipAddress} and {@code userAgent} say where the provider's callback came from: if a
     * factor chain opens, its wrong proofs are charged to that address against this account,
     * exactly as after a password sign-in.
     */
    public SignInOutcome signIn(
                    String provider,
                    String subject,
                    String email,
                    boolean emailVerified,
                    String ipAddress,
                    String userAgent) {

        ProviderIdentity identity;
        try {
            identity = new ProviderIdentity(
                    provider,
                    subject,
                    Email.of(email),
                    emailVerified);
        } catch (IllegalArgumentException unreadable) {
            return new SignInOutcome.UnreadableEmail();
        }

        Source source = new Source(
                new IpAddress(ipAddress),
                userAgent);

        FederatedSignIn.Result result = transactionBoundary.execute(
                () -> federatedSignIn.execute(identity, source));

        return switch (result) {
            case FederatedSignIn.Result.SignedIn signedIn ->
                    new SignInOutcome.SignedIn(
                            signedIn.session().plainAccessToken(),
                            signedIn.session().plainRefreshToken());
            case FederatedSignIn.Result.MfaRequired mfaRequired ->
                    new SignInOutcome.MfaRequired(
                            mfaRequired.ticket(),
                            mfaRequired.nextFactor().value(),
                            mfaRequired.challengeData());
            case FederatedSignIn.Result.EmailNotVouched emailNotVouched ->
                    new SignInOutcome.EmailNotVouched();
            case FederatedSignIn.Result.AccountClosing accountClosing ->
                    new SignInOutcome.AccountClosing();
        };
    }
}
