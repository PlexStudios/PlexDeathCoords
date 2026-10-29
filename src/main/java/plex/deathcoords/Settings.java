package plex.deathcoords;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class Settings {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().strict(true).build();
    private final int precision;
    private final boolean showWorld;
    private final boolean showTime;
    private final boolean allowClear;
    private final boolean clickableCoordinates;
    private final boolean respawnReminder;
    private final boolean allowCompass;
    private final Map<String, String> messages;

    private Settings(int precision, boolean showWorld, boolean showTime, boolean allowClear,
                     boolean clickableCoordinates, boolean respawnReminder, boolean allowCompass,
                     Map<String, String> messages) {
        this.precision = precision;
        this.showWorld = showWorld;
        this.showTime = showTime;
        this.allowClear = allowClear;
        this.clickableCoordinates = clickableCoordinates;
        this.respawnReminder = respawnReminder;
        this.allowCompass = allowCompass;
        this.messages = Map.copyOf(messages);
    }

    static Settings load(Path file, Reader defaultsReader) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object defaultData;
        Object configData;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            defaultData = yaml.load(defaultsReader);
            configData = yaml.load(reader);
        } catch (YAMLException exception) {
            throw new IOException("Invalid configuration YAML", exception);
        }
        if (!(defaultData instanceof Map<?, ?> defaults)) {
            throw new IllegalArgumentException("Bundled configuration must be a YAML section");
        }
        if (configData != null && !(configData instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Configuration must be a YAML section");
        }
        Map<Object, Object> config = new LinkedHashMap<>(defaults);
        if (configData instanceof Map<?, ?> overrides) {
            config.putAll(overrides);
        }
        Object precisionValue = config.get("coordinate-precision");
        if (!(precisionValue instanceof Integer precision) || precision < 0 || precision > 6) {
            throw new IllegalArgumentException("coordinate-precision must be an integer from 0 to 6");
        }
        boolean showWorld = booleanSetting(config, "show-world");
        boolean showTime = booleanSetting(config, "show-time");
        boolean allowClear = booleanSetting(config, "allow-clear");
        boolean clickableCoordinates = booleanSetting(config, "clickable-coordinates");
        boolean respawnReminder = booleanSetting(config, "respawn-reminder");
        boolean allowCompass = booleanSetting(config, "allow-compass");
        if (!(config.get("messages") instanceof Map<?, ?> configuredMessages)) {
            throw new IllegalArgumentException("messages must be a YAML section");
        }
        if (!(defaults.get("messages") instanceof Map<?, ?> defaultMessages)) {
            throw new IllegalArgumentException("Bundled message defaults are missing");
        }
        Map<Object, Object> templates = new LinkedHashMap<>(defaultMessages);
        templates.putAll(configuredMessages);
        TagResolver validation = TagResolver.resolver(
                Placeholder.unparsed("world", ""), Placeholder.unparsed("x", ""),
                Placeholder.unparsed("y", ""), Placeholder.unparsed("z", ""),
                Placeholder.unparsed("value", ""), Placeholder.unparsed("command", ""),
                Placeholder.component("world_part", Component.empty()),
                Placeholder.component("time_part", Component.empty()),
                Placeholder.component("ago", Component.empty()),
                Placeholder.component("location", Component.empty()));
        Map<String, String> messages = new HashMap<>();
        for (Object name : defaultMessages.keySet()) {
            String key = (String) name;
            if (!(templates.get(key) instanceof String template)) {
                throw new IllegalArgumentException("messages." + key + " must be text");
            }
            try {
                MINI_MESSAGE.deserialize(template, validation);
            } catch (ParsingException | IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid MiniMessage in messages." + key, exception);
            }
            messages.put(key, template);
        }
        return new Settings(precision, showWorld, showTime, allowClear,
                clickableCoordinates, respawnReminder, allowCompass, messages);
    }

    private static boolean booleanSetting(Map<?, ?> config, String key) {
        if (!(config.get(key) instanceof Boolean value)) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return value;
    }

    boolean allowClear() {
        return allowClear;
    }

    boolean respawnReminder() { return respawnReminder; }

    boolean allowCompass() { return allowCompass; }

    Component respawnMessage(DeathLocation death, long now) {
        return message("respawn-reminder", Placeholder.component("location", coordinates(death, now)));
    }

    Component message(String key, TagResolver... placeholders) {
        return MINI_MESSAGE.deserialize(messages.get(key), placeholders);
    }

    Component coordinates(DeathLocation death, long now) {
        TagResolver values = TagResolver.resolver(
                Placeholder.unparsed("world", death.worldName()),
                Placeholder.unparsed("x", coordinate(death.x())),
                Placeholder.unparsed("y", coordinate(death.y())),
                Placeholder.unparsed("z", coordinate(death.z())));
        Component worldPart = showWorld ? message("world", values) : Component.empty();
        long seconds = Math.max(0, now - death.timestamp()) / 1000;
        String unit;
        long value;
        if (seconds >= 86400) {
            unit = "days";
            value = seconds / 86400;
        } else if (seconds >= 3600) {
            unit = "hours";
            value = seconds / 3600;
        } else if (seconds >= 60) {
            unit = "minutes";
            value = seconds / 60;
        } else {
            unit = "seconds";
            value = seconds;
        }
        Component age = message(unit, Placeholder.unparsed("value", Long.toString(value)));
        Component timePart = showTime ? message("time", values, Placeholder.component("ago", age))
                : Component.empty();
        Component result = message("last-death", values, Placeholder.component("world_part", worldPart),
                Placeholder.component("time_part", timePart), Placeholder.component("ago", age));
        if (clickableCoordinates) {
            String copied = coordinate(death.x()) + " " + coordinate(death.y()) + " " + coordinate(death.z());
            result = result.clickEvent(ClickEvent.copyToClipboard(copied))
                    .hoverEvent(HoverEvent.showText(message("copy-hover")));
        }
        return result;
    }

    private String coordinate(double value) {
        return BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_UP).toPlainString();
    }
}
