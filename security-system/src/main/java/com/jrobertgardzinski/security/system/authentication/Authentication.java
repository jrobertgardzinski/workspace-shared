package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.entity.AuthenticationBlock;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.AuthenticationRequest;
import com.jrobertgardzinski.security.domain.vo.AttemptedAccount;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.system.mfa.OpenMfaChain;
import com.jrobertgardzinski.security.system.password.VerifyPassword;

import java.util.List;
import java.util.Optional;

public class Authentication {

    private final _BruteForceGuard bruteForceGuard;
    private final VerifyPassword verifyPassword;
    private final UserRepository userRepository;
    private final _RequireVerifiedEmail requireVerifiedEmail;
    private final _GenerateSession generateSession;
    private final _CleanBruteForceRecords cleanBruteForceRecords;
    private final _UpdateBruteForceRecords updateBruteForceRecords;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final OpenMfaChain openMfaChain;

    Authentication(
                    _BruteForceGuard bruteForceGuard,
                    VerifyPassword verifyPassword,
                    UserRepository userRepository,
                    _RequireVerifiedEmail requireVerifiedEmail,
                    _GenerateSession generateSession,
                    _CleanBruteForceRecords cleanBruteForceRecords,
                    _UpdateBruteForceRecords updateBruteForceRecords,
                    EnrolledFactorRepository enrolledFactorRepository,
                    OpenMfaChain openMfaChain) {
        this.bruteForceGuard = bruteForceGuard;
        this.verifyPassword = verifyPassword;
        this.userRepository = userRepository;
        this.requireVerifiedEmail = requireVerifiedEmail;
        this.generateSession = generateSession;
        this.cleanBruteForceRecords = cleanBruteForceRecords;
        this.updateBruteForceRecords = updateBruteForceRecords;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.openMfaChain = openMfaChain;
    }

    public sealed interface Result {

        record Authenticated(SessionTokens session) implements Result {}

        record Rejected() implements Result {}

        record Blocked(AuthenticationBlock authenticationBlock) implements Result {}

        /** Credentials were correct, but the e-mail address has not been verified yet. */
        record EmailNotVerified() implements Result {}

        /**
         * Link #1 (the password) passed, but the user has enrolled factors: no session yet. The
         * client presents proofs against {@code ticket} until the chain completes; the first
         * factor's challenge (if any) has already been issued.
         */
        record MfaRequired(String ticket, FactorType nextFactor, String challengeData) implements Result {}
    }

    public Result execute(AuthenticationRequest request) {

        Source source = request.source();

        // failures are charged to the PAIR: this address against this account. Either half alone is
        // walkable-around — by account, anyone locks out a victim from anywhere; by address, three
        // typos take a whole office down with them.
        LockoutSubject subject = new LockoutSubject(
                source,
                AttemptedAccount.of(request.email()));

        Optional<AuthenticationBlock> authenticationBlock = bruteForceGuard.execute(subject);

        if (
                authenticationBlock.isPresent()
        ) {
            return new Result.Blocked(
                    authenticationBlock.get());
        }

        VerifyPassword.Result verifyPasswordResult = verifyPassword.verify(
                request.email(),
                request.plaintextPassword());

        if (
                !verifyPasswordResult.errors().isEmpty()
        ) {
            // a miss is written down, whichever half was wrong
            updateBruteForceRecords.execute(subject);
            return new Result.Rejected();
        }

        // the STORED spelling from here on, not the one that was typed: everything after this — the
        // verified-address check, the factor lookup, the session and every lookup the token later
        // drives — is keyed by an address, and an account that can be reached by two spellings must
        // not be half-reached by one of them
        Email email = verifyPasswordResult.user().email();

        if (
                userRepository.isPendingDeletion(email)
        ) {
            // an account locked by a running deletion saga behaves like a wrong password
            updateBruteForceRecords.execute(subject);
            return new Result.Rejected();
        }

        // correct credentials are not a guessing signal, so no brute-force update here
        if (
                !requireVerifiedEmail.isVerified(email)
        ) {
            return new Result.EmailNotVerified();
        }

        // The person got their own password right, so THEIR earlier misses stop counting — this
        // pair's and nobody else's. Clearing the whole address (what this used to do) made one
        // known-good credential an amnesty for everything that address was trying; clearing nothing
        // at all (what it did between P18 poz. 6 and now) left an office locked out over one
        // person's typos. A placed BLOCK is untouched either way: it expires on its own timer.
        cleanBruteForceRecords.execute(subject);

        // link #1 passed. With enrolled factors the session waits until the chain completes; with
        // none it is minted now (unchanged single-factor sign-in).
        List<EnrolledFactor> factors = enrolledFactorRepository.findByUser(email);

        if (
                factors.isEmpty()
        ) {
            return new Result.Authenticated(
                    generateSession.create(email));
        }

        OpenMfaChain.Result openMfaChainResult = openMfaChain.open(email, factors, source);

        return new Result.MfaRequired(
                openMfaChainResult.ticket(),
                openMfaChainResult.nextFactor(),
                openMfaChainResult.challengeData());
    }
}
