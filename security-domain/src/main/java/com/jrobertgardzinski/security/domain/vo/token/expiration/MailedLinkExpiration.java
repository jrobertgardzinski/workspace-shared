package com.jrobertgardzinski.security.domain.vo.token.expiration;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * When a link that was e-mailed stops working: the moment it was issued plus the window its use
 * case allows. The stored row keeps only the issue time, so the window is handed in by the caller.
 */
public final class MailedLinkExpiration extends AbstractTokenExpiration {

    public MailedLinkExpiration(LocalDateTime issuedAt, Duration window) {
        super(issuedAt.plus(window));
    }
}
