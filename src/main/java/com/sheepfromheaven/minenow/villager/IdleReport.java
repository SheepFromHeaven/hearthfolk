package com.sheepfromheaven.minenow.villager;

import com.mojang.brigadier.CommandDispatcher;
import com.sheepfromheaven.minenow.farmer.FarmerReport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/** {@code /minenow villager} (also {@code /minenow farmer}): why the nearest working villager is or isn't working, for every profession. */
public final class IdleReport {
    private static final int SEARCH_RADIUS = 16;

    /** Extra lines a profession adds, e.g. the farmer's field. */
    private static final Map<ResourceKey<VillagerProfession>, BiConsumer<Villager, List<String>>> EXTRAS = new java.util.HashMap<>();

    private IdleReport() {}

    public static void addExtra(ResourceKey<VillagerProfession> profession, BiConsumer<Villager, List<String>> extra) {
        EXTRAS.put(profession, extra);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("minenow")
                .then(Commands.literal("villager").executes(context -> run(context.getSource(), false)))
                .then(Commands.literal("farmer").executes(context -> run(context.getSource(), true))));
    }

    private static int run(CommandSourceStack source, boolean farmersOnly) {
        ServerLevel level = source.getLevel();
        Optional<Villager> villager = level.getEntitiesOfClass(Villager.class, new AABB(BlockPos.containing(source.getPosition())).inflate(SEARCH_RADIUS),
                        v -> !v.isBaby() && TradeTable.forVillager(v).isPresent() && (!farmersOnly || v.getVillagerData().profession().is(VillagerProfession.FARMER))).stream()
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(source.getPosition())));
        if (villager.isEmpty()) {
            source.sendFailure(Component.literal("No " + (farmersOnly ? "farmer" : "working villager") + " within " + SEARCH_RADIUS + " blocks"));
            return 0;
        }
        describe(level, villager.get()).forEach(line -> source.sendSuccess(() -> line, false));
        return 1;
    }

    public static List<Component> describe(ServerLevel level, Villager villager) {
        List<Component> lines = new ArrayList<>();
        String profession = villager.getVillagerData().profession().unwrapKey().map(key -> key.identifier().getPath()).orElse("villager");
        lines.add(ReportBook.heading("General"));
        add(lines, villager.getDisplayName().getString() + ", " + profession + " level " + villager.getVillagerData().level());

        String activity = villager.getBrain().getActiveNonCoreActivity().map(Activity::getName).orElse("none");
        List<String> doing = villager.getBrain().getRunningBehaviors().stream()
                .filter(behavior -> behavior.getClass().getName().startsWith("com.sheepfromheaven.minenow"))
                .map(behavior -> behavior.getClass().getSimpleName()).toList();
        add(lines, "Activity: " + activity + (activity.equals("work") ? "" : " (villagers only work from early morning to mid-afternoon)")
                + (doing.isEmpty() ? ", not working at the moment" : ", running " + String.join(", ", doing)));
        long used = villager.getInventory().getItems().stream().filter(stack -> !stack.isEmpty()).count();
        add(lines, "Inventory: " + used + "/" + villager.getInventory().getContainerSize() + " slots used");

        lines.add(ReportBook.heading("Job"));
        add(lines, JobLoss.describe(level, villager));
        List<String> job = new ArrayList<>();
        TradeTable.forVillager(villager).filter(IdleReason::requiresTool).ifPresent(table -> {
            Optional<ItemStack> tool = villager.getInventory().getItems().stream().filter(stack -> Unloading.isTool(villager, stack)).findFirst();
            job.add(tool.map(held -> "Tool: " + held.getHoverName().getString() + " " + (held.getMaxDamage() - held.getDamageValue()) + "/" + held.getMaxDamage())
                    .orElse("Tool: " + (Unloading.ownsTool(level, villager) ? "none on it, it fetches one from its chest" : IdleReason.needsToolNow(level, villager) ? "no tool of its trade, it won't do the work that needs one until it gets one" : "no tool of its trade, it does what it can without one and buys one when it can")));
        });
        BiConsumer<Villager, List<String>> extra = villager.getVillagerData().profession().unwrapKey().map(EXTRAS::get).orElse(null);
        if (extra != null) extra.accept(villager, job);
        job.forEach(line -> add(lines, line));
        List<IdleReason> reasons = IdleReason.of(level, villager);
        add(lines, reasons.isEmpty() ? "Nothing is stopping it" : "Stopping it: " + String.join("; ", reasons.stream().map(reason -> reason.message).toList()));

        lines.add(ReportBook.heading("Storage"));
        add(lines, HomeStorage.storageIn(level, villager)
                .map(pos -> "Home chest: " + pos.toShortString())
                .orElse(villager.getBrain().hasMemoryValue(MemoryModuleType.HOME)
                        ? "Home chest: no home chest, it needs a free chest within " + HomeStorage.RADIUS + " blocks of its bed"
                        : "Home chest: no home chest, it has no bed yet"));
        add(lines, "Workshop chests: " + Workshop.chestsOf(level, villager).size());

        lines.add(ReportBook.heading("Breeding"));
        BreedingReport.lines(level, villager).forEach(line -> add(lines, line));
        return lines;
    }

    private static void add(List<Component> lines, String text) {
        lines.add(Component.literal(text));
    }
}
