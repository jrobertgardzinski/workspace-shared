package com.jrobertgardzinski.security.system.federation;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.HashAlgorithmPort;
import com.jrobertgardzinski.password.domain.HashedPassword;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.domain.entity.EnrolledFactor;
import com.jrobertgardzinski.security.domain.entity.SessionTokens;
import com.jrobertgardzinski.security.domain.entity.User;
import com.jrobertgardzinski.security.domain.port.AccessTokenMint;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.PasswordlessAccountRepository;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.repository.EmailVerificationRepository;
import com.jrobertgardzinski.security.domain.repository.FederatedIdentityRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.FactorType;
import com.jrobertgardzinski.security.domain.vo.ProviderIdentity;
import com.jrobertgardzinski.security.domain.vo.SessionFamily;
import com.jrobertgardzinski.security.domain.vo.SessionTokensConfig;
import com.jrobertgardzinski.security.domain.vo.Source;
import com.jrobertgardzinski.security.system.mfa.OpenMfaChain;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Signs a caller in with an identity a provider vouched for — registration collapses into the
 * first sign-in. One account, many identities: the {@code (provider, subject)} link decides, and
 * when there is none yet, the vouched email does:
 * <ul>
 *   <li>no local account &rarr; one is created, verified from birth (the provider's word replaces
 *       our mail loop) and passwordless — its password hash is an unguessable random secret that
 *       verifies nothing, until the owner sets one through the reset flow;</li>
 *   <li>a local account whose email WE verified &rarr; the same inbox proved twice is the same
 *       person: auto-link and sign in;</li>
 *   <li>a local account never verified &rarr; a squatter may have planted it on someone else's
 *       address; the provider's proof beats the unproven password, so the account is taken over —
 *       linked and verified, the old password replaced with an unusable one, every session
 *       revoked.</li>
 * </ul>
 * An assertion whose email the provider does NOT vouch for is refused outright, and an account
 * locked by a running deletion saga refuses federated sign-in just like the password kind.
 */
public class FederatedSignIn {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final FederatedIdentityRepository federatedIdentityRepository;
    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final SessionRepository sessionRepository;
    private final HashAlgorithmPort hashAlgorithm;
    private final SessionTokensConfig config;
    private final Clock clock;
    private final AccessTokenMint accessTokenMint;
    private final PasswordlessAccountRepository passwordlessAccountRepository;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final OpenMfaChain openMfaChain;

    public FederatedSignIn(
                    FederatedIdentityRepository federatedIdentityRepository,
                    UserRepository userRepository,
                    EmailVerificationRepository emailVerificationRepository,
                    SessionRepository sessionRepository,
                    HashAlgorithmPort hashAlgorithm,
                    SessionTokensConfig config,
                    Clock clock,
                    AccessTokenMint accessTokenMint,
                    PasswordlessAccountRepository passwordlessAccountRepository,
                    EnrolledFactorRepository enrolledFactorRepository,
                    OpenMfaChain openMfaChain) {
        this.federatedIdentityRepository = federatedIdentityRepository;
        this.userRepository = userRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.sessionRepository = sessionRepository;
        this.hashAlgorithm = hashAlgorithm;
        this.config = config;
        this.clock = clock;
        this.accessTokenMint = accessTokenMint;
        this.passwordlessAccountRepository = passwordlessAccountRepository;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.openMfaChain = openMfaChain;
    }

    public sealed interface Result {

        record SignedIn(SessionTokens session) implements Result {}

        /**
         * The provider login proved link #1, but the account has enrolled factors: no session yet.
         * The client presents proofs against {@code ticket} (the same {@code /authenticate/factor}
         * endpoint the password chain uses) until the chain completes.
         */
        record MfaRequired(String ticket, FactorType nextFactor, String challengeData) implements Result {}

        /** The provider did not vouch for the address, so it proves nothing about an inbox. */
        record EmailNotVouched() implements Result {}

        /** The account is being deleted. */
        record AccountClosing() implements Result {}
    }

    public Result execute(ProviderIdentity identity, Source source) {

        if (
                !identity.emailVerified()
        ) {
            return new Result.EmailNotVouched();
        }

        Optional<Email> linkedEmail = federatedIdentityRepository.findUserBy(
                identity.provider(),
                identity.subject());

        // a link pointing at a user that no longer exists (e.g. the email changed locally) is stale
        // — fall through to the email rules and re-link
        boolean linked =
                linkedEmail.isPresent() &&
                userRepository.findBy(linkedEmail.get()).isPresent();

        // The lock is checked BEFORE claimByEmail, not after. claimByEmail WRITES — it can wipe the
        // password, revoke every session, mark the address verified and link the provider identity —
        // and it used to run first, so an account with a running deletion saga got a fresh provider
        // identity welded onto it moments before it vanished (and, if the saga compensated, came back
        // in a state nobody asked for). Sign-in was refused either way; the side effects were not.
        // The address claimByEmail would act on is exactly the one it returns, so asking about it up
        // front costs nothing: for an unknown address isPendingDeletion is simply false.
        Email account;
        if (
                linked
        ) {
            account = linkedEmail.get();
        } else {
            account = identity.email();
        }

        if (
                userRepository.isPendingDeletion(account)
        ) {
            return new Result.AccountClosing();
        }

        if (
                !linked
        ) {
            claimByEmail(identity);
        }

        // the provider login is link #1; if the account has enrolled factors, they must be passed
        // too before a session — the same chain the password sign-in walks
        List<EnrolledFactor> factors = enrolledFactorRepository.findByUser(account);

        if (
                factors.isEmpty()
        ) {
            return new Result.SignedIn(
                    sessionRepository.create(
                            SessionTokens.createFor(account, config, clock, accessTokenMint),
                            SessionFamily.start()));
        }

        OpenMfaChain.Result openMfaChainResult = openMfaChain.open(account, factors, source);

        return new Result.MfaRequired(
                openMfaChainResult.ticket(),
                openMfaChainResult.nextFactor(),
                openMfaChainResult.challengeData());
    }

    /**
     * Applies the e-mail rules to the vouched address and links the provider identity to it. Writes
     * only; the address it acts on is {@code identity.email()}, which the caller already holds.
     */
    private void claimByEmail(ProviderIdentity identity) {

        Email email = identity.email();
        Optional<User> existing = userRepository.findBy(email);

        if (
                existing.isEmpty()
        ) {
            userRepository.save(
                    new User(
                            email,
                            unusablePassword()));
            emailVerificationRepository.markVerified(email);
            // born through the provider, no password of its own
            passwordlessAccountRepository.setPasswordless(email, true);
        } else if (
                !emailVerificationRepository.isVerified(email)
        ) {
            // the squatter case: the provider's proof of the inbox beats the unproven password
            userRepository.updatePassword(
                    email,
                    unusablePassword());
            sessionRepository.revokeAllSessions(email);
            emailVerificationRepository.markVerified(email);
            // the wiped password no longer counts
            passwordlessAccountRepository.setPasswordless(email, true);
        }

        // the auto-link case (existing, verified) keeps its password — it stays a password account
        federatedIdentityRepository.link(
                identity.provider(),
                identity.subject(),
                email);
    }

    /**
     * A hash that verifies no password anyone can type: a discarded 256-bit secret. Real Argon2
     * work on purpose — a malformed sentinel could make the verifier throw instead of refuse.
     */
    private HashedPassword unusablePassword() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return hashAlgorithm.hash(PlaintextPassword.of(Base64.getEncoder().encodeToString(secret)));
    }
}
