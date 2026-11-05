package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.config.bruteforce.BruteForceConfig;
import com.jrobertgardzinski.security.config.mfa.ChallengeCodeConfig;
import com.jrobertgardzinski.security.domain.port.AccessTokenMint;
import com.jrobertgardzinski.security.domain.repository.AuthenticationBlockRepository;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.system.mfa.FactorRegistry;
import com.jrobertgardzinski.security.system.mfa.OpenMfaChain;
import com.jrobertgardzinski.security.system.mfa.PendingAuthenticationStore;
import com.jrobertgardzinski.security.system.mfa.PresentProof;
import com.jrobertgardzinski.security.system.password.VerifyPassword;

import java.time.Clock;

/**
 * Public assembly seam for {@link Authentication} and its MFA continuation.
 *
 * <p>{@code Authentication}, {@link ContinueAuthentication} and their collaborators keep package-
 * private constructors on purpose (internals stay hidden). This factory lives in the same package,
 * wires them, and hands back both use cases sharing one chain and one session minter, so a sign-in
 * begun by {@code Authentication} is completed by {@code ContinueAuthentication} identically.
 */
public final class AuthenticationFactory {

    /** The two halves of a sign-in: start (through link #1) and continue (through the factor chain). */
    public record AuthenticationUseCases(Authentication authentication,
                                         ContinueAuthentication continueAuthentication) {}

    private AuthenticationFactory() {
    }

    public static AuthenticationUseCases assemble(
            UserRepository userRepository,
            EmailVerificationRepository emailVerificationRepository,
            RejectedAuthenticationRepository rejectedAuthenticationRepository,
            AuthenticationBlockRepository authenticationBlockRepository,
            SessionRepository sessionRepository,
            VerifyPassword verifyPassword,
            BruteForceConfig bruteForceConfig,
            SessionTokensConfig sessionTokensConfig,
            Clock clock,
            BlockDurationPolicy blockDurationPolicy,
            AccessTokenMint accessTokenMint,
            EnrolledFactorRepository enrolledFactorRepository,
            com.jrobertgardzinski.security.system.mfa.MfaChain mfaChain,
            PendingAuthenticationStore pendingAuthenticationStore,
            OpenMfaChain openMfaChain) {

        var bruteForceGuard = new _BruteForceGuard(
                rejectedAuthenticationRepository, authenticationBlockRepository,
                clock, bruteForceConfig, blockDurationPolicy);
        var requireVerifiedEmail = new _RequireVerifiedEmail(emailVerificationRepository);
        var generateSession = new _GenerateSession(sessionRepository, clock, sessionTokensConfig, accessTokenMint);
        // no block repository here any more: a success clears this pair's failures and never a
        // placed block, which would hand the amnesty straight back
        var cleanBruteForceRecords = new _CleanBruteForceRecords(rejectedAuthenticationRepository);
        var updateBruteForceRecords = new _UpdateBruteForceRecords(rejectedAuthenticationRepository, clock);

        var authentication = new Authentication(
                bruteForceGuard, verifyPassword, userRepository, requireVerifiedEmail, generateSession,
                cleanBruteForceRecords, updateBruteForceRecords,
                enrolledFactorRepository, openMfaChain);
        var continueAuthentication = new ContinueAuthentication(
                new PresentProof(mfaChain, pendingAuthenticationStore, clock),
                generateSession,
                new _AccountStillSignsIn(userRepository, requireVerifiedEmail),
                updateBruteForceRecords);

        return new AuthenticationUseCases(authentication, continueAuthentication);
    }
}
