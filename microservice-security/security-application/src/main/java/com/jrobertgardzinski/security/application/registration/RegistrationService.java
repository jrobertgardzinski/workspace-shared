package com.jrobertgardzinski.security.application.registration;

import com.jrobertgardzinski.email.config.EmailPolicy;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.password.domain.PlaintextPassword;
import com.jrobertgardzinski.security.application.BrokenRule;
import com.jrobertgardzinski.security.application.TransactionBoundary;
import com.jrobertgardzinski.security.domain.port.RegistrationNoticeNotifier;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.IpAddress;
import com.jrobertgardzinski.security.system.registration.Register;
import com.jrobertgardzinski.security.system.throttle.SourceThrottle;
import com.jrobertgardzinski.security.system.verification.RequestEmailVerification;

import java.util.List;

/**
 * The bridge for registration. The framework side hands over what it has, plain strings; this
 * builds the domain's objects from them, runs the use cases, and answers in plain values again.
 * Nothing above this class imports the domain to register somebody.
 */
public final class RegistrationService {

    private final Register register;
    private final RequestEmailVerification requestEmailVerification;
    private final UserRepository userRepository;
    private final RegistrationNoticeNotifier registrationNoticeNotifier;
    private final EmailPolicy emailPolicy;
    private final SourceThrottle throttle;
    private final TransactionBoundary transactionBoundary;

    public RegistrationService(
                    Register register,
                    RequestEmailVerification requestEmailVerification,
                    UserRepository userRepository,
                    RegistrationNoticeNotifier registrationNoticeNotifier,
                    EmailPolicy emailPolicy,
                    SourceThrottle throttle,
                    TransactionBoundary transactionBoundary) {
        this.register = register;
        this.requestEmailVerification = requestEmailVerification;
        this.userRepository = userRepository;
        this.registrationNoticeNotifier = registrationNoticeNotifier;
        this.emailPolicy = emailPolicy;
        this.throttle = throttle;
        this.transactionBoundary = transactionBoundary;
    }

    public sealed interface Outcome {

        /** A new account, and a verification link on its way: sign-in waits for a verified address. */
        record Registered() implements Outcome {}

        /**
         * The address already holds an account. Named apart from {@link Registered} for whoever
         * reads the outcome; the wire must answer both alike, or it confirms that an account
         * exists. The address owner has been told by mail.
         */
        record EmailAlreadyTaken() implements Outcome {}

        /** The rules each channel broke; a channel that passed is an empty list. */
        record Rejected(List<BrokenRule> emailErrors, List<BrokenRule> passwordErrors) implements Outcome {}

        /** This source has spent its window; nothing was judged and nothing was hashed. */
        record Throttled(long retryAfterSeconds) implements Outcome {}
    }

    public Outcome register(String email, String password, String ipAddress) {

        // guard before the expensive work: registration hashes a password and creates an account
        SourceThrottle.Decision decision = throttle.check(
                new IpAddress(ipAddress));

        if (
                !decision.allowed()
        ) {
            return new Outcome.Throttled(
                    decision.retryAfterSeconds());
        }

        // The account and the mail that makes it usable are written in ONE transaction. They used to
        // be two: the account committed first, and anything that went wrong afterwards — the JVM
        // being stopped between them, a failure appending to the outbox — left an account whose
        // owner was never sent a link, while every sign-in demands a verified address. Nothing here
        // talks to the mail service synchronously (the notifier appends to the transactional
        // outbox), so there is no slow call to keep out of the transaction.
        return transactionBoundary.execute(() -> registerAndNotify(email, password));
    }

    private Outcome registerAndNotify(String email, String password) {

        // the strings become candidates, not objects: an address or a password that does not
        // construct is a broken rule the judges report, not an exception thrown here
        Register.Result result = register.execute(
                () -> Email.of(email),
                () -> PlaintextPassword.of(password));

        return switch (result) {
            case Register.Result.Registered registered -> {
                // sign-in requires a verified address, so onboarding starts the verification here
                requestEmailVerification.execute(
                        registered.user().email());
                yield new Outcome.Registered();
            }
            case Register.Result.EmailAlreadyTaken alreadyTaken -> {
                // quiet refusal: the caller sees a fresh-looking registration; the address owner is
                // told by mail — a lost-mail re-register gets a fresh link, a real account a notice
                if (
                        userRepository.isVerified(alreadyTaken.email())
                ) {
                    registrationNoticeNotifier.sendAlreadyRegistered(alreadyTaken.email());
                } else {
                    requestEmailVerification.execute(alreadyTaken.email());
                }
                yield new Outcome.EmailAlreadyTaken();
            }
            // nothing was written, and nothing is mailed: the address may not even be one
            case Register.Result.EmailRejected rejected ->
                    new Outcome.Rejected(
                            BrokenRule.ofEmail(rejected.emailErrors(), emailPolicy),
                            List.of());
            case Register.Result.PasswordRejected rejected ->
                    new Outcome.Rejected(
                            List.of(),
                            BrokenRule.ofPassword(rejected.passwordErrors(), rejected.policy()));
            case Register.Result.BothRejected rejected ->
                    new Outcome.Rejected(
                            BrokenRule.ofEmail(rejected.emailErrors(), emailPolicy),
                            BrokenRule.ofPassword(rejected.passwordErrors(), rejected.policy()));
        };
    }
}
