package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.policy.PasswordPolicy;
import com.jrobertgardzinski.security.domain.entity.User;

import java.util.List;

public sealed interface RegisterResult {

    record Registered(User user) implements RegisterResult {}

    /** Both channels at once, each with the policy it was judged against, so the wire can name the parameters. */
    record Rejected(List<EmailError> emailErrors, CanRegisterConfig emailPolicy,
                    List<PasswordError> passwordErrors, PasswordPolicy passwordPolicy) implements RegisterResult {
        public Rejected {
            emailErrors = List.copyOf(emailErrors);
            passwordErrors = List.copyOf(passwordErrors);
        }
    }

    record EmailAlreadyTaken(Email email) implements RegisterResult {}
}
