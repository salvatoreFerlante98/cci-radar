package com.cciradar.command;

import com.cciradar.server.PlayerProgressData;
import com.cciradar.server.VeinSyncManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public final class CciCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("cci_radar")
                        .then(Commands.literal("unlock_tier")
                                .then(Commands.argument("tier", IntegerArgumentType.integer(0))
                                        .executes(ctx -> unlockTierSelf(ctx, IntegerArgumentType.getInteger(ctx, "tier")))
                                )
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .requires(src -> src.hasPermission(2))
                                        .then(Commands.argument("tier", IntegerArgumentType.integer(0))
                                                .executes(ctx -> unlockTierTargets(
                                                        ctx,
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        IntegerArgumentType.getInteger(ctx, "tier")))
                                        )
                                )
                        )
                        .then(Commands.literal("reset")
                                .executes(CciCommands::resetSelf)
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(ctx -> resetTargets(ctx, EntityArgument.getPlayers(ctx, "targets")))
                                )
                        )
                        .then(Commands.literal("debug_fake_veins")
                                .requires(src -> src.hasPermission(2))
                                .executes(CciCommands::debugFakeVeins)
                        )
        );
    }

    private static int unlockTierSelf(CommandContext<CommandSourceStack> ctx, int tier)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerProgressData.get(player.server).unlockTier(player.getUUID(), tier);
        VeinSyncManager.syncToPlayer(player.server, player);
        ctx.getSource().sendSuccess(() -> Component.literal("[CCI Radar] Tier " + tier + " unlocked."), false);
        return 1;
    }

    private static int unlockTierTargets(CommandContext<CommandSourceStack> ctx,
                                          Collection<ServerPlayer> targets, int tier) {
        PlayerProgressData data = PlayerProgressData.get(ctx.getSource().getServer());
        for (ServerPlayer player : targets) {
            data.unlockTier(player.getUUID(), tier);
            VeinSyncManager.syncToPlayer(ctx.getSource().getServer(), player);
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("[CCI Radar] Tier " + tier + " unlocked for " + targets.size() + " player(s)."),
                true);
        return targets.size();
    }

    private static int resetSelf(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerProgressData.get(player.server).resetPlayer(player.getUUID());
        VeinSyncManager.syncToPlayer(player.server, player);
        ctx.getSource().sendSuccess(() -> Component.literal("[CCI Radar] Progress reset."), false);
        return 1;
    }

    private static int resetTargets(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        PlayerProgressData data = PlayerProgressData.get(ctx.getSource().getServer());
        for (ServerPlayer player : targets) {
            data.resetPlayer(player.getUUID());
            VeinSyncManager.syncToPlayer(ctx.getSource().getServer(), player);
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("[CCI Radar] Progress reset for " + targets.size() + " player(s)."),
                true);
        return targets.size();
    }

    private static int debugFakeVeins(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        VeinSyncManager.syncAllFakeVeinsToPlayer(player);
        ctx.getSource().sendSuccess(() -> Component.literal("[CCI Radar] Sent all fake veins (debug)."), false);
        return 1;
    }
}
