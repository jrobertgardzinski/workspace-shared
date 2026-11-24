package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

/**
 * Gate applied after the credentials check out: only a verified e-mail address may sign in. Keeps
 * an unverified (possibly hijacked-at-registration) address from becoming a working account.
 */
class _RequireVerifiedEmail {

    private final UserRepository userRepository;

    _RequireVerifiedEmail(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    boolean isVerified(Email email) {
        return userRepository.isVerified(email);
    }
}
