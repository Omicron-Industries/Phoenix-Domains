package net.phoenixvine.domains.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.phoenixvine.domains.client.ClientDomainCache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CDomainSyncPacket {

    public record ClaimEntry(int x, int z, String ownerName, int color, boolean chunkloaded) {}

    private final List<ClaimEntry> claims;
    private final long availableClaimBlocks;
    private final long usedClaimBlocks;
    private final long availableChunkloadBlocks;
    private final long usedChunkloadBlocks;

    public S2CDomainSyncPacket(List<ClaimEntry> claims, long availableClaimBlocks, long usedClaimBlocks,
                               long availableChunkloadBlocks, long usedChunkloadBlocks) {
        this.claims = claims;
        this.availableClaimBlocks = availableClaimBlocks;
        this.usedClaimBlocks = usedClaimBlocks;
        this.availableChunkloadBlocks = availableChunkloadBlocks;
        this.usedChunkloadBlocks = usedChunkloadBlocks;
    }

    public S2CDomainSyncPacket(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ClaimEntry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new ClaimEntry(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readInt(),
                    buf.readBoolean()));
        }
        this.claims = list;
        this.availableClaimBlocks = buf.readLong();
        this.usedClaimBlocks = buf.readLong();
        this.availableChunkloadBlocks = buf.readLong();
        this.usedChunkloadBlocks = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(claims.size());
        for (ClaimEntry c : claims) {
            buf.writeVarInt(c.x());
            buf.writeVarInt(c.z());
            buf.writeUtf(c.ownerName(), 64);
            buf.writeInt(c.color());
            buf.writeBoolean(c.chunkloaded());
        }
        buf.writeLong(availableClaimBlocks);
        buf.writeLong(usedClaimBlocks);
        buf.writeLong(availableChunkloadBlocks);
        buf.writeLong(usedChunkloadBlocks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> applyOnClient(this)));
        ctx.get().setPacketHandled(true);
    }

    private static void applyOnClient(S2CDomainSyncPacket pkt) {
        if (Minecraft.getInstance().player == null) return;
        ClientDomainCache.update(pkt.claims, pkt.availableClaimBlocks, pkt.usedClaimBlocks,
                pkt.availableChunkloadBlocks, pkt.usedChunkloadBlocks);
    }
}
