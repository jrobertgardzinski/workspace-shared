package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.EmailError;

import java.util.List;

/**
 * Outcome of {@link RequestEmailChange}: a verification link was sent to the new address, that
 * address is already taken, or the address is one this deployment does not admit at all.
 */
public sealed interface RequestEmailChangeResult {

    record Requested() implements RequestEmailChangeResult {}

    record EmailTaken() implements RequestEmailChangeResult {}

    /**
     * The address broke the e-mail policy: the rules it broke, exactly as registration reports
     * them, so one client renders both refusals with one piece of code. The policy itself is a
     * boot value the edge already holds.
     */
    record Rejected(List<EmailError> emailErrors) implements RequestEmailChangeResult {}
}
