package net.phoenixvine.domains.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.api.DomainAPI;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.ClaimFlag;
import net.phoenixvine.domains.data.DomainManager;
import net.phoenixvine.domains.ownership.DomainOwnership;
import net.phoenixvine.guilds.data.Guild;
import net.phoenixvine.guilds.data.GuildManager;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = PhoenixDomains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DomainCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var d = event.getDispatcher();

        var grantPlayer = Commands.argument("player", EntityArgument.player())
                .then(Commands.literal("claimpower")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> grant(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                        LongArgumentType.getLong(ctx, "amount"), true))))
                .then(Commands.literal("chunkload")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> grant(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                        LongArgumentType.getLong(ctx, "amount"), false))));

        var grantGuild = Commands.argument("guildName", StringArgumentType.word())
                .then(Commands.literal("claimpower")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> grantGuildByName(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "guildName"),
                                        LongArgumentType.getLong(ctx, "amount"), true))))
                .then(Commands.literal("chunkload")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> grantGuildByName(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "guildName"),
                                        LongArgumentType.getLong(ctx, "amount"), false))));

        var admin = Commands.literal("admin")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("grant")
                        .then(grantPlayer)
                        .then(Commands.literal("guild").then(grantGuild)));

        var flagNode = Commands.literal("flag")
                .then(Commands.argument("flag", StringArgumentType.word())
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> flag(ctx.getSource(), StringArgumentType.getString(ctx, "flag"),
                                        BoolArgumentType.getBool(ctx, "value")))));

        var chunkloadNode = Commands.literal("chunkload")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> chunkload(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"))));

        d.register(Commands.literal("domain")
                .then(Commands.literal("claim").executes(ctx -> claim(ctx.getSource())))
                .then(Commands.literal("unclaim").executes(ctx -> unclaim(ctx.getSource())))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(chunkloadNode)
                .then(flagNode)
                .then(admin));
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int claim(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String result = DomainAPI.claim(player, currentChunk(player));
        send(source, result);
        return 1;
    }

    private static int unclaim(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String result = DomainAPI.unclaim(player, currentChunk(player));
        send(source, result);
        return 1;
    }

    private static int chunkload(CommandSourceStack source, boolean value) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String result = DomainAPI.setChunkloaded(player, currentChunk(player), value);
        send(source, result);
        return 1;
    }

    private static int flag(CommandSourceStack source, String flagName, boolean value)
                                                                                       throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ClaimFlag flag = ClaimFlag.byName(flagName);
        if (flag == null) {
            source.sendFailure(Component.literal("§cUnknown flag '§f" + flagName + "§c'."));
            return 0;
        }
        String result = DomainAPI.setFlag(player, currentChunk(player), flag, value);
        send(source, result);
        return 1;
    }

    private static int info(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ChunkKey key = currentChunk(player);
        DomainManager manager = DomainManager.get(player.getServer().overworld());
        Optional<Claim> claimOpt = manager.getClaim(key);

        if (claimOpt.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7This chunk is unclaimed."), false);
            return 1;
        }

        Claim claim = claimOpt.get();
        StringBuilder sb = new StringBuilder("§6[Domain] §fOwner: §f" + DomainOwnership.displayName(claim.getOwner()));
        sb.append("\n§7Chunkloaded: ").append(claim.isChunkloaded() ? "§aYes" : "§cNo");
        if (!claim.getFlagOverrides().isEmpty()) {
            sb.append("\n§7Flag overrides:");
            for (Map.Entry<ClaimFlag, Boolean> e : claim.getFlagOverrides().entrySet()) {
                sb.append("\n  §f").append(e.getKey().label()).append(": ")
                        .append(e.getValue() ? "§aON" : "§cOFF");
            }
        }
        Component msg = Component.literal(sb.toString());
        source.sendSuccess(() -> msg, false);
        return 1;
    }

    private static int grant(CommandSourceStack source, ServerPlayer target, long amount, boolean claimPower) {
        UUID token = DomainOwnership.tokenFor(target.getUUID());
        return grantToken(source, token, amount, claimPower);
    }

    private static int grantGuildByName(CommandSourceStack source, String guildName, long amount,
                                        boolean claimPower) {
        GuildManager mgr = GuildManager.get(source.getServer().overworld());
        Optional<Guild> guild = mgr.getGuildByName(guildName);
        if (guild.isEmpty()) {
            source.sendFailure(Component.literal("§cGuild '§f" + guildName + "§c' not found."));
            return 0;
        }
        return grantToken(source, guild.get().getId(), amount, claimPower);
    }

    private static int grantToken(CommandSourceStack source, UUID token, long amount, boolean claimPower) {
        if (claimPower) DomainAPI.grantClaimPower(token, amount);
        else DomainAPI.grantChunkloadPower(token, amount);
        String kind = claimPower ? "claim power" : "chunkload power";
        source.sendSuccess(
                () -> Component.literal(
                        "§aGranted §f" + amount + " §a" + kind + " to §f" + DomainOwnership.displayName(token) + "§a."),
                true);
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ChunkKey currentChunk(ServerPlayer player) {
        return ChunkKey.of(player.level(), player.blockPosition().getX(), player.blockPosition().getZ());
    }

    private static void send(CommandSourceStack source, String resultKey) {
        if ("ok".equals(resultKey)) {
            source.sendSuccess(() -> Component.translatable("domains.result.ok"), false);
        } else {
            source.sendFailure(Component.translatable("domains.result." + resultKey));
        }
    }
}
