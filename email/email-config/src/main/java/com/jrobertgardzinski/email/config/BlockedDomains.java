package com.jrobertgardzinski.email.config;

import com.jrobertgardzinski.email.domain.DomainPart;

import java.util.Objects;
import java.util.Set;

/**
 * Domains no address may register from; never empty.
 */
public record BlockedDomains(Set<DomainPart> values) {
    public BlockedDomains {
        Objects.requireNonNull(values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Set cannot be empty!");
        }
    }
}
