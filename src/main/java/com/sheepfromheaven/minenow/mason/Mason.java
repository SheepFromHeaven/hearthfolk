package com.sheepfromheaven.minenow.mason;

import com.sheepfromheaven.minenow.MineNow;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/** The vanilla mason: cuts stone at its stonecutter (trade file) and, while the stonecutter is powered, paves roads ({@link PaveRoads}). */
public final class Mason {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MineNow.MOD_ID);

    /** Road blocks a mason laid itself with cobblestone or gravel: the only ones it later lays stone on, never the player's. */
    static final Supplier<AttachmentType<List<BlockPos>>> PAVED = ATTACHMENT_TYPES.register("paved",
            () -> AttachmentType.<List<BlockPos>>builder(() -> List.of())
                    .serialize(BlockPos.CODEC.listOf().fieldOf("blocks"))
                    .build());

    private Mason() {}

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
