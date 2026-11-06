package com.jrobertgardzinski.config.ladder;

import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.config.source.restart.RestartConfigPort;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class ConfigLadderSteps {

    private final Map<String, Integer> databaseRows = new HashMap<>();
    private final Map<String, Integer> properties = new HashMap<>();
    private final LiveConfigPort<Integer> rows = databaseRows::get;
    private final RestartConfigPort<Integer> props = properties::get;
    /** The same two levels as TEXT, the way a properties file and a settings table hold them. */
    private final Map<String, String> textRows = new HashMap<>();
    private final Map<String, String> textProperties = new HashMap<>();
    private final LiveConfigPort<String> rowsAsText = textRows::get;
    private final RestartConfigPort<String> propsAsText = textProperties::get;
    private final List<LogRecord> warnings = new ArrayList<>();
    private final Handler collectWarnings = new Handler() {
        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue())
                warnings.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    private Consumer<Integer> gate;
    private String keyName;
    private List<Rung<Integer>> rungs;
    private Resolution<Integer> answer;
    private Throwable declarationFailure;

    @Given("the validation gate accepts only values of at least {int}")
    public void theValidationGateAcceptsOnlyValuesOfAtLeast(int floor) {
        gate = value -> {
            if (value < floor)
                throw new IllegalArgumentException("value must be at least " + floor);
        };
    }

    @Given("a ladder for {string} with rungs live, restart and rebuild default {int}")
    public void aLadderOverAllThree(String name, int def) {
        declare(name, List.of(Rung.live(rows), Rung.restart(props), Rung.rebuild(def)));
    }

    @Given("a ladder for {string} with rungs restart and rebuild default {int}")
    public void aLadderWithoutLive(String name, int def) {
        declare(name, List.of(Rung.restart(props), Rung.rebuild(def)));
    }

    @Given("a ladder for {string} with rungs live and rebuild default {int}")
    public void aLadderWithoutRestart(String name, int def) {
        declare(name, List.of(Rung.live(rows), Rung.rebuild(def)));
    }

    @Given("a ladder for {string} with rungs rebuild default {int}")
    public void aLadderOfTheDefaultAlone(String name, int def) {
        declare(name, List.of(Rung.rebuild(def)));
    }

    @Given("the property {string} is set to {int}")
    public void thePropertyIsSetTo(String name, int value) {
        properties.put(name, value);
    }

    @Given("a ladder for {string} over text rungs live, restart and rebuild default {int}")
    public void aLadderOverText(String name, int def) {
        declare(name, List.of(Rung.live(rowsAsText, Parse::integer), Rung.restart(propsAsText, Parse::integer), Rung.rebuild(def)));
    }

    @Given("the text property {string} is set to {string}")
    public void theTextPropertyIsSetTo(String name, String value) {
        textProperties.put(name, value);
    }

    @Given("the text row {string} holds {string}")
    public void theTextRowHolds(String name, String value) {
        textRows.put(name, value);
    }

    @When("a ladder for {string} is declared over text rungs live, restart and rebuild default {int}")
    public void aLadderOverTextIsDeclared(String name, int def) {
        declareNow(name, List.of(Rung.live(rowsAsText, Parse::integer), Rung.restart(propsAsText, Parse::integer), Rung.rebuild(def)));
    }

    @Given("the property {string} is unset")
    public void thePropertyIsUnset(String name) {
        properties.remove(name);
    }

    @Given("the database row {string} holds {int}")
    public void theDatabaseRowHolds(String name, int value) {
        databaseRows.put(name, value);
    }

    @Given("the database row {string} is absent")
    public void theDatabaseRowIsAbsent(String name) {
        databaseRows.remove(name);
    }

    @When("a ladder for {string} is declared with rungs live, restart and rebuild default {int}")
    public void aLadderOverAllThreeIsDeclared(String name, int def) {
        declareNow(name, List.of(Rung.live(rows), Rung.restart(props), Rung.rebuild(def)));
    }

    @When("a ladder for {string} is declared with rungs restart and rebuild default {int}")
    public void aLadderWithoutLiveIsDeclared(String name, int def) {
        declareNow(name, List.of(Rung.restart(props), Rung.rebuild(def)));
    }

    @When("a ladder for {string} is declared with rungs live and restart only")
    public void aLadderWithoutRebuildIsDeclared(String name) {
        declareNow(name, List.of(Rung.live(rows), Rung.restart(props)));
    }

    @When("a ladder for {string} is declared with rungs restart, live and rebuild default {int}")
    public void aLadderOutOfOrderIsDeclared(String name, int def) {
        declareNow(name, List.of(Rung.restart(props), Rung.live(rows), Rung.rebuild(def)));
    }

    @When("a ladder for {string} is declared with rungs live, live and rebuild default {int}")
    public void aLadderWithADoubledLevelIsDeclared(String name, int def) {
        declareNow(name, List.of(Rung.live(rows), Rung.live(rows), Rung.rebuild(def)));
    }

    private void declare(String name, List<Rung<Integer>> rungs) {
        keyName = name;
        this.rungs = new ArrayList<>(rungs);
    }

    private void declareNow(String name, List<Rung<Integer>> rungs) {
        declare(name, rungs);
        declarationFailure = catchThrowable(this::buildLadder);
    }

    /** Resolution is a side-effect-free query, so the scenarios skip the When and the Then asks. */
    private Resolution<Integer> answer() {
        if (answer == null)
            answer = buildLadder().resolution();
        return answer;
    }

    @Then("the ladder answers {int}")
    public void theLadderAnswers(int expected) {
        assertThat(answer().value()).isEqualTo(expected);
    }

    @Then("the answer comes from the {string} level")
    public void theAnswerComesFromTheLevel(String source) {
        assertThat(answer().source()).isEqualTo(source);
    }

    @Then("no level was refused")
    public void noLevelWasRefused() {
        assertThat(answer().rejected()).isEmpty();
    }

    @Then("the {string} level was refused holding {int} because {string}")
    public void theLevelWasRefused(String source, int held, String reason) {
        assertThat(answer().rejected()).contains(new Resolution.Rejected(source, held, reason));
    }

    @Then("the {string} level was refused holding the text {string}")
    public void theLevelWasRefusedHoldingTheText(String source, String held) {
        assertThat(answer().rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.source()).isEqualTo(source);
            assertThat(rejected.value()).isEqualTo(held);
        });
    }

    @When("the ladder is asked {int} times")
    public void theLadderIsAskedTimes(int times) {
        Logger logger = Logger.getLogger(RungLadder.class.getName());
        logger.addHandler(collectWarnings);
        try {
            ConfigLadder<Integer> ladder = buildLadder();
            for (int i = 0; i < times; i++) {
                answer = ladder.resolution();
            }
        } finally {
            logger.removeHandler(collectWarnings);
        }
    }

    @Then("the refusal was logged once")
    public void theRefusalWasLoggedOnce() {
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst().getMessage()).contains("illegal value");
    }

    @Then("the declaration is rejected")
    public void theDeclarationIsRejected() {
        assertThat(declarationFailure).isInstanceOf(IllegalArgumentException.class);
    }

    @Then("the declaration is rejected naming the {string} level")
    public void theDeclarationIsRejectedNamingTheLevel(String level) {
        assertThat(declarationFailure).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(keyName)
                .hasMessageContaining(level);
    }

    @SuppressWarnings("unchecked")
    private ConfigLadder<Integer> buildLadder() {
        return ConfigLadder.of(keyName, gate, rungs.toArray(Rung[]::new));
    }
}
