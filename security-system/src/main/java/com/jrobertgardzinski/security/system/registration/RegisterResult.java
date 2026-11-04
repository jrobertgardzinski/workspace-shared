package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;

import java.util.List;

public sealed interface RegisterResult {

    record Registered(User user) implements RegisterResult {}

    /**
     * Both channels at once: the rules the address broke and the rules the password broke, at least
     * one list non-empty, and the policy the password was judged against, because that policy can
     * change between attempts; the address policy is a boot value the edge already holds.
     */
    record Rejected(List<EmailError> emailErrors, List<PasswordError> passwordErrors,
                    PasswordPolicy policy) implements RegisterResult {
        public Rejected {
            emailErrors = List.copyOf(emailErrors);
            passwordErrors = List.copyOf(passwordErrors);
        }
    }

    record EmailAlreadyTaken(Email email) implements RegisterResult {}
}
