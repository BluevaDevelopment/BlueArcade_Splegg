package net.blueva.arcade.modules.splegg.game;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.modules.splegg.state.SpleggStateRegistry;
import net.blueva.arcade.modules.splegg.state.SpleggArenaState;
import net.blueva.arcade.modules.splegg.support.loadout.SpleggLoadoutService;
import net.blueva.arcade.modules.splegg.support.messaging.SpleggMessagingService;
import net.blueva.arcade.modules.splegg.support.stats.SpleggStatsService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpleggGameManager {

    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final SpleggStatsService statsService;
    private final SpleggLoadoutService loadoutService;
    private final SpleggMessagingService messagingService;
    private final SpleggStateRegistry stateRegistry;

    public SpleggGameManager(ModuleInfo moduleInfo,
                             ModuleConfigAPI moduleConfig,
                             CoreConfigAPI coreConfig,
                             SpleggStatsService statsService) {
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.statsService = statsService;
        this.stateRegistry = new SpleggStateRegistry();
        this.loadoutService = new SpleggLoadoutService(moduleConfig);
        this.messagingService = new SpleggMessagingService(moduleInfo, moduleConfig, coreConfig);
    }

    public void handleStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        stateRegistry.registerArena(context);
        messagingService.sendDescription(context);
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
                                    int secondsLeft) {
        messagingService.sendCountdownTick(context, secondsLeft);
    }

    public void handleCountdownFinish(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        messagingService.sendCountdownFinished(context);
    }

    public void handleGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        startGameTimer(context);

        for (Player player : context.getPlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            loadoutService.giveStartingItems(player);
            loadoutService.applyStartingEffects(player);
            context.getScoreboardAPI().showModuleScoreboard(player);
        }
    }

    private void startGameTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        Integer gameTime = context.getDataAccess().getGameData("basic.time", Integer.class);
        if (gameTime == null || gameTime == 0) {
            gameTime = 180;
        }

        final int[] timeLeft = {gameTime};

        String taskId = "arena_" + arenaId + "_splegg_timer";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (stateRegistry.isEnded(arenaId)) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            timeLeft[0]--;

            List<Player> alivePlayers = context.getAlivePlayers();
            List<Player> allPlayers = context.getPlayers();

            if (alivePlayers.size() <= 1 || timeLeft[0] <= 0) {
                endGameOnce(context);
                return;
            }

            for (Player player : allPlayers) {
                if (!player.isOnline()) {
                    continue;
                }

                messagingService.sendActionBar(context, player, timeLeft[0]);

                Map<String, String> customPlaceholders = getCustomPlaceholders(player);
                customPlaceholders.put("time", String.valueOf(timeLeft[0]));
                customPlaceholders.put("round", String.valueOf(context.getCurrentRound()));
                customPlaceholders.put("round_max", String.valueOf(context.getMaxRounds()));
                customPlaceholders.put("spectators", String.valueOf(context.getSpectators().size()));

                context.getScoreboardAPI().update(player, customPlaceholders);
            }
        }, 0L, 20L);
    }

    private void endGameOnce(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        if (!stateRegistry.markEnded(arenaId)) {
            return;
        }

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        List<Player> alivePlayers = context.getAlivePlayers();
        if (alivePlayers.size() == 1) {
            Player winner = alivePlayers.getFirst();
            context.setWinner(winner);
            handleWin(winner);
        }

        context.endGame();
    }

    public void handleEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();

        context.getSchedulerAPI().cancelArenaTasks(arenaId);

        statsService.recordGamesPlayed(context.getPlayers());
        stateRegistry.clearArena(arenaId);
    }

    public void handleDisable() {
        stateRegistry.cancelAllSchedulers(moduleInfo.getId());
        stateRegistry.clearAll();
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getGameContext(Player player) {
        Integer arenaId = stateRegistry.getArenaId(player);
        if (arenaId == null) {
            return null;
        }
        return stateRegistry.getContext(arenaId);
    }

    public void handleBlockBreak(Player player) {
        statsService.recordBlockBreak(player);
    }

    public void handleWin(Player player) {
        Integer arenaId = stateRegistry.getArenaId(player);
        if (arenaId == null) {
            return;
        }

        if (stateRegistry.markWinner(arenaId, player.getUniqueId())) {
            statsService.recordWin(player);
        }
    }

    public void handlePlayerElimination(Player player) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context == null) {
            return;
        }

        // Don't eliminate spectators
        if (context.getSpectators().contains(player)) {
            return;
        }

        SpleggArenaState state = stateRegistry.getArenaState(context.getArenaId());
        if (state == null || !state.getEliminatedPlayers().add(player.getUniqueId())) {
            return;
        }

        messagingService.broadcastElimination(context, player);
        context.eliminatePlayer(player, moduleConfig.getStringFrom("language.yml", "messages.eliminated"));
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        messagingService.playRespawnSound(context, player);
        loadoutService.applyRespawnEffects(player);
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        if (context != null) {
            placeholders.put("alive", String.valueOf(context.getAlivePlayers().size()));
            placeholders.put("spectators", String.valueOf(context.getSpectators().size()));
        }

        return placeholders;
    }
}
