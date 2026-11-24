package com.jrobertgardzinski.security.system.account;

import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.security.domain.repository.EnrolledFactorRepository;
import com.jrobertgardzinski.security.domain.repository.MailedLinkRepository;
import com.jrobertgardzinski.security.domain.repository.SessionRepository;
import com.jrobertgardzinski.security.domain.repository.UserRepository;

/**
 * Closes a user's account (GDPR right to be forgotten): revokes every session, drops the MFA
 * factors and recovery codes the account left behind (their secret hashes must not outlive the
 * account), severs the federated links (a stale link would let the old owner's Google identity
 * open whoever registers the freed address next), then deletes the user — so nothing of the
 * account can authenticate and no trace of its secrets remains. Idempotent.
 *
 * <p>Every one of those tables is keyed by the e-mail ADDRESS and not one of them has a foreign key,
 * so nothing cascades: whatever this use case does not name by hand outlives the account, and the
 * freed address can be registered by a stranger. Hence the pending-token tables are purged too — a
 * reset link e-mailed before the account closed would otherwise still be redeemable and, matched by
 * address alone, would set the password of the address's NEXT owner. So is the passwordless mark: it
 * would tell a step-up that the successor's account has no password to check.
 *
 * <p>One call per repository, each forgetting everything it holds under the address: the sessions,
 * the second factors with their recovery codes, the mailed links still pending, and last the
 * account itself together with its verification, its passwordless mark and its federated links.
 */
public class DeleteAccount {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final EnrolledFactorRepository enrolledFactorRepository;
    private final MailedLinkRepository mailedLinkRepository;

    public DeleteAccount(
                    UserRepository userRepository,
                    SessionRepository sessionRepository,
                    EnrolledFactorRepository enrolledFactorRepository,
                    MailedLinkRepository mailedLinkRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.enrolledFactorRepository = enrolledFactorRepository;
        this.mailedLinkRepository = mailedLinkRepository;
    }

    public void execute(Email email) {

        sessionRepository.revokeAllSessions(email);
        enrolledFactorRepository.purge(email);
        mailedLinkRepository.purge(email);
        userRepository.deleteByEmail(email);
    }
}
