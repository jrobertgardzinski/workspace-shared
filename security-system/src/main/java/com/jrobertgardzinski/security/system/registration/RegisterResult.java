package com.jrobertgardzinski.security.system.registration;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.policy.AdmitEmail;
import com.jrobertgardzinski.password.policy.CreatePasswordHash;
import com.jrobertgardzinski.security.domain.entity.User;

import java.util.Optional;

public sealed interface RegisterResult {

    record Registered(User user) implements RegisterResult {}

    /**
     * Both channels at once, each refusal as its judge handed it, at least one of them present. The
     * password refusal carries the policy it was judged against, because that policy can change
     * between attempts; the address policy is a boot value the edge already holds.
     */
    record Rejected(Optional<AdmitEmail.Result.Failure> emailFailure,
                    Optional<CreatePasswordHash.Result.Failure> passwordFailure) implements RegisterResult {}

    record EmailAlreadyTaken(Email email) implements RegisterResult {}
}
