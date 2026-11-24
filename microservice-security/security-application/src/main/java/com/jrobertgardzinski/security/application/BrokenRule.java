package com.jrobertgardzinski.security.application;

import com.jrobertgardzinski.email.config.EmailPolicy;
import com.jrobertgardzinski.email.domain.DomainPart;
import com.jrobertgardzinski.email.domain.EmailError;
import com.jrobertgardzinski.password.domain.PasswordError;
import com.jrobertgardzinski.password.policy.PasswordPolicy;

import java.util.List;

/**
 * One rule an address or a password broke, in words the framework side can carry: the rule's code
 * and the parameter in force for this very attempt ({@code MIN_LENGTH_NOT_MET}, 12), or
 * {@code true} where the rule has none. Both policies are configuration, so a bare code would
 * leave the caller guessing what the minimum was, or which domains an employee may register from.
 *
 * @param code      the rule's name, as the wire spells it
 * @param parameter an {@code Integer}, a {@code String}, a {@code List<String>} or
 *                  {@code Boolean.TRUE}
 */
public record BrokenRule(String code, Object parameter) {

    /**
     * Only the company rule carries a parameter: the domains an employee MAY register from are the
     * hint the caller needs. The blocked and disposable rules stay {@code true}: those lists are
     * denylists, and enumerating them would only tell an abuser which domains to try next; the
     * caller already knows which domain they typed.
     */
    public static List<BrokenRule> ofEmail(List<EmailError> errors, EmailPolicy policy) {
        // exhaustive on purpose: a new rule does not compile until it says what parameter it carries
        return errors.stream().map(error -> new BrokenRule(error.name(), switch (error) {
            case NOT_A_COMPANY_DOMAIN -> policy.companyDomains().values().stream()
                    .map(DomainPart::value).sorted().toList();
            case EMAIL_BLANK, EMAIL_FORMAT_INVALID, LOCAL_PART_EMPTY, LOCAL_PART_DOT_AT_EDGE,
                 LOCAL_PART_CONSECUTIVE_DOTS, DOMAIN_EMPTY, DOMAIN_MISSING_DOT,
                 RFC_FORMAT_INVALID, DOMAIN_BLOCKED, DISPOSABLE_DOMAIN -> Boolean.TRUE;
        })).toList();
    }

    /** Digit, uppercase and lowercase say everything in their code already. */
    public static List<BrokenRule> ofPassword(List<PasswordError> errors, PasswordPolicy policy) {
        return errors.stream().map(error -> new BrokenRule(error.name(), switch (error) {
            case MIN_LENGTH_NOT_MET -> policy.minLength().value();
            case SPECIAL_CHAR_REQUIRED -> policy.specialChars().value();
            case PASSWORD_BLANK, LOWERCASE_REQUIRED, UPPERCASE_REQUIRED, DIGIT_REQUIRED -> Boolean.TRUE;
        })).toList();
    }
}
