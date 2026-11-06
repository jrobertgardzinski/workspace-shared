package com.jrobertgardzinski.email.domain;

import java.util.Objects;

/**
 * Common abstraction for all email address representations.
 */
public abstract class AbstractEmail {

    public abstract String value();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractEmail)) return false;
        AbstractEmail other = (AbstractEmail) o;
        return value().equals(other.value());
    }

    @Override
    public int hashCode() {
        return Objects.hash(value());
    }

    @Override
    public String toString() {
        return value();
    }
}
