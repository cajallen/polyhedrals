package net.geryu.polyhedrals;

import net.geryu.polyhedrals.api.PolyhedralsAPI;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class DiceItem extends Item {
    private final int sides;

    public DiceItem(int sides, Properties properties) {
        super(properties);
        this.sides = sides;
    }

    public int getSides() {
        return sides;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F);

        int count = stack.getCount();
        if (!level.isClientSide) {
            PolyhedralsAPI.newRoll(count + "d" + sides)
                    .underhand(player, hand)
                    .item(stack.copyWithCount(1))
                    .canPickup(true)
                    .spawn();
        }
        player.awardStat(Stats.ITEM_USED.get(this), count);
        stack.setCount(0);

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
