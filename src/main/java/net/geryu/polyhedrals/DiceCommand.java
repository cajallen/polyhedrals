package net.geryu.polyhedrals;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.geryu.polyhedrals.api.PolyhedralsAPI;
import net.geryu.polyhedrals.api.RollBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Polyhedrals.MODID)
public final class DiceCommand {
    private static final int CLEAR_DELAY_TICKS = 50;

    private DiceCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var rollExpression = Commands.argument("expression", StringArgumentType.greedyString())
                .executes(DiceCommand::rollDice);
        event.getDispatcher().register(Commands.literal("polyhedrals")
                .then(Commands.literal("roll").then(rollExpression))
                .then(Commands.literal("config")
                    .then(Commands.literal("default_colors")
                        .then(Commands.argument("face_color", StringArgumentType.word())
                            .then(Commands.argument("detail_color", StringArgumentType.word())
                                .executes(context -> setDefaultColors(
                                        context.getSource().getPlayerOrException(),
                                        hexColor(context, "face_color"),
                                        hexColor(context, "detail_color")))))))
                .then(Commands.literal("clear")
                        .executes(DiceCommand::clearDice)));
        event.getDispatcher().register(Commands.literal("roll").then(rollExpression));
        event.getDispatcher().register(Commands.literal("r").then(rollExpression));
    }

    private static int hexColor(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        String value = StringArgumentType.getString(context, name);
        if (!value.matches("[0-9a-fA-F]{6}"))
            throw new DynamicCommandExceptionType(input -> Component.literal("Expected a hex color, got " + input))
                .create(value);
        return Integer.parseInt(value, 16);
    }

    private static int setDefaultColors(ServerPlayer player, int faceColor, int detailColor) {
        player.setData(Polyhedrals.DEFAULT_COLORS, new DiceColors(faceColor, detailColor));
        player.sendSystemMessage(Component.literal("Defaults updated"));
        return 1;
    }

    private static int rollDice(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (PolyhedralsConfig.WAIT_FOR_DICE.get() && RollManager.isRolling(player)) {
            context.getSource().sendFailure(Component.literal("Wait for pending die. Use /polyhedrals clear if this is not expected."));
            return 0;
        }
        String expression = StringArgumentType.getString(context, "expression");
        if (PolyhedralsAPI.newRoll(expression).underhand(player, InteractionHand.MAIN_HAND).spawn() != null)
            return 1;
        context.getSource().sendFailure(Component.literal("Cannot roll " + expression
                + ": expected something like 4d6h3+2, throwing 1 to " + RollBuilder.MAX_DICE
                + " dice of 1 to " + RollBuilder.MAX_SIDES + " sides."));
        return 0;
    }

    private static int clearDice(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!RollManager.queueClear(player, CLEAR_DELAY_TICKS)) {
            context.getSource().sendFailure(Component.literal("No dice."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Clearing dice..."));
        return 1;
    }
}
