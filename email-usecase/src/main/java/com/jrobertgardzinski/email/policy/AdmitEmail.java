package com.jrobertgardzinski.email.policy;

import com.jrobertgardzinski.email.config.EmailPolicy;
import com.jrobertgardzinski.email.domain.DomainPart;
import com.jrobertgardzinski.email.domain.Email;
import com.jrobertgardzinski.email.domain.EmailError;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Whether an address that did construct may hold an account here: the deployment's {@link EmailPolicy},
 * applied to a new registration and to the address an existing account wants to move to alike. A
 * refused one comes back as {@link Result.Failure} with every broken rule at once, in policy order,
 * so a form can show all of them in one round. A {@code null} rule in the policy is an absent rule.
 *
 * <p>The policy is a boot value, so it is given once here; the password policy, which can change
 * between attempts, travels with each call instead.
 */
public class AdmitEmail {

    private static final Pattern RFC_FORMAT = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+"
                    + "@"
                    + "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"
                    + "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");

    private final EmailPolicy policy;

    public AdmitEmail(EmailPolicy policy) {
        this.policy = policy;
    }

    public sealed interface Result {
        record Success(Email email) implements Result {}
        record Failure(List<EmailError> errors) implements Result {
            public Failure {
                errors = List.copyOf(errors);
            }
        }
    }

    public Result evaluate(Email email) {
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
