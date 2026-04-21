package net.blueva.arcade.modules.splegg.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.splegg.game.SpleggGameManager;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class SpleggListener implements Listener {

    private static final String SPLEGG_PROJECTILE_TAG = "bluearcade_splegg_projectile";

    private final SpleggGameManager gameManager;

    public SpleggListener(SpleggGameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (context.getPhase() != GamePhase.PLAYING) {
            if (!context.isInsideBounds(event.getTo())) {
                Location spawn = context.getArenaAPI().getRandomSpawn();
                if (spawn != null) {
                    player.teleport(spawn);
                }
            }
            return;
        }

        if (!context.isInsideBounds(event.getTo())) {
            gameManager.handlePlayerElimination(player);
            return;
        }

        Location boundsMin = context.getArenaAPI().getBoundsMin();
        Location boundsMax = context.getArenaAPI().getBoundsMax();
        double minY = Math.min(boundsMin.getY(), boundsMax.getY());
        if (event.getTo().getY() < minY - 1) {
            gameManager.handlePlayerElimination(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        // In Splegg, players don't break blocks directly — they shoot eggs
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);

        if (context == null || context.getPhase() != GamePhase.PLAYING || !context.isPlayerPlaying(player)) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.DIAMOND_SHOVEL) {
            return;
        }

        event.setCancelled(true);
        Egg egg = player.launchProjectile(Egg.class);
        egg.addScoreboardTag(SPLEGG_PROJECTILE_TAG);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();

        if (!(projectile instanceof Egg)) {
            return;
        }

        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(shooter);
        if (context == null || context.getPhase() != GamePhase.PLAYING || !context.isPlayerPlaying(shooter)) {
            return;
        }

        if (event.getHitBlock() != null) {
            Block hitBlock = event.getHitBlock();

            if (!context.isInsideBounds(hitBlock.getLocation())) {
                return;
            }

            Material hitType = hitBlock.getType();
            if (hitType != Material.AIR && hitType != Material.BARRIER && hitType != Material.BEDROCK) {
                hitBlock.getWorld().playEffect(hitBlock.getLocation(), Effect.STEP_SOUND, hitType);
                hitBlock.setType(Material.AIR);
                gameManager.handleBlockBreak(shooter);
            }
            return;
        }

        if (event.getHitEntity() instanceof Player target) {
            if (!context.isPlayerPlaying(target)) {
                return;
            }

            Vector knockback = projectile.getVelocity();
            if (knockback.lengthSquared() > 0) {
                Vector push = knockback.normalize().multiply(0.6);
                target.setVelocity(target.getVelocity().add(push));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);

        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerEggThrow(PlayerEggThrowEvent event) {
        if (!event.getEgg().getScoreboardTags().contains(SPLEGG_PROJECTILE_TAG)) {
            return;
        }

        event.setHatching(false);
        event.setNumHatches((byte) 0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);

        if (context == null || !context.isPlayerPlaying(player) || context.getPhase() != GamePhase.PLAYING) {
            return;
        }

        event.setCancelled(true);
    }
}
