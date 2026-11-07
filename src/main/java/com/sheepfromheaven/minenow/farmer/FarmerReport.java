package com.sheepfromheaven.minenow.farmer;

import com.mojang.brigadier.CommandDispatcher;
import com.sheepfromheaven.minenow.villager.HomeStorage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** {@code /minenow farmer}: why the nearest farmer is or isn't working. */
public final class FarmerReport {
    private static final int SEARCH_RADIUS = 16;

    private FarmerReport() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("minenow").then(Commands.literal("farmer").executes(context -> {
            CommandSourceStack source = context.getSource();
            ServerLevel level = source.getLevel();
            Optional<Villager> farmer = level.getEntitiesOfClass(Villager.class, new AABB(BlockPos.containing(source.getPosition())).inflate(SEARCH_RADIUS),
                            FarmerRules::isFarmer).stream()
                    .min(Comparator.comparingDouble(villager -> villager.distanceToSqr(source.getPosition())));
            if (farmer.isEmpty()) {
                source.sendFailure(Component.literal("No farmer within " + SEARCH_RADIUS + " blocks"));
                return 0;
            }
            describe(level, farmer.get()).forEach(line -> source.sendSuccess(() -> line, false));
            return 1;
        })));
    }

    public static List<Component> describe(ServerLevel level, Villager farmer) {
        List<String> lines = new ArrayList<>();
        int villagerLevel = farmer.getVillagerData().level();
        lines.add(farmer.getDisplayName().getString() + ", farmer level " + villagerLevel);

        String activity = farmer.getBrain().getActiveNonCoreActivity().map(Activity::getName).orElse("none");
        boolean tending = farmer.getBrain().getRunningBehaviors().stream().anyMatch(TendFields.class::isInstance);
        lines.add("Activity: " + activity + (activity.equals("work") ? "" : " (farmers only work from early morning to mid-afternoon)")
                + (tending ? ", tending its field" : ", not tending"));

        SimpleContainer inventory = farmer.getInventory();
        lines.add(inventory.getItems().stream().filter(Hoes::isHoe).findFirst()
                .map(hoe -> "Hoe: " + hoe.getHoverName().getString() + " " + (hoe.getMaxDamage() - hoe.getDamageValue()) + "/" + hoe.getMaxDamage())
                .orElse("Hoe: no hoe, it won't farm until it gets one"));
        long used = inventory.getItems().stream().filter(stack -> !stack.isEmpty()).count();
        lines.add("Inventory: " + used + "/" + inventory.getContainerSize() + " slots used");

        lines.add(HomeStorage.storageIn(level, farmer)
                .map(pos -> "Home chest: " + pos.toShortString())
                .orElse(farmer.getBrain().hasMemoryValue(MemoryModuleType.HOME)
                        ? "Home chest: no home chest, it needs a free chest within " + HomeStorage.RADIUS + " blocks of its bed"
                        : "Home chest: no home chest, it has no bed yet"));

        Optional<BlockPos> composter = farmer.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(site -> site.dimension() == level.dimension()).map(GlobalPos::pos);
        if (composter.isEmpty()) {
            lines.add("Field: no composter claimed");
        } else {
            int radius = Field.radiusFor(villagerLevel);
            int ripe = 0, empty = 0, tillable = 0;
            List<BlockPos> ground = Field.ground(level, composter.get(), radius);
            for (BlockPos pos : ground) {
                BlockState above = level.getBlockState(pos.above());
                if (above.getBlock() instanceof CropBlock crop && crop.isMaxAge(above)) ripe++;
                else if (Field.isFarmland(level.getBlockState(pos))) { if (above.isAir()) empty++; }
                else tillable++;
            }
            lines.add("Field: " + ground.size() + " blocks within " + radius + " of " + composter.get().toShortString()
                    + ": " + ripe + " ripe, " + empty + " to plant, " + tillable + " to till");
        }
        return lines.stream().map(line -> (Component) Component.literal(line)).toList();
    }
}
