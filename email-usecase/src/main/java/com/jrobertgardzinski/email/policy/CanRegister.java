package com.jrobertgardzinski.email.policy;

import com.jrobertgardzinski.email.config.CanRegisterConfig;
import com.jrobertgardzinski.email.domain.DomainPart;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Judges an address that did construct against a deployment's registration policy. A refused one
 * comes back as {@link Result.Failure} with every broken rule at once, in policy order, so a form
 * can show all of them in one round. A {@code null} rule in the policy is an absent rule.
 */
public class CanRegister {

    private static final Pattern RFC_FORMAT = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+"
                    + "@"
                    + "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"
                    + "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");

    public sealed interface Result {
        record Success(Email email) implements Result {}
        record Failure(List<EmailError> errors) implements Result {
            public Failure {
                errors = List.copyOf(errors);
            }
        }
    }

    public Result evaluate(Email email, CanRegisterConfig policy) {
        List<EmailError> errors = new LinkedList<>();
        DomainPart domain = email.domain();
        if (!RFC_FORMAT.matcher(email.value()).matches()) {
            errors.add(EmailError.RFC_FORMAT_INVALID);
        }
        if (policy.blockedDomains() != null && policy.blockedDomains().values().contains(domain)) {
            errors.add(EmailError.DOMAIN_BLOCKED);
        }
        if (policy.disposableDomains() != null && policy.disposableDomains().values().contains(domain)) {
            errors.add(EmailError.DISPOSABLE_DOMAIN);
        }
        if (policy.companyDomains() != null && !policy.companyDomains().values().contains(domain)) {
            errors.add(EmailError.NOT_A_COMPANY_DOMAIN);
        }

        if (errors.isEmpty()) {
            return new Result.Success(email);
        }
        else {
            return new Result.Failure(errors);
        }
    }
}
