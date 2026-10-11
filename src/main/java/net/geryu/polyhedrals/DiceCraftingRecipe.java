package net.geryu.polyhedrals;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class DiceCraftingRecipe extends CustomRecipe {
    private static final Set<DyeColor> LIGHT_DETAIL_DYES = EnumSet.of(DyeColor.BLACK, DyeColor.GRAY, DyeColor.BLUE, DyeColor.BROWN, DyeColor.PURPLE, DyeColor.GREEN);

    public DiceCraftingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !assemble(input, null).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack base = ItemStack.EMPTY;
        List<DyeColor> dyes = new ArrayList<>();
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            DyeColor dye = DyeColor.getColor(stack);
            if (dye != null) {
                dyes.add(dye);
            } else if (base.isEmpty() && (stack.is(Items.BONE) || stack.getItem() instanceof DiceItem)) {
                base = stack;
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (base.isEmpty() || dyes.size() > 2)
            return ItemStack.EMPTY;

        if (base.is(Items.BONE)) {
            // A bone with one dye is a plain recipe, so it shows in the recipe book.
            return dyes.size() == 2 ? dyed(new ItemStack(Polyhedrals.diceItem(20)), dyes) : ItemStack.EMPTY;
        }
        if (dyes.isEmpty()) {
            // A lone die steps down a size, wrapping from the d4 round to the d100.
            List<Integer> sizes = Polyhedrals.DIE_SIDES;
            int index = sizes.indexOf(((DiceItem) base.getItem()).getSides());
            return base.transmuteCopy(Polyhedrals.diceItem(sizes.get((index - 1 + sizes.size()) % sizes.size())), 1);
        }
        return dyed(base.copyWithCount(1), dyes);
    }

    private static ItemStack dyed(ItemStack die, List<DyeColor> dyes) {
        int faceColor = dyes.get(0).getTextureDiffuseColor() & 0xFFFFFF;
        int detailColor = dyes.size() > 1 ? dyes.get(1).getTextureDiffuseColor() & 0xFFFFFF : detailColorFor(dyes.get(0));
        die.set(Polyhedrals.TWO_TONE_COLORS.get(), new DiceColors(faceColor, detailColor));
        return die;
    }

    private static int detailColorFor(DyeColor dye) {
        int toward = LIGHT_DETAIL_DYES.contains(dye) ? 0xFF : 0x00;
        int rgb = dye.getTextureDiffuseColor();
        int detailColor = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            detailColor |= (((rgb >> shift) & 0xFF) + toward) / 2 << shift;
        }
        return detailColor;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Polyhedrals.DICE_CRAFTING.get();
    }
}
