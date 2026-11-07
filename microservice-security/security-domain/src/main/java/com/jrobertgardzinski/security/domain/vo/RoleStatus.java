package com.jrobertgardzinski.security.domain.vo;

/**
 * What requiring a {@link Role} of a caller found: they hold it, or the refusal for lacking it, one
 * per role, so a non-admin learns only that they are not one. The wire code for each refusal
 * ({@code NOT_AN_ADMIN}) is the edge's to spell. Sealed, not an enum, so that a {@code switch}
 * statement over it is exhaustive by the compiler.
 */
public sealed interface RoleStatus {

    record Passed() implements RoleStatus {}

    record NotAUser() implements RoleStatus {}

    record NotAModerator() implements RoleStatus {}

    record NotAnAdmin() implements RoleStatus {}
}
