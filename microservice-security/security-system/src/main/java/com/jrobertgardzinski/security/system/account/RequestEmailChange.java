package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;
import com.jrobertgardzinski.email.domain.NormalizedEmail;
import com.jrobertgardzinski.email.policy.AdmitEmail;
import com.jrobertgardzinski.security.domain.port.EmailVerificationNotifier;
import com.jrobertgardzinski.security.domain.repository.EmailChangeRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;
import com.jrobertgardzinski.security.domain.vo.EmailChange;
import com.jrobertgardzinski.security.domain.vo.token.VerificationToken;

import java.util.List;
import com.jrobertgardzinski.email.domain.EmailStatus;


/**
 * Starts an email change for a signed-in user: refuses an address this deployment does not admit,
 * refuses one that is already taken, and otherwise mints a verification token, remembers the
 * pending change, and e-mails the link to the new address (ownership must be proven before the
 * change takes effect).
 *
 * <p>The e-mail policy is asked FIRST, and asked here rather than only at registration: the two
 * questions are the same question. A deployment that admits only company addresses, or refuses
 * disposable ones, was letting an account walk out of that rule the moment it existed — the change
 * takes roles, factors and federated links with it. The refusal carries the broken rules and the
 * policy in force, the same shape registration answers with.
 */
public class RequestEmailChange {

    private final UserRepository userRepository;
    private final EmailChangeRepository emailChangeRepository;
    private final EmailVerificationNotifier notifier;
    private final AdmitEmail admitEmail;

    public RequestEmailChange(
                    UserRepository userRepository,
                    EmailChangeRepository emailChangeRepository,
                    EmailVerificationNotifier notifier,
                    AdmitEmail admitEmail) {
        this.userRepository = userRepository;
        this.emailChangeRepository = emailChangeRepository;
        this.notifier = notifier;
        this.admitEmail = admitEmail;
    }

    /**
     * Outcome of {@link RequestEmailChange}: a verification link was sent to the new address, that
     * address is already taken, or the address is one this deployment does not admit at all.
     */
    public sealed interface Result {

        record Requested() implements Result {}

        record EmailTaken() implements Result {}

        /**
         * The address broke the e-mail policy: the rules it broke, exactly as registration reports
         * them, so one client renders both refusals with one piece of code. The policy itself is a
         * boot value the edge already holds.
         */
        record Rejected(List<EmailError> emailErrors) implements Result {}
    }

    public Result execute(Email currentEmail, Email newEmail) {

        // the same question Register asks of a new address, asked again of the one an account wants
        // to move to: a closed shop that only checks at registration is not closed
        AdmitEmail.Result admitEmailResult = admitEmail.evaluate(newEmail);

        switch (admitEmailResult.status()) {
            case EmailStatus.Rejected _ -> {
                return new Result.Rejected(
                        admitEmailResult.errors());
            }
            case EmailStatus.Passed _ -> {}
        }

        if (
                userRepository.existsBy(
                        NormalizedEmail.of(newEmail))
        ) {
            return new Result.EmailTaken();
        }

        VerificationToken token = VerificationToken.random();
        emailChangeRepository.startChange(
                new EmailChange(currentEmail, newEmail),
                token);
        notifier.sendVerificationLink(newEmail, token);

        return new Result.Requested();
    }
}
