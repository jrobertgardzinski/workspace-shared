package com.jrobertgardzinski.security.domain.vo;

/**
 * What requiring a {@link Role} of a caller found: they hold it, or the refusal for lacking it, one
 * per role, so a non-admin learns only that they are not one. The wire carries the refusal's
 * {@link #name()}.
 */
public enum RoleStatus {
    PASSED,
    NOT_A_USER,
    NOT_A_MODERATOR,
    NOT_AN_ADMIN
}
