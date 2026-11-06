package com.jrobertgardzinski.closure;

/**
 * Who asked for an account to be closed — and therefore under what basis it is closed.
 *
 * <p>{@link #SELF} is the data subject exercising the right to erasure. The conditions under which
 * their content may survive are not anybody's to choose: the exceptions to that right are
 * enumerated by law, and "this one is popular" is not among them. So a self-closure states no
 * conditions, and a service that receives one destroys what it holds whatever its own defaults or
 * an operator's override say.
 *
 * <p>{@link #ADMIN} is the controller of the data acting on its own decision — a ban, house rules.
 * Nobody exercised a right, so conditions ARE a business choice and may ride the saga: keep what
 * the community made popular, keep it without its author, or destroy it.
 *
 * <p>WHICH administrator pressed the button is identity's own audit line and is deliberately not
 * on the wire: the content services would each gain an address to hold, and not one of them has a
 * use for it.
 */
public enum ClosureInitiator {

    SELF,
    ADMIN;

    /** The word this initiator goes by on the fact and on every command derived from it. */
    public String wire() {
        return name();
    }

    /**
     * Read an initiator off the wire — and note that this is NOT symmetrical, deliberately.
     *
     * <p>Anything that is not exactly {@code ADMIN} is the account's own owner. A missing value is
     * the honest reading of a message from before the field existed (identity had one closure route
     * then, and only the owner could walk it), and a value nobody recognises must never be the
     * reason somebody's content survives their own erasure request. Both unknowns therefore fall to
     * the stricter side, which is the side that deletes.
     */
    public static ClosureInitiator of(String raw) {
        return ADMIN.name().equals(raw) ? ADMIN : SELF;
    }

    /** Whether the conditions travelling beside this closure may be honoured at all. */
    public static boolean allowsConditions(String raw) {
        return of(raw) == ADMIN;
    }
}
