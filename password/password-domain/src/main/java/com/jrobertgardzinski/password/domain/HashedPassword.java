package com.jrobertgardzinski.password.domain;

import java.util.Objects;

/**
 * A password transformed by a hashing algorithm, safe for storage.
 */
public final class HashedPassword {
    private final String value;

    public HashedPassword(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HashedPassword that = (HashedPassword) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /** Redacted intentionally — never log hashed passwords. */
    @Override
    public String toString() {
        return "PasswordHash[***]";
    }
}
