package net.phoenixvine.domains.event;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.phoenixvine.domains.PhoenixDomains;
import net.phoenixvine.domains.data.ChunkKey;
import net.phoenixvine.domains.data.Claim;
import net.phoenixvine.domains.data.ClaimFlag;
import net.phoenixvine.domains.data.DomainManager;
import net.phoenixvine.domains.ownership.ClaimPermissions;
import net.phoenixvine.domains.ownership.DomainOwnership;

import java.util.Optional;

/**
 * Enforces claim protection: rank/ally-gated block access (break/place/interact/
 * containers/buckets), non-member damage to owned animals, and the environmental
 * {@link ClaimFlag} toggles (PvP, explosions, mob griefing, spawning, fluid flow).
 *
 * Fire spread has no cancelable vanilla/Forge hook (confirmed against the
 * decompiled {@code FireBlock} source — {@code tryCatchFire} calls
 * {@code Level#setBlockAndUpdate} directly) so {@link ClaimFlag#FIRE_SPREAD} is
 * intentionally not enforced here; a mixin would be required to close that gap.
 */
@Mod.EventBusSubscriber(modid = PhoenixDomains.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClaimProtectionEvents {

    // ── Block access ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        claimAt(event.getLevel(), event.getPos()).ifPresent(claim -> {
            if (!ClaimPermissions.canBuild(player, claim)) {
                event.setCanceled(true);
                notifyBlocked(player, claim);
            }
        });
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        claimAt(event.getLevel(), event.getPos()).ifPresent(claim -> {
            if (!ClaimPermissions.canBuild(player, claim)) {
                event.setCanceled(true);
                notifyBlocked(player, claim);
            }
        });
    }

    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        claimAt(event.getLevel(), event.getPos()).ifPresent(claim -> {
            if (!ClaimPermissions.canBuild(player, claim)) {
                event.setCanceled(true);
                notifyBlocked(player, claim);
            }
        });
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        claimAt(event.getLevel(), event.getPos()).ifPresent(claim -> {
            BlockState state = event.getLevel().getBlockState(event.getPos());
            boolean isContainer = state.getMenuProvider(event.getLevel(), event.getPos()) != null;
            boolean allowed = isContainer ? ClaimPermissions.canOpenContainer(player, claim) :
                    ClaimPermissions.canInteract(player, claim);
            if (!allowed) {
                event.setCanceled(true);
                notifyBlocked(player, claim);
            }
        });
    }

    /** Right-click with a bucket: filling (taking fluid) or emptying (placing it) both need build trust. */
    @SubscribeEvent
    public static void onFillBucket(FillBucketEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof BlockHitResult hit)) return;

        claimAt(event.getLevel(), hit.getBlockPos()).ifPresent(claim -> {
            if (!ClaimPermissions.canBuild(player, claim)) {
                event.setCanceled(true);
                notifyBlocked(player, claim);
            }
        });
    }

    // ── PvP and animal protection ───────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        LivingEntity victim = event.getEntity();

        if (victim instanceof ServerPlayer playerVictim) {
            if (playerVictim.getUUID().equals(attacker.getUUID())) return;
            claimAt(victim.level(), victim.blockPosition()).ifPresent(claim -> {
                if (!ClaimPermissions.canPvp(claim)) event.setCanceled(true);
            });
        } else if (victim instanceof Animal || victim instanceof AbstractVillager) {
            // Protects farm animals and villagers from non-members; hostile mobs are left
            // alone so a visiting ally can still fight off a zombie that wandered in.
            claimAt(victim.level(), victim.blockPosition()).ifPresent(claim -> {
                if (!ClaimPermissions.canBuild(attacker, claim)) {
                    event.setCanceled(true);
                    notifyBlocked(attacker, claim);
                }
            });
        }
    }

    // ── Explosions ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        DomainManager manager = DomainManager.get(level.getServer().overworld());
        event.getAffectedBlocks().removeIf(pos -> {
            ChunkKey key = ChunkKey.of(level, pos.getX(), pos.getZ());
            return manager.getClaim(key).map(c -> !c.getFlag(ClaimFlag.EXPLOSIONS)).orElse(false);
        });
    }

    // ── Mob griefing ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity == null) return;
        claimAt(entity.level(), entity.blockPosition()).ifPresent(claim -> {
            if (!claim.getFlag(ClaimFlag.MOB_GRIEFING)) event.setResult(Event.Result.DENY);
        });
    }

    // ── Spawning ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        claimAt(level, event.getPos()).ifPresent(claim -> {
            MobCategory category = event.getEntityType().getCategory();
            ClaimFlag flag = category == MobCategory.MONSTER ? ClaimFlag.HOSTILE_SPAWNING : ClaimFlag.PASSIVE_SPAWNING;
            if (!claim.getFlag(flag)) event.setResult(Event.Result.DENY);
        });
    }

    // ── Fluids ───────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        claimAt(event.getLevel(), event.getPos()).ifPresent(claim -> {
            if (!claim.getFlag(ClaimFlag.FLUID_FLOW)) event.setCanceled(true);
        });
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private static Optional<Claim> claimAt(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();
        ChunkKey key = ChunkKey.of(serverLevel, pos.getX(), pos.getZ());
        return DomainManager.get(serverLevel.getServer().overworld()).getClaim(key);
    }

    private static void notifyBlocked(ServerPlayer player, Claim claim) {
        String owner = DomainOwnership.displayName(claim.getOwner());
        player.displayClientMessage(Component.translatable("domains.protection.blocked", owner), true);
    }
}
