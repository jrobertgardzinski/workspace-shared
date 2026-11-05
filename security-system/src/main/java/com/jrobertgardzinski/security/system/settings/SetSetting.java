package com.jrobertgardzinski.security.system.settings;

import com.jrobertgardzinski.config.ConfigValue;
import com.jrobertgardzinski.config.LiveKey;

import java.util.Optional;

/**
 * An ADMIN's decision on any live rule, made while the system runs: the key names the rule, the
 * text is what the rule is told. There is no code per rule here - the catalogue supplies the
 * rule's own parser and gate, the same ones the ladder reads a row with, so a value the rule
 * would refuse on the way in is refused at the door and nothing is written, and a key nobody
 * declared cannot be written at all. What is written is the value in its canonical text, under
 * the rule's own key, the one the ladder reads.
 */
public class SetSetting {

    private final SettingCatalog catalogue;
    private final SettingsRepository settingsRepository;

    public SetSetting(
                    SettingCatalog catalogue,
                    SettingsRepository settingsRepository) {
        this.catalogue = catalogue;
        this.settingsRepository = settingsRepository;
    }

    public sealed interface Result {

        /** The rule's value as held. */
        record Accepted(Object value) implements Result {}

        /** The gate's words. */
        record Refused(String reason) implements Result {}

        record UnknownKey(String reason) implements Result {}
    }

    public Result execute(String key, String text) {

        Optional<LiveKey> liveKey = catalogue.find(key);

        if (
                liveKey.isEmpty()
        ) {
            return new Result.UnknownKey(
                    "no live rule goes by '" + key + "'");
        }

        ConfigValue<?> held;
        try {
            held = liveKey.get().holding(text);
        } catch (IllegalArgumentException refused) {
            return new Result.Refused(
                    refused.getMessage());
        }

        settingsRepository.save(
                key,
                String.valueOf(held.value()));

        return new Result.Accepted(
                held.value());
    }
}
