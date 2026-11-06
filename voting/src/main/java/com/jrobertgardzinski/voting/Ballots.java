package com.jrobertgardzinski.voting;

import java.util.Optional;

/**
 * Storage port of the voting context: at most one remembered vote per voter per target. Services
 * decide what a target is (a meme, a comment, anything votable) and where the ballots live; the
 * semantics of casting live in {@link Voting}.
 */
public interface Ballots {

    void cast(String targetId, String voter, VoteDirection direction);

    void retract(String targetId, String voter);

    Optional<VoteDirection> voteOf(String targetId, String voter);

    /** Up-voters minus down-voters. */
    int scoreOf(String targetId);
}
