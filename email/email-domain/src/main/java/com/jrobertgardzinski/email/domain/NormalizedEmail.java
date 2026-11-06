package com.jrobertgardzinski.email.domain;

/**
 * An {@link Email} with provider-specific normalization applied, used for deduplication.
 */
public final class NormalizedEmail extends AbstractEmail {

    private final LocalPart local;
    private final DomainPart domain;

    private NormalizedEmail(LocalPart local, DomainPart domain) {
        this.local = local;
        this.domain = domain;
    }

    public static NormalizedEmail of(Email email) {
        DomainPart domain = email.domain();
        LocalPart local = email.local().normalize(domain.value());
        return new NormalizedEmail(local, domain);
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
