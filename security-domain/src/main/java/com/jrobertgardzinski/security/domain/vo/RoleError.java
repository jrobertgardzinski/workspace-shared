package com.jrobertgardzinski.security.domain.vo;

/**
 * The refusal a caller gets for lacking a {@link Role}: one per role, so a non-admin learns only
 * that they are not one. The wire carries {@link #name()}.
 */
public enum RoleError {
    NOT_A_USER,
    NOT_A_MODERATOR,
    NOT_AN_ADMIN
}
