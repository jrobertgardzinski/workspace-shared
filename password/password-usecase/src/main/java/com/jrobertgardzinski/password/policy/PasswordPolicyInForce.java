package com.jrobertgardzinski.password.policy;

/**
 * The password policy as it stands right now; a use case asks per attempt and never keeps the answer.
 */
@FunctionalInterface
public interface PasswordPolicyInForce {
    PasswordPolicy current();
}
