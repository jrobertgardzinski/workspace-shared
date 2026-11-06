package com.jrobertgardzinski.email.domain;

import java.util.Objects;

/** The recipient-specific portion of an {@link Email}, preceding the '@' symbol. */
public final class LocalPart {

    private final String value;

    LocalPart(String value) {
        this.value = value;
    }

    public static LocalPart of(String value) {
        if (value == null || value.isEmpty()) {
            throw new InvalidEmailException(EmailError.LOCAL_PART_EMPTY, "Email local part must not be empty");
        }
        if (value.startsWith(".") || value.endsWith(".")) {
            throw new InvalidEmailException(EmailError.LOCAL_PART_DOT_AT_EDGE,
                    "Email local part must not start or end with a dot: " + value);
        }
        // dot-atom is dot-SEPARATED atoms, so a dot must have something on either side. The RFC
        // constraint downstream cannot catch this one: its pattern carries the dot inside the
        // character class, so "a..b" matches it - and did, right up to a created account.
        if (value.contains("..")) {
            throw new InvalidEmailException(EmailError.LOCAL_PART_CONSECUTIVE_DOTS,
                    "Email local part must not contain two dots in a row: " + value);
        }
        return new LocalPart(value);
    }

    public String value() { return value; }

    /**
     * The form this address is DEDUPLICATED by — never what is shown or mailed to.
     *
     * <p>Case first, and for every domain. The RFC allows a mail server to treat the local part as
     * case-sensitive, and essentially none does; what the old rule produced instead was two
     * accounts for {@code Alice@corp.com} and {@code alice@corp.com}, a sign-in that answered
     * "wrong password" to the same person typing the same address with a capital, and a
     * per-(source, account) lockout counter that split across spellings — so guessing one account
     * had as many budgets as the attacker had ways to write it. Only the four big providers were
     * lower-cased, because that is where the PROVIDER-SPECIFIC rules below happen to live; the
     * lower-casing was never provider-specific at all.
     *
     * <p>The rest stay exactly as they were: gmail ignores dots and everything after a '+',
     * outlook and yahoo drop their own suffixes. Those ARE provider claims about their own address
     * space, and stating them for a domain that never made them would merge two people's accounts.
     */
    // todo should be DomainPart instead of String domain!
    public LocalPart normalize(String domain) {
        String lower = value.toLowerCase();
        if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            return new LocalPart(lower.replaceAll("\\+.*", "").replace(".", ""));
        }
        if (domain.startsWith("yahoo.")) {
            return new LocalPart(lower.replaceAll("-.*", ""));
        }
        if (domain.equals("outlook.com") || domain.equals("hotmail.com") || domain.equals("live.com")) {
            return new LocalPart(lower.replaceAll("\\+.*", ""));
        }
        return new LocalPart(lower);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalPart)) return false;
        LocalPart other = (LocalPart) o;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return value; }
}
