package net.phoenixvine.domains.client;

import net.phoenixvine.domains.network.S2CDomainSyncPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side mirror of nearby claim data and the local player's power pools, updated by {@code S2CDomainSyncPacket}.
 */
public final class ClientDomainCache {

    private ClientDomainCache() {}

    public static List<S2CDomainSyncPacket.ClaimEntry> claims = List.of();
    public static long availableClaimBlocks = 0;
    public static long usedClaimBlocks = 0;
    public static long availableChunkloadBlocks = 0;
    public static long usedChunkloadBlocks = 0;

    /** Bumped every {@link #update}, so consumers (e.g. the Solaris overlay refresh) can detect changes cheaply. */
    public static int version = 0;

    // entryAt() is called once per visible chunk by the Solaris claim-tint overlay every time
    // its map texture rebuilds — with map radii in the dozens of chunks, a linear scan there
    // multiplied out fast enough to visibly stutter the render thread, most noticeably right
    // when clicking to claim/chunkload (that's exactly when a rebuild fires). Indexed by
    // packed chunk coords instead, so lookups are O(1) regardless of how many claims exist.
    private static Map<Long, S2CDomainSyncPacket.ClaimEntry> byChunk = Map.of();

    public static void update(List<S2CDomainSyncPacket.ClaimEntry> claimList, long availClaim, long usedClaim,
                              long availChunkload, long usedChunkload) {
        claims = claimList;
        availableClaimBlocks = availClaim;
        usedClaimBlocks = usedClaim;
        availableChunkloadBlocks = availChunkload;
        usedChunkloadBlocks = usedChunkload;

        Map<Long, S2CDomainSyncPacket.ClaimEntry> index = new HashMap<>(claimList.size() * 2);
        for (S2CDomainSyncPacket.ClaimEntry c : claimList) index.put(packKey(c.x(), c.z()), c);
        byChunk = index;

        version++;
    }

    /** Looks up the cached entry for a chunk, or null if it's unclaimed (or not yet synced). */
    public static S2CDomainSyncPacket.ClaimEntry entryAt(int chunkX, int chunkZ) {
        return byChunk.get(packKey(chunkX, chunkZ));
    }

    private static long packKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
