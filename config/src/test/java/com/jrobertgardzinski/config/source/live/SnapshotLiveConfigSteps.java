package com.jrobertgardzinski.config.source.live;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SnapshotLiveConfigSteps {

    private final Map<String, String> table = new HashMap<>();
    private int reads;
    private boolean unreadable;

    private SnapshotLiveConfigPort snapshot;

    private Map<String, String> readTable() {
        reads++;
        if (unreadable)
            throw new IllegalStateException("connection refused");
        return new HashMap<>(table);
    }

    @Given("a settings table where {string} is {string}")
    public void aSettingsTableWhereIs(String name, String value) {
        table.put(name, value);
        snapshot = new SnapshotLiveConfigPort(this::readTable);
    }

    @Given("a settings table where {string} is {string} and {string} is {string}")
    public void aSettingsTableWhereIsAndIs(String name, String value, String other, String otherValue) {
        table.put(name, value);
        table.put(other, otherValue);
        snapshot = new SnapshotLiveConfigPort(this::readTable);
    }

    @Given("an empty settings table")
    public void anEmptySettingsTable() {
        snapshot = new SnapshotLiveConfigPort(this::readTable);
    }

    @Given("an unreadable settings table")
    public void anUnreadableSettingsTable() {
        unreadable = true;
    }

    @When("the table's {string} changes to {string}")
    public void theTablesChangesTo(String name, String value) {
        table.put(name, value);
    }

    @When("the snapshot is asked for {string} and {string} and {string}")
    public void theSnapshotIsAskedFor(String first, String second, String third) {
        snapshot.find(first);
        snapshot.find(second);
        snapshot.find(third);
    }

    @When("the writer refreshes the snapshot")
    public void theWriterRefreshesTheSnapshot() {
        snapshot.refresh();
    }

    @When("the table becomes unreadable")
    public void theTableBecomesUnreadable() {
        unreadable = true;
    }

    @Then("the snapshot cannot be taken")
    public void theSnapshotCannotBeTaken() {
        assertThatThrownBy(() -> new SnapshotLiveConfigPort(this::readTable))
                .isInstanceOf(IllegalStateException.class);
    }

    @Then("the writer's refresh fails")
    public void theWritersRefreshFails() {
        assertThatThrownBy(snapshot::refresh).isInstanceOf(IllegalStateException.class);
    }

    @Then("the table was read once")
    public void theTableWasReadOnce() {
        assertThat(reads).isEqualTo(1);
    }

    @Then("the snapshot answers {string} for {string}")
    public void theSnapshotAnswersFor(String expected, String name) {
        assertThat(snapshot.find(name)).isEqualTo(expected);
    }

    @Then("the snapshot answers nothing for {string}")
    public void theSnapshotAnswersNothingFor(String name) {
        assertThat(snapshot.find(name)).isNull();
    }
}
