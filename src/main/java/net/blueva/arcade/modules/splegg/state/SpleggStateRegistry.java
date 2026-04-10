package net.blueva.arcade.modules.splegg.state;

import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpleggStateRegistry {

    private final Map<Integer, SpleggArenaState> arenas = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerArenas = new ConcurrentHashMap<>();

    public void registerArena(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int arenaId = context.getArenaId();
        arenas.put(arenaId, new SpleggArenaState(context));
        context.getPlayers().forEach(player -> playerArenas.put(player.getUniqueId(), arenaId));
    }

    public Integer getArenaId(Player player) {
        return playerArenas.get(player.getUniqueId());
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext(int arenaId) {
        SpleggArenaState state = arenas.get(arenaId);
        return state != null ? state.getContext() : null;
    }

    public SpleggArenaState getArenaState(int arenaId) {
        return arenas.get(arenaId);
    }

    public boolean isEnded(int arenaId) {
        SpleggArenaState state = arenas.get(arenaId);
        return state != null && state.isEnded();
    }

    public boolean markEnded(int arenaId) {
        SpleggArenaState state = arenas.get(arenaId);
        if (state == null || state.isEnded()) {
            return false;
        }

        state.markEnded();
        return true;
    }

    public boolean markWinner(int arenaId, UUID winner) {
        SpleggArenaState state = arenas.get(arenaId);
        if (state == null || state.getWinner() != null) {
            return false;
        }

        state.setWinner(winner);
        return true;
    }

    public void clearArena(int arenaId) {
        SpleggArenaState state = arenas.remove(arenaId);
        if (state != null) {
            state.getContext().getPlayers().forEach(player -> playerArenas.remove(player.getUniqueId()));
        }
    }

    public void clearAll() {
        arenas.clear();
        playerArenas.clear();
    }

    public void cancelAllSchedulers(String moduleId) {
        for (SpleggArenaState state : arenas.values()) {
            state.getContext().getSchedulerAPI().cancelModuleTasks(moduleId);
        }
    }
}
