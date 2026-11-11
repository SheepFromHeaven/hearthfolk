package com.sheepfromheaven.minenow.cartographer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sheepfromheaven.minenow.MineNowConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A shapeless recipe that only works while the {@code inkAndQuillRecipes} switch is {@code when_enabled} (on or off): the quill's
 * recipe is on, the book and quill taking the quill is on, and vanilla's own book and quill recipe, overridden under its own id, is off.
 * The switch is read when crafting, so it needs no reload.
 */
public final class SwitchedShapelessRecipe extends NormalCraftingRecipe {
    public static final MapCodec<SwitchedShapelessRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
            Codec.lazyInitialized(() -> Ingredient.CODEC.listOf(1, 9)).fieldOf("ingredients").forGetter(recipe -> recipe.ingredients),
            Codec.BOOL.fieldOf("when_enabled").forGetter(recipe -> recipe.whenEnabled)
    ).apply(i, SwitchedShapelessRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchedShapelessRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
            ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), recipe -> recipe.ingredients,
            ByteBufCodecs.BOOL, recipe -> recipe.whenEnabled,
            SwitchedShapelessRecipe::new);

    public static final RecipeSerializer<SwitchedShapelessRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ItemStackTemplate result;
    private final List<Ingredient> ingredients;
    private final boolean whenEnabled;

    public SwitchedShapelessRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ItemStackTemplate result, List<Ingredient> ingredients, boolean whenEnabled) {
        super(commonInfo, bookInfo);
        this.result = result;
        this.ingredients = ingredients;
        this.whenEnabled = whenEnabled;
    }

    private boolean active() {
        return MineNowConfig.inkAndQuillRecipes() == whenEnabled;
    }

    @Override
    public RecipeSerializer<SwitchedShapelessRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.create(ingredients);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!active() || input.ingredientCount() != ingredients.size()) return false;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty()) items.add(stack);
        }
        return net.neoforged.neoforge.common.util.RecipeMatcher.findMatches(items, ingredients) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return result.create();
    }

    @Override
    public List<RecipeDisplay> display() {
        if (!active()) return List.of();
        return List.of(new ShapelessCraftingRecipeDisplay(ingredients.stream().map(Ingredient::display).toList(),
                new SlotDisplay.ItemStackSlotDisplay(result), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }
}
