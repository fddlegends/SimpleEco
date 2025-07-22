package de.simpleeco.listeners;

import de.simpleeco.config.ConfigManager;
import de.simpleeco.currency.BasicCurrency;
import de.simpleeco.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Event-Listener für Spieler-Join-Events
 * 
 * Behandelt:
 * - Initialisierung neuer Spieler-Accounts
 * - Erstellung von Scoreboards beim Join
 * - Cleanup beim Quit
 */
public class PlayerJoinListener implements Listener {
    
    private final BasicCurrency currency;
    private final ConfigManager configManager;
    private final ScoreboardManager scoreboardManager;
    
    public PlayerJoinListener(BasicCurrency currency, ConfigManager configManager, ScoreboardManager scoreboardManager) {
        this.currency = currency;
        this.configManager = configManager;
        this.scoreboardManager = scoreboardManager;
    }
    
    /**
     * Behandelt das Beitreten eines Spielers
     * 
     * @param event Das PlayerJoinEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Spieler-Account initialisieren (falls noch nicht vorhanden)
        currency.initializeAccount(player).thenAccept(success -> {
            if (success) {
                // Scoreboard erstellen (verzögert um sicherzustellen dass der Spieler vollständig geladen ist)
                org.bukkit.Bukkit.getScheduler().runTaskLater(
                    scoreboardManager.getPlugin(), 
                    () -> scoreboardManager.createScoreboard(player), 
                    10L // 0.5 Sekunden Verzögerung
                );
            }
        }).exceptionally(throwable -> {
            // Fehler beim Account-Setup - trotzdem Scoreboard erstellen
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                scoreboardManager.getPlugin(), 
                () -> scoreboardManager.createScoreboard(player), 
                10L
            );
            return null;
        });
    }
    
    /**
     * Behandelt das Verlassen eines Spielers
     * 
     * @param event Das PlayerQuitEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Scoreboard entfernen beim Quit
        scoreboardManager.removeScoreboard(player);
    }
} 