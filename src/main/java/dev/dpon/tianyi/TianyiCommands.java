package dev.dpon.tianyi;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.dpon.tianyi.entity.TianyiEntity;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class TianyiCommands {
    private TianyiCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tianyi")
                .then(Commands.literal("skin")
                        .then(Commands.argument("id", IntegerArgumentType.integer(0, TianyiEntity.MAX_SKIN_INDEX))
                                .executes(ctx -> setSkin(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "id"))))));
    }

    private static int setSkin(CommandSourceStack source, int variant) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.tianyi_companion.not_player"));
            return 0;
        }
        TianyiEntity tianyi = TianyiCompanionMod.findOwnedTianyi(player);
        if (tianyi == null) {
            source.sendFailure(Component.translatable("command.tianyi_companion.no_companion"));
            return 0;
        }
        tianyi.setSkinIndex(variant);
        source.sendSuccess(() -> Component.translatable("command.tianyi_companion.skin_set", variant), false);
        return 1;
    }
}