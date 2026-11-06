package com.jrobertgardzinski.voting;

import java.util.Optional;

/**
 * The heart of the voting context — Reddit-style toggle semantics over a {@link Ballots} store:
 * a first vote counts, repeating the same direction retracts it, the opposite direction switches
 * it. Every operation answers with the target's fresh {@link VoteTally} (score + the voter's
 * resulting choice), which is exactly what a UI needs to render a pressed arrow.
 */
public final class Voting {

    private final Ballots ballots;

    public Voting(Ballots ballots) {
        this.ballots = ballots;
    }

    public VoteTally toggle(String targetId, String voter, VoteDirection direction) {
        if (ballots.voteOf(targetId, voter).filter(direction::equals).isPresent()) {
            ballots.retract(targetId, voter);
        } else {
            ballots.cast(targetId, voter, direction);
        }
        return tally(targetId, Optional.of(voter));
    }

    /** The tally through a viewer's eyes; anonymous viewers see no own choice. */
    public VoteTally tally(String targetId, Optional<String> viewer) {
        return new VoteTally(ballots.scoreOf(targetId), viewer.flatMap(v -> ballots.voteOf(targetId, v)));
    }
}
