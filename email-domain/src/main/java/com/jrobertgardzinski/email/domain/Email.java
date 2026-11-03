package com.jrobertgardzinski.email.domain;

/**
 * An email address, composed of a {@link LocalPart} and a {@link DomainPart}.
 */
public final class Email extends AbstractEmail {

    private final LocalPart local;
    private final DomainPart domain;

    Email(LocalPart local, DomainPart domain) {
        this.local = local;
        this.domain = domain;
    }

    public static Email of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidEmailException(EmailError.EMAIL_BLANK, "Email cannot be null or blank");
        }

        int atIndex = raw.indexOf('@');
        if (atIndex < 1 || atIndex != raw.lastIndexOf('@') || atIndex == raw.length() - 1) {
            throw new InvalidEmailException(EmailError.EMAIL_FORMAT_INVALID, "Invalid email format: " + raw);
        }

        LocalPart local = LocalPart.of(raw.substring(0, atIndex));
        DomainPart domain = DomainPart.of(raw.substring(atIndex + 1));

        return new Email(local, domain);
    }

    public LocalPart local() {
        return local;
    }

    public DomainPart domain() {
        return domain;
    }

    public String value() {
        return local + "@" + domain;
    }


}
