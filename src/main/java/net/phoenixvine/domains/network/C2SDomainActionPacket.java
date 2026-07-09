package net.phoenixvine.domains.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.domains.api.DomainAPI;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.ClaimFlag;

import java.util.function.Supplier;

public class C2SDomainActionPacket {

    public enum Action {
        CLAIM,
        UNCLAIM,
        SET_CHUNKLOADED,
        SET_FLAG,
        REQUEST_SYNC
    }

    private final Action action;
    private final String dimension; // empty = player's current dimension
    private final int x;
    private final int z;
    private final String arg; // flag name for SET_FLAG
    private final boolean boolArg; // chunkloaded/flag value
    private final int radius; // for REQUEST_SYNC

    public C2SDomainActionPacket(Action action, String dimension, int x, int z, String arg, boolean boolArg,
                                 int radius) {
        this.action = action;
        this.dimension = dimension;
        this.x = x;
        this.z = z;
        this.arg = arg;
        this.boolArg = boolArg;
        this.radius = radius;
    }

    public static C2SDomainActionPacket claim(int x, int z) {
        return new C2SDomainActionPacket(Action.CLAIM, "", x, z, "", false, 0);
    }

    public static C2SDomainActionPacket unclaim(int x, int z) {
        return new C2SDomainActionPacket(Action.UNCLAIM, "", x, z, "", false, 0);
    }

    public static C2SDomainActionPacket setChunkloaded(int x, int z, boolean value) {
        return new C2SDomainActionPacket(Action.SET_CHUNKLOADED, "", x, z, "", value, 0);
    }

    public static C2SDomainActionPacket setFlag(int x, int z, ClaimFlag flag, boolean value) {
        return new C2SDomainActionPacket(Action.SET_FLAG, "", x, z, flag.name(), value, 0);
    }

    public static C2SDomainActionPacket requestSync(int radius) {
        return new C2SDomainActionPacket(Action.REQUEST_SYNC, "", 0, 0, "", false, radius);
    }

    public C2SDomainActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.dimension = buf.readUtf(256);
        this.x = buf.readInt();
        this.z = buf.readInt();
        this.arg = buf.readUtf(64);
        this.boolArg = buf.readBoolean();
        this.radius = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(dimension, 256);
        buf.writeInt(x);
        buf.writeInt(z);
        buf.writeUtf(arg, 64);
        buf.writeBoolean(boolArg);
        buf.writeVarInt(radius);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (action) {
                case CLAIM -> respond(player, DomainAPI.claim(player, key(player)));
                case UNCLAIM -> respond(player, DomainAPI.unclaim(player, key(player)));
                case SET_CHUNKLOADED -> respond(player, DomainAPI.setChunkloaded(player, key(player), boolArg));
                case SET_FLAG -> {
                    ClaimFlag flag = ClaimFlag.byName(arg);
                    if (flag != null) respond(player, DomainAPI.setFlag(player, key(player), flag, boolArg));
                }
                case REQUEST_SYNC -> DomainNetwork.sendSync(player, radius);
            }

            if (action != Action.REQUEST_SYNC) DomainNetwork.sendSync(player, 8);
        });
        ctx.get().setPacketHandled(true);
    }

    private ChunkKey key(ServerPlayer player) {
        ResourceLocation dim = dimension.isEmpty() ? player.level().dimension().location() :
                new ResourceLocation(dimension);
        return new ChunkKey(dim, x, z);
    }

    private void respond(ServerPlayer player, String result) {
        if (!"ok".equals(result)) {
            player.displayClientMessage(Component.translatable("domains.result." + result), true);
        }
    }
}
