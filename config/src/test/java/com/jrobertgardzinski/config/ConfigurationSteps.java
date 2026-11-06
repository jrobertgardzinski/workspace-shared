package com.jrobertgardzinski.config;

import com.jrobertgardzinski.config.ladder.ConfigLadder;
import com.jrobertgardzinski.config.ladder.Resolution;
import com.jrobertgardzinski.config.source.live.LiveConfigPort;
import com.jrobertgardzinski.config.source.restart.RestartConfigPort;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class ConfigurationSteps {

    /** A sample rule: an integer floor, never below 5. The constructor is the gate. */
    record Floor(Integer value) implements ConfigValue<Integer> {
        static final String KEY = "sample.floor";
        static final Floor DEFAULT = new Floor(5);

        Floor {
            if (value < 5) throw new IllegalArgumentException("floor must be at least 5");
        }

        @Override
        public String key() {
            return KEY;
        }

        @Override
        public Integer defaultValue() {
            return DEFAULT.value();
        }

        @Override
        public Floor holding(Integer value) {
            return new Floor(value);
        }
    }

    /** A sample flag. */
    record Strict(Boolean value) implements ConfigValue<Boolean> {
        static final Strict DEFAULT = new Strict(false);

        @Override
        public String key() {
            return "sample.strict";
        }

        @Override
        public Boolean defaultValue() {
            return DEFAULT.value();
        }

        @Override
        public Strict holding(Boolean value) {
            return new Strict(value);
        }
    }

    enum ModeName { LENIENT, STRICT }

    /** A sample enum-valued rule. */
    record Mode(ModeName value) implements ConfigValue<ModeName> {
        static final Mode DEFAULT = new Mode(ModeName.LENIENT);

        @Override
        public String key() {
            return "sample.mode";
        }

        @Override
        public ModeName defaultValue() {
            return DEFAULT.value();
        }

        @Override
        public Mode holding(ModeName value) {
            return new Mode(value);
        }
    }

    private final Map<String, String> rows = new HashMap<>();
    private final Map<String, String> properties = new HashMap<>();
    private final LiveConfigPort<String> table = rows::get;
    private final RestartConfigPort<String> deployment = properties::get;
    private Configuration configuration;

    private ConfigLadder<Floor> liveFloor;
    private Floor boundFloor;
    private Strict boundStrict;
    private Mode boundMode;
    private Throwable refusal;

    @Given("a deployment whose settings table and properties are both empty")
    public void aDeployment() {
        configuration = new Configuration(table, deployment);
    }

    @Given("the property {string} is {string}")
    public void thePropertyIs(String name, String value) {
        properties.put(name, value);
    }

    @Given("the settings row {string} is {string}")
    public void theSettingsRowIs(String name, String value) {
        rows.put(name, value);
    }

    @When("the settings row {string} becomes {string}")
    public void theSettingsRowBecomes(String name, String value) {
        rows.put(name, value);
    }

    @When("the settings row {string} is deleted")
    public void theSettingsRowIsDeleted(String name) {
        rows.remove(name);
    }

    @When("the floor is read live over the shipped rule")
    public void theFloorIsReadLive() {
        liveFloor = configuration.liveOver(Floor.DEFAULT);
    }

    @When("the floor is bound over the shipped rule")
    public void theFloorIsBound() {
        refusal = catchThrowable(() -> boundFloor = configuration.boundOver(Floor.DEFAULT));
    }

    @When("the strictness is bound over the shipped rule")
    public void theStrictnessIsBound() {
        refusal = catchThrowable(() -> boundStrict = configuration.boundOver(Strict.DEFAULT));
    }

    @When("the mode is bound over the shipped rule")
    public void theModeIsBound() {
        refusal = catchThrowable(() -> boundMode = configuration.boundOver(Mode.DEFAULT));
    }

    @Then("the floor in force is {int}, from the {string} level")
    public void theFloorInForceIs(int value, String level) {
        Resolution<Floor> resolved = liveFloor.resolution();
        assertThat(resolved.value()).isEqualTo(new Floor(value));
        assertThat(resolved.source()).isEqualTo(level);
    }

    @Then("the report says the {string} level was refused holding {int}")
    public void theReportSaysRefused(String level, int held) {
        assertThat(liveFloor.resolution().rejected()).singleElement().satisfies(rejected -> {
            assertThat(rejected.source()).isEqualTo(level);
            assertThat(rejected.value()).isEqualTo(held);
        });
    }

    @Then("the bound floor is {int}")
    public void theBoundFloorIs(int value) {
        assertThat(refusal).isNull();
        assertThat(boundFloor).isEqualTo(new Floor(value));
    }

    @Then("the bound floor is still {int}")
    public void theBoundFloorIsStill(int value) {
        assertThat(boundFloor).isEqualTo(new Floor(value));
    }

    @Then("the bound strictness is {word}")
    public void theBoundStrictnessIs(String value) {
        assertThat(refusal).isNull();
        assertThat(boundStrict).isEqualTo(new Strict(Boolean.parseBoolean(value)));
    }

    @Then("the bound mode is {word}")
    public void theBoundModeIs(String value) {
        assertThat(refusal).isNull();
        assertThat(boundMode).isEqualTo(new Mode(ModeName.valueOf(value)));
    }

    @Then("the declaration is refused naming {string} and the {string} level")
    public void theDeclarationIsRefused(String key, String level) {
        assertThat(refusal).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(key)
                .hasMessageContaining(level);
    }

    @Then("the catalogue lists {string}")
    public void theCatalogueLists(String key) {
        assertThat(configuration.liveKeys()).containsKey(key);
        assertThat(configuration.liveKey(key)).isPresent();
    }

    @Then("the catalogue does not list {string}")
    public void theCatalogueDoesNotList(String key) {
        assertThat(configuration.liveKeys()).doesNotContainKey(key);
        assertThat(configuration.liveKey(key)).isEmpty();
    }

    @Then("{string} told {string} holds {int}")
    public void toldHolds(String key, String text, int value) {
        assertThat(configuration.liveKey(key).orElseThrow().holding(text)).isEqualTo(new Floor(value));
    }

    @Then("{string} told {string} is refused because {string}")
    public void toldIsRefused(String key, String text, String reason) {
        LiveKey live = configuration.liveKey(key).orElseThrow();
        assertThat(catchThrowable(() -> live.holding(text)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(reason);
    }
}
