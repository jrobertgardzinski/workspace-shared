package com.jrobertgardzinski.email.config;

import com.jrobertgardzinski.email.domain.DomainPart;

import java.util.Objects;
import java.util.Set;

/**
 * The only domains an employee may register from; never empty.
 */
public record CompanyDomains(Set<DomainPart> values) {
    public CompanyDomains {
        Objects.requireNonNull(values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Set cannot be empty!");
        }
    }
}
