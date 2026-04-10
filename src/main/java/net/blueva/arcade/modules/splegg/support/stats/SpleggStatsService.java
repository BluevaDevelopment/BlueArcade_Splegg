package net.blueva.arcade.modules.splegg.support.stats;

import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import org.bukkit.entity.Player;

import java.util.Collection;

public class SpleggStatsService {

    private final StatsAPI statsAPI;
    private final ModuleInfo moduleInfo;
    private final ModuleConfigAPI moduleConfig;

    public SpleggStatsService(StatsAPI statsAPI, ModuleInfo moduleInfo, ModuleConfigAPI moduleConfig) {
        this.statsAPI = statsAPI;
        this.moduleInfo = moduleInfo;
        this.moduleConfig = moduleConfig;
    }

    public void registerStats() {
        if (statsAPI == null) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", moduleConfig.getStringFrom("language.yml", "stats.labels.wins", "Wins"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.wins", "Splegg wins"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", moduleConfig.getStringFrom("language.yml", "stats.labels.games_played", "Games Played"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.games_played", "Splegg games played"), StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("blocks_broken", moduleConfig.getStringFrom("language.yml", "stats.labels.blocks_broken", "Blocks broken"), moduleConfig.getStringFrom("language.yml", "stats.descriptions.blocks_broken", "Blocks broken by eggs"), StatScope.MODULE));
    }

    public void recordBlockBreak(Player player) {
        if (statsAPI == null) {
            return;
        }

        statsAPI.addModuleStat(player, moduleInfo.getId(), "blocks_broken", 1);
    }

    public void recordWin(Player player) {
        if (statsAPI == null) {
            return;
        }

        statsAPI.addModuleStat(player, moduleInfo.getId(), "wins", 1);
        statsAPI.addGlobalStat(player, "wins", 1);
    }

    public void recordGamesPlayed(Collection<Player> players) {
        if (statsAPI == null) {
            return;
        }

        for (Player player : players) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "games_played", 1);
        }
    }
}
