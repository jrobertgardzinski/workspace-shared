package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.security.domain.entity.User;

import java.util.List;
import java.util.Optional;

public sealed interface RegisterResult {

    record Registered(User user) implements RegisterResult {}

    /**
     * Both channels at once: the rules the address broke (maybe none) and the password's refusal
     * (maybe none), at least one of them present. The password refusal carries the policy it was
     * judged against, because that policy can change between attempts; the address policy is a
     * boot value the edge already holds.
     */
    record Rejected(List<EmailError> emailErrors,
                    Optional<CreatePasswordHash.Result.Failure> passwordFailure) implements RegisterResult {
        public Rejected {
            emailErrors = List.copyOf(emailErrors);
        }
    }

    record EmailAlreadyTaken(Email email) implements RegisterResult {}
}
