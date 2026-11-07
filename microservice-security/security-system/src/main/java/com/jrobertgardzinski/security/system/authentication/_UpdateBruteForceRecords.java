package com.jrobertgardzinski.security.system.authentication;

import com.jrobertgardzinski.security.domain.repository.RejectedAuthenticationRepository;
import com.jrobertgardzinski.security.domain.vo.LockoutSubject;
import com.jrobertgardzinski.security.domain.vo.RejectedAuthenticationDetails;
import com.jrobertgardzinski.security.system.mfa.PendingAuthentication;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** Writes one failed attempt down, charged to the pair that made it: this source, this account. */
class _UpdateBruteForceRecords {

    private final RejectedAuthenticationRepository rejectedAuthenticationRepository;
    private final Clock clock;

    public _UpdateBruteForceRecords(
                    RejectedAuthenticationRepository rejectedAuthenticationRepository,
                    Clock clock) {
        this.rejectedAuthenticationRepository = rejectedAuthenticationRepository;
        this.clock = clock;
    }

    public void execute(LockoutSubject subject) {

        rejectedAuthenticationRepository.create(
                new RejectedAuthenticationDetails(subject, LocalDateTime.now(clock)));
    }

    /**
     * A wrong proof is a wrong guess at this account from this address, so it is written down like
     * a wrong password. Five-per-ticket was never a limit on the person: link #1 is a password they
     * already hold, so a new ticket costs one request and the codes could be walked at whatever
     * rate tickets could be minted.
     */
    public void execute(PendingAuthentication chain) {

        Optional<LockoutSubject> lockoutSubject = chain.lockoutSubject();

        if (
                lockoutSubject.isPresent()
        ) {
            execute(
                    lockoutSubject.get());
        }
    }
}
