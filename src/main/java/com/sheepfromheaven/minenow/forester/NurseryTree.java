package com.sheepfromheaven.minenow.forester;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.Optional;

/** The trees a nursery can raise: the sapling potted in it, which is also what its forester replants. */
public enum NurseryTree implements StringRepresentable {
    OAK("oak", Items.OAK_SAPLING, Blocks.OAK_SAPLING, Blocks.POTTED_OAK_SAPLING),
    SPRUCE("spruce", Items.SPRUCE_SAPLING, Blocks.SPRUCE_SAPLING, Blocks.POTTED_SPRUCE_SAPLING),
    BIRCH("birch", Items.BIRCH_SAPLING, Blocks.BIRCH_SAPLING, Blocks.POTTED_BIRCH_SAPLING),
    JUNGLE("jungle", Items.JUNGLE_SAPLING, Blocks.JUNGLE_SAPLING, Blocks.POTTED_JUNGLE_SAPLING),
    ACACIA("acacia", Items.ACACIA_SAPLING, Blocks.ACACIA_SAPLING, Blocks.POTTED_ACACIA_SAPLING),
    DARK_OAK("dark_oak", Items.DARK_OAK_SAPLING, Blocks.DARK_OAK_SAPLING, Blocks.POTTED_DARK_OAK_SAPLING),
    CHERRY("cherry", Items.CHERRY_SAPLING, Blocks.CHERRY_SAPLING, Blocks.POTTED_CHERRY_SAPLING),
    PALE_OAK("pale_oak", Items.PALE_OAK_SAPLING, Blocks.PALE_OAK_SAPLING, Blocks.POTTED_PALE_OAK_SAPLING),
    MANGROVE("mangrove", Items.MANGROVE_PROPAGULE, Blocks.MANGROVE_PROPAGULE, Blocks.POTTED_MANGROVE_PROPAGULE);

    private final String name;
    private final Item sapling;
    private final Block plant;
    private final Block potted;

    NurseryTree(String name, Item sapling, Block plant, Block potted) {
        this.name = name;
        this.sapling = sapling;
        this.plant = plant;
        this.potted = potted;
    }

    public Item sapling() {
        return sapling;
    }

    public Block plant() {
        return plant;
    }

    public Block potted() {
        return potted;
    }

    public static Optional<NurseryTree> of(Item sapling) {
        return Arrays.stream(values()).filter(tree -> tree.sapling == sapling).findFirst();
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
