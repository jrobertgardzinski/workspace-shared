package com.jrobertgardzinski.email.config;


/**
 * The domain rules registration is judged by; a {@code null} list is an absent rule.
 */
public record EmailPolicy(
        BlockedDomains blockedDomains,
        DisposableDomains disposableDomains,
        CompanyDomains companyDomains) {

    public EmailPolicy() {
        this(null, null, null);
    }
}
