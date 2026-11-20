package com.sheepfromheaven.minenow;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * The switches. Each world keeps its own values in its save ({@link WorldSettings}); {@code config/minenow-local.toml}, one file
 * for the whole game, holds the defaults a new world starts with, and is what is read with no world running.
 */
public final class MineNowConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue VILLAGERS_SUMMON_GOLEMS = BUILDER
            .comment("Whether villagers summon iron golems as in vanilla. Off by default: the golemsmith builds a village's golems.")
            .define("villagersSummonGolems", false);

    public static final ModConfigSpec.BooleanValue TRAVELLING_MERCHANTS = BUILDER
            .comment("Whether a village's export goods draw travelling merchants that buy them for new emeralds.")
            .define("travellingMerchants", true);

    public static final ModConfigSpec.BooleanValue INK_AND_QUILL_RECIPES = BUILDER
            .comment("Whether players can craft the cartographer's ink and quill (feather, ink sac and glass bottle), and whether a book and quill takes it in place of an ink sac and a feather. Off by default: vanilla's book and quill recipe stays as it is.")
            .define("inkAndQuillRecipes", false);

    public static final ModConfigSpec.IntValue MAX_TOURS = BUILDER
            .comment("How many merchant tours may run at once in a world. A touring merchant keeps the chunks around it ticking, so each tour costs a few loaded chunks; a merchant over the cap waits at home.")
            .defineInRange("maxMerchantTours", 4, 0, 64);

    private static final Map<Feature, ModConfigSpec.BooleanValue> FEATURES = new EnumMap<>(Feature.class);
    private static final Map<Profession, ModConfigSpec.BooleanValue> PROFESSIONS = new EnumMap<>(Profession.class);

    static {
        BUILDER.comment("Systems of Hearthfolk that can each be switched off. Off, villagers do as in vanilla there.").push("features");
        for (Feature feature : Feature.values()) {
            FEATURES.put(feature, BUILDER.comment(feature.comment).define(feature.key, true));
        }
        BUILDER.pop();
        BUILDER.comment("Professions that can each be switched off. Off, a villager of that profession works and trades as in vanilla.").push("professions");
        for (Profession profession : Profession.values()) {
            PROFESSIONS.put(profession, BUILDER.comment(profession.comment).define(profession.getSerializedName(), true));
        }
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MineNowConfig() {}

    /** The running world's settings, or empty with no server (main menu, or a client connected to a dedicated server). */
    private static java.util.Optional<WorldSettings> world() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? java.util.Optional.empty() : java.util.Optional.of(WorldSettings.of(server));
    }

    /** Read live, so a behaviour can ask on every start check. */
    public static boolean enabled(Feature feature) {
        return world().map(settings -> settings.enabled(feature)).orElseGet(() -> defaultValue(feature));
    }

    /** Sets the running world's value; with no world running, the toml's. */
    public static void set(Feature feature, boolean on) {
        world().ifPresentOrElse(settings -> settings.set(feature, on), () -> setDefault(feature, on));
    }

    /** What the toml says, the value a new world starts with. On until the config has loaded. */
    public static boolean defaultValue(Feature feature) {
        return tomlOr(FEATURES.get(feature), true);
    }

    public static void setDefault(Feature feature, boolean on) {
        FEATURES.get(feature).set(on);
    }

    /** Whether a villager's profession is on in the running world. A profession with no switch is always on. */
    public static boolean professionEnabled(Holder<VillagerProfession> profession) {
        return profession.unwrapKey().map(key -> professionEnabled(key)).orElse(true);
    }

    public static boolean professionEnabled(ResourceKey<VillagerProfession> profession) {
        return Profession.of(profession).map(each -> professionEnabled(each)).orElse(true);
    }

    /** Whether the profession is one of Hearthfolk's own with its switch off. */
    public static boolean switchedOffHearthfolk(Holder<VillagerProfession> profession) {
        return profession.unwrapKey().flatMap(Profession::of).filter(each -> each.losesJobWhenOff() && !professionEnabled(each)).isPresent();
    }

    /** Read live, like {@link #enabled(Feature)}. */
    public static boolean professionEnabled(Profession profession) {
        return world().map(settings -> settings.enabled(profession)).orElseGet(() -> defaultValue(profession));
    }

    /** Sets the running world's value; with no world running, the toml's. */
    public static void set(Profession profession, boolean on) {
        world().ifPresentOrElse(settings -> settings.set(profession, on), () -> setDefault(profession, on));
    }

    /** What the toml says, the value a new world starts with. On until the config has loaded. */
    public static boolean defaultValue(Profession profession) {
        return tomlOr(PROFESSIONS.get(profession), true);
    }

    public static void setDefault(Profession profession, boolean on) {
        PROFESSIONS.get(profession).set(on);
    }

    public static boolean villagersSummonGolems() {
        return world().map(WorldSettings::villagersSummonGolems).orElseGet(() -> tomlOr(VILLAGERS_SUMMON_GOLEMS, false));
    }

    public static void setVillagersSummonGolems(boolean on) {
        world().ifPresentOrElse(settings -> settings.setFlag(WorldSettings.SUMMON_GOLEMS, on), () -> VILLAGERS_SUMMON_GOLEMS.set(on));
    }

    public static boolean travellingMerchants() {
        return world().map(WorldSettings::travellingMerchants).orElseGet(() -> tomlOr(TRAVELLING_MERCHANTS, true));
    }

    public static void setTravellingMerchants(boolean on) {
        world().ifPresentOrElse(settings -> settings.setFlag(WorldSettings.TRAVELLING_MERCHANTS, on), () -> TRAVELLING_MERCHANTS.set(on));
    }

    /**
     * Crafting is decided on the server, which reads the world's value live. The recipe book's display is computed when datapacks
     * load (before the server exists on a new world, so from the toml then; {@code /reload} redoes it), and a 26.3 client only gets
     * those displays from the server, so nothing has to be sent to it.
     */
    public static boolean inkAndQuillRecipes() {
        return world().map(WorldSettings::inkAndQuillRecipes).orElseGet(() -> tomlOr(INK_AND_QUILL_RECIPES, false));
    }

    public static void setInkAndQuillRecipes(boolean on) {
        world().ifPresentOrElse(settings -> settings.setFlag(WorldSettings.INK_AND_QUILL, on), () -> INK_AND_QUILL_RECIPES.set(on));
    }

    public static int maxMerchantTours() {
        return world().map(WorldSettings::maxMerchantTours).orElseGet(() -> tomlOr(MAX_TOURS, 4));
    }

    public static void setMaxMerchantTours(int tours) {
        world().ifPresentOrElse(settings -> settings.setMaxMerchantTours(tours), () -> MAX_TOURS.set(tours));
    }

    /** The toml's value, or {@code fallback} before the config has loaded (recipes are shown to the client early). */
    static <T> T tomlOr(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return fallback;
        }
    }

    public enum Feature implements StringRepresentable {
        VILLAGERS_DEFEND_THE_VILLAGE("villagersDefendTheVillage",
                "Whether armed villagers fight hostiles, man archer posts and gather when the bell rings. Off: every villager panics and hides as in vanilla, and a bell only warns those within earshot."),
        VILLAGERS_EAT_MEALS("villagersEatMeals",
                "Whether villagers eat two meals a day from their pockets and home chest. Off: they never eat, and no food is spent on meals."),
        VILLAGERS_PAY_EACH_OTHER("villagersPayEachOther",
                "Whether villagers pay emeralds when they buy from each other. Off: villagers take what they need from each other for free. Players still pay."),
        VILLAGERS_FOLLOW_EMERALDS("villagersFollowEmeralds",
                "Whether jobless villagers follow a player holding an emerald, so they can be led to a new home. Off: they ignore it."),
        VILLAGER_CHESTS_LOCKED("villagerChestsLocked",
                "Whether a villager's chests, barrels, armor stands and wall displays are locked for players. Off: players can open and take from them, and taking is not theft."),
        VILLAGERS_PREFER_ROADS("villagersPreferRoads",
                "Whether villagers prefer walking on paths and roads, and stroll along them when idle. Off: they walk as in vanilla."),
        VILLAGERS_LEVEL_THROUGH_WORK("villagersLevelThroughWork",
                "Whether villagers earn experience from their work, not only from trading with players. Levels gate a lot (how far a farmer's field reaches, how deep a miner digs, which recipes a craftsman knows), so with this off they progress much more slowly. Off: only trading levels them up, as in vanilla."),
        VILLAGERS_GET_NAMES("villagersGetNames",
                "Whether every villager gets a name, and their births and deaths are announced. Off: new villagers stay nameless as in vanilla.");

        public static final Codec<Feature> CODEC = StringRepresentable.fromEnum(Feature::values);

        private final String key;
        private final String comment;

        Feature(String key, String comment) {
            this.key = key;
            this.comment = comment;
        }

        @Override
        public String getSerializedName() {
            return key;
        }
    }

    /**
     * The professions that can be switched off, one entry per profession with a switch of its own: the switch is saved per
     * world, its toml key is the profession's name, and a villager reads it through {@link #professionEnabled(Holder)}.
     */
    public enum Profession implements StringRepresentable {
        FARMER(Identifier.fromNamespaceAndPath("minecraft", "farmer"),
                "Whether farmers tend the fields beside their composter, and never trample farmland. Off: they farm as in vanilla, with vanilla's trades, and take no part in villager trading."),
        FISHERMAN(Identifier.fromNamespaceAndPath("minecraft", "fisherman"),
                "Whether fishermen go fishing at the water by their barrel. Off: they work and trade as in vanilla, and take no part in villager trading."),
        SHEPHERD(Identifier.fromNamespaceAndPath("minecraft", "shepherd"),
                "Whether shepherds shear the sheep by their loom and open fence gates. Off: they work and trade as in vanilla, and take no part in villager trading."),
        BUTCHER(Identifier.fromNamespaceAndPath("minecraft", "butcher"),
                "Whether butchers keep a herd by their smoker, smoke meat, feed the young and open fence gates. Off: they work and trade as in vanilla, and take no part in villager trading."),
        LIBRARIAN(Identifier.fromNamespaceAndPath("minecraft", "librarian"),
                "Whether librarians make paper, books and enchanted books at their lectern. Off: they bind no books, with vanilla's trades, and take no part in villager trading."),
        CLERIC(Identifier.fromNamespaceAndPath("minecraft", "cleric"),
                "Whether clerics brew potions at their brewing stand, heal villagers and throw splash potions. Off: they brew nothing and fight like any villager, with vanilla's trades, and take no part in villager trading."),
        MASON(Identifier.fromNamespaceAndPath("minecraft", "mason"),
                "Whether masons cut stone for sale and pave dirt paths while a redstone signal reaches their stonecutter. Off: they pave nothing and take no paving orders, with vanilla's trades, and take no part in villager trading."),
        CARTOGRAPHER(Identifier.fromNamespaceAndPath("minecraft", "cartographer"),
                "Whether cartographers draw a map of their village at their cartography table. Off: they draw no map, with vanilla's trades, and take no part in villager trading."),
        TOOLSMITH(Identifier.fromNamespaceAndPath("minecraft", "toolsmith"),
                "Whether toolsmiths craft tools at their smithing table. Off: they craft nothing, show vanilla's trades and take no part in villager trading."),
        FLETCHER(Identifier.fromNamespaceAndPath("minecraft", "fletcher"),
                "Whether fletchers craft at their fletching table and practise archery at a target block. Off: they do neither, show vanilla's trades and take no part in villager trading."),
        ARMORER(Identifier.fromNamespaceAndPath("minecraft", "armorer"),
                "Whether armorers craft armor and shields and dress the armor stands by their anvil. Off: they craft and dress nothing, show vanilla's trades and take no part in villager trading. Stands they dressed stay theirs."),
        WEAPONSMITH(Identifier.fromNamespaceAndPath("minecraft", "weaponsmith"),
                "Whether weaponsmiths craft swords, grind worn blades and hang swords on their wall. Off: they do none of it, show vanilla's trades and take no part in villager trading. Swords already on the wall stay theirs."),
        LEATHERWORKER(Identifier.fromNamespaceAndPath("minecraft", "leatherworker"),
                "Whether leatherworkers craft leather goods, bone-meal tall flowers for dye, dye armor, hang tanning racks and dress armor stands. Off: they do none of it, show vanilla's trades and take no part in villager trading. Racks and stands already up stay theirs."),
        MINER(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "miner"),
                "Whether miners dig mines at their furnace minecarts. Off: miners lose their job and keep their items, and no villager takes a furnace minecart as a job site."),
        FORESTER(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "forester"),
                "Whether foresters fell and replant the trees around their nurseries. Off: foresters lose their job and keep their items, and no villager takes a nursery as a job site."),
        SMELTER(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "smelter"),
                "Whether smelters smelt ore and burn charcoal at their blast furnaces. Off: a villager at a blast furnace is an armorer, and keeps its items."),
        GOLEMSMITH(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "golemsmith"),
                "Whether golemsmiths mend and build iron golems at their workshops. Off: golemsmiths lose their job and keep their items, and no villager takes a golem workshop as a job site. A half-built golem stays where it stands, and with villagersSummonGolems off a village gets no new golems."),
        MERCHANT(Identifier.fromNamespaceAndPath(MineNow.MOD_ID, "merchant"),
                "Whether merchants keep pack mules and go on tours to other villages. Off: merchants lose their job and keep their items, a tour in progress ends, their mules are let go with their cargo given to the merchant, and no villager takes a pack station as a job site.");

        public static final Codec<Profession> CODEC = StringRepresentable.fromEnum(Profession::values);

        private final Identifier profession;
        private final String comment;

        Profession(Identifier profession, String comment) {
            this.profession = profession;
            this.comment = comment;
        }

        /** The switch of this profession, if it has one; a profession without one is always on. */
        public static java.util.Optional<Profession> of(ResourceKey<VillagerProfession> profession) {
            return Arrays.stream(values()).filter(each -> each.profession.equals(profession.identifier())).findFirst();
        }

        /** A profession of Hearthfolk's own has no vanilla work to fall back to, so switched off its villagers lose the job. */
        public boolean isHearthfolk() {
            return profession.getNamespace().equals(MineNow.MOD_ID);
        }

        /** The smelter shares vanilla's armorer job site, so switched off it becomes an armorer instead of losing its job. */
        public boolean losesJobWhenOff() {
            return isHearthfolk() && this != SMELTER;
        }

        public ResourceKey<VillagerProfession> key() {
            return ResourceKey.create(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION, profession);
        }

        /** Its row in the settings menu: {@code minenow.settings.profession.<name>} in en_us.json, and that plus {@code .tooltip}. */
        public String settingsKey() {
            return "profession." + getSerializedName();
        }

        @Override
        public String getSerializedName() {
            return profession.getPath();
        }
    }
}
