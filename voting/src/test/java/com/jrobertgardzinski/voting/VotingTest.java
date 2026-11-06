package com.jrobertgardzinski.voting;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Epic("Voting")
@Feature("Toggle semantics")
class VotingTest {

    private final Map<String, Map<String, VoteDirection>> votes = new HashMap<>();

    private final Ballots ballots = new Ballots() {
        public void cast(String targetId, String voter, VoteDirection direction) {
            votes.computeIfAbsent(targetId, id -> new HashMap<>()).put(voter, direction);
        }

        public void retract(String targetId, String voter) {
            votes.getOrDefault(targetId, Map.of()).remove(voter);
        }

        public Optional<VoteDirection> voteOf(String targetId, String voter) {
            return Optional.ofNullable(votes.getOrDefault(targetId, Map.of()).get(voter));
        }

        public int scoreOf(String targetId) {
            return votes.getOrDefault(targetId, Map.of()).values().stream()
                    .mapToInt(d -> d == VoteDirection.UP ? 1 : -1).sum();
        }
    };
    private final Voting voting = new Voting(ballots);

    @Test
    @DisplayName("a vote counts once; repeating retracts; the opposite switches")
    void toggle_semantics() {
        assertEquals(new VoteTally(1, Optional.of(VoteDirection.UP)),
                voting.toggle("t1", "alice", VoteDirection.UP));
        assertEquals(new VoteTally(0, Optional.empty()),
                voting.toggle("t1", "alice", VoteDirection.UP));          // retracted
        assertEquals(new VoteTally(1, Optional.of(VoteDirection.UP)),
                voting.toggle("t1", "alice", VoteDirection.UP));
        assertEquals(new VoteTally(-1, Optional.of(VoteDirection.DOWN)),
                voting.toggle("t1", "alice", VoteDirection.DOWN));        // switched
    }

    @Test
    @DisplayName("distinct voters accumulate; the tally shows each viewer their own choice")
    void tally_through_a_viewers_eyes() {
        voting.toggle("t1", "alice", VoteDirection.UP);
        voting.toggle("t1", "bob", VoteDirection.UP);

        assertEquals(new VoteTally(2, Optional.of(VoteDirection.UP)), voting.tally("t1", Optional.of("bob")));
        assertEquals(new VoteTally(2, Optional.empty()), voting.tally("t1", Optional.of("carol")));
        assertEquals(new VoteTally(2, Optional.empty()), voting.tally("t1", Optional.empty()));
    }
}
