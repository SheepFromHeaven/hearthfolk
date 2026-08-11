package com.sheepfromheaven.minenow.effect;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry mapping an effect's {@code "type"} string (as it appears in the structure-levels JSON) to a
 * parser that builds the {@link StructureEffect} from its JSON object. Replaces what used to be a hardcoded
 * {@code switch}, so new effect kinds register themselves here instead of editing the config parser - the
 * seam that keeps "more effects in the future" from touching core loading code.
 *
 * <p>Built-in effect types register in {@link #bootstrap()}, invoked once before the first config load.
 */
public final class StructureEffects {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** A parser returns {@code null} to signal a malformed entry that should be skipped (already logged). */
    @FunctionalInterface
    public interface Parser extends Function<JsonObject, StructureEffect> {}

    private static final Map<String, Parser> PARSERS = new HashMap<>();
    private static boolean bootstrapped = false;

    private StructureEffects() {}

    public static void register(String type, Parser parser) {
        if (PARSERS.putIfAbsent(type, parser) != null) {
            LOGGER.warn("Duplicate structure effect type '{}' registered, keeping the first", type);
        }
    }

    /**
     * Parses a single effect entry, or {@code null} if its type is unknown or the parser rejects it. The
     * caller (config loader) logs context (which structure/level) around a {@code null}.
     */
    public static StructureEffect parse(JsonObject obj) {
        String type = obj.has("type") ? obj.get("type").getAsString() : "";
        var parser = PARSERS.get(type);
        if (parser == null) return null;
        return parser.apply(obj);
    }

    /** Registers the built-in effect types. Idempotent; safe to call more than once. */
    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        register("inhabitantSlots", obj -> new InhabitantSlotsEffect(obj.get("amount").getAsInt()));
        register("productivity", obj -> new ProductivityMultiplierEffect(obj.get("multiplier").getAsDouble()));
        register("territory", obj -> new TerritoryEffect(
                obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt()));
        register("staticWorkArea", obj -> new StaticWorkAreaEffect(obj.get("radius").getAsInt()));
    }
}
