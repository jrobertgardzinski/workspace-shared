package com.jrobertgardzinski.email.domain;

import java.util.Objects;

/**
 * The mail server portion of an {@link Email}, following the '@' symbol.
 */
public final class DomainPart {

    private final String value;

    DomainPart(String value) {
        this.value = value;
    }

    public static DomainPart of(String value) {
        if (value == null || value.isEmpty()) {
            throw new InvalidEmailException(EmailError.DOMAIN_EMPTY, "Email domain must not be empty");
        }
        String lower = value.toLowerCase();
        if (!lower.contains(".")) {
            throw new InvalidEmailException(EmailError.DOMAIN_MISSING_DOT,
                    "Email domain must contain at least one '.': " + value);
        }
        return new DomainPart(lower);
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DomainPart)) return false;
        DomainPart other = (DomainPart) o;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return value; }
}
