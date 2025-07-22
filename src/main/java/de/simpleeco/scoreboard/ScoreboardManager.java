package de.simpleeco.scoreboard;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.currency.BasicCurrency;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manager für Scoreboards zur Anzeige der Spieler-Balance
 * 
 * Verwaltet individuelle Scoreboards für jeden Spieler und aktualisiert
 * die Balance-Anzeige in regelmäßigen Intervallen.
 */
public class ScoreboardManager {
    
    private final SimpleEcoPlugin plugin;
    private final ConfigManager configManager;
    private final BasicCurrency currency;
    
    // Map zum Tracking der Player-Scoreboards
    private final Map<UUID, Scoreboard> playerScoreboards;
    private final Map<UUID, Objective> playerObjectives;
    
    // Update-Task
    private BukkitTask updateTask;
    
    public ScoreboardManager(SimpleEcoPlugin plugin, ConfigManager configManager, BasicCurrency currency) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currency = currency;
        this.playerScoreboards = new HashMap<>();
        this.playerObjectives = new HashMap<>();
        
        // Update-Task starten wenn Scoreboard aktiviert ist
        if (isScoreboardEnabled()) {
            startUpdateTask();
        }
    }
    
    /**
     * Prüft ob das Scoreboard in der Config aktiviert ist
     * 
     * @return true wenn aktiviert, false sonst
     */
    public boolean isScoreboardEnabled() {
        return configManager.getConfig().getBoolean("scoreboard.enabled", true);
    }
    
    /**
     * Erstellt und zeigt ein Scoreboard für einen Spieler an
     * 
     * @param player Der Spieler
     */
    public void createScoreboard(Player player) {
        if (!isScoreboardEnabled()) {
            return;
        }
        
        try {
            // Bestehendes Scoreboard entfernen falls vorhanden
            removeScoreboard(player);
            
            // Neues Scoreboard erstellen
            org.bukkit.scoreboard.ScoreboardManager bukkitScoreboardManager = Bukkit.getScoreboardManager();
            if (bukkitScoreboardManager == null) {
                plugin.getLogger().warning("Bukkit ScoreboardManager ist null - kann kein Scoreboard erstellen");
                return;
            }
            
            Scoreboard scoreboard = bukkitScoreboardManager.getNewScoreboard();
            
            // Objective erstellen
            String title = configManager.getConfig().getString("scoreboard.title", "§6§lSimpleEco");
            Objective objective = scoreboard.registerNewObjective("balance", "dummy", title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            
            // Spieler-Daten speichern
            playerScoreboards.put(player.getUniqueId(), scoreboard);
            playerObjectives.put(player.getUniqueId(), objective);
            
            // Scoreboard dem Spieler zuweisen
            player.setScoreboard(scoreboard);
            
            // Initial Balance laden und anzeigen
            updatePlayerScoreboard(player);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Erstellen des Scoreboards für " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Entfernt das Scoreboard eines Spielers
     * 
     * @param player Der Spieler
     */
    public void removeScoreboard(Player player) {
        UUID playerUUID = player.getUniqueId();
        
        // Objective entfernen
        Objective objective = playerObjectives.remove(playerUUID);
        if (objective != null) {
            try {
                objective.unregister();
            } catch (IllegalStateException ignored) {
                // Objective bereits entfernt
            }
        }
        
        // Scoreboard entfernen
        playerScoreboards.remove(playerUUID);
        
        // Standard-Scoreboard zuweisen
        try {
            org.bukkit.scoreboard.ScoreboardManager bukkitScoreboardManager = Bukkit.getScoreboardManager();
            if (bukkitScoreboardManager != null) {
                player.setScoreboard(bukkitScoreboardManager.getMainScoreboard());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Zurücksetzen des Scoreboards für " + player.getName());
        }
    }
    
    /**
     * Aktualisiert das Scoreboard eines einzelnen Spielers
     * 
     * @param player Der Spieler
     */
    public void updatePlayerScoreboard(Player player) {
        if (!isScoreboardEnabled()) {
            return;
        }
        
        UUID playerUUID = player.getUniqueId();
        Objective objective = playerObjectives.get(playerUUID);
        
        if (objective == null) {
            // Scoreboard existiert nicht - erstellen
            createScoreboard(player);
            return;
        }
        
        try {
            // Alle bestehenden Scores löschen
            for (String entry : objective.getScoreboard().getEntries()) {
                objective.getScoreboard().resetScores(entry);
            }
            
            // Balance asynchron laden und Scoreboard aktualisieren
            currency.getBalance(player).thenAccept(balance -> {
                // Sicherstellen dass der Spieler noch online ist
                if (!player.isOnline()) {
                    return;
                }
                
                // Scoreboard-Zeilen aus Config laden
                List<String> lines = configManager.getConfig().getStringList("scoreboard.lines");
                
                // Zeilen durchgehen und anzeigen (von unten nach oben)
                int score = lines.size();
                for (String line : lines) {
                    // Platzhalter ersetzen
                    String processedLine = line
                        .replace("{balance}", currency.formatAmount(balance))
                        .replace("{currency}", configManager.getCurrencyName())
                        .replace("{player}", player.getName());
                    
                    // Score setzen
                    Score scoreEntry = objective.getScore(processedLine);
                    scoreEntry.setScore(score--);
                }
            }).exceptionally(throwable -> {
                plugin.getLogger().severe("Fehler beim Laden der Balance für Scoreboard: " + throwable.getMessage());
                return null;
            });
            
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Aktualisieren des Scoreboards für " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Aktualisiert alle Scoreboards
     */
    public void updateAllScoreboards() {
        if (!isScoreboardEnabled()) {
            return;
        }
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerScoreboard(player);
        }
    }
    
    /**
     * Startet den periodischen Update-Task
     */
    private void startUpdateTask() {
        long updateInterval = configManager.getConfig().getLong("scoreboard.updateInterval", 20);
        
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllScoreboards();
            }
        }.runTaskTimer(plugin, 20L, updateInterval); // Startverzögerung von 1 Sekunde
        
        plugin.getLogger().info("Scoreboard Update-Task gestartet (Intervall: " + updateInterval + " Ticks)");
    }
    
    /**
     * Stoppt den Update-Task
     */
    public void stopUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            plugin.getLogger().info("Scoreboard Update-Task gestoppt");
        }
    }
    
    /**
     * Entfernt alle Scoreboards und stoppt Tasks
     */
    public void shutdown() {
        // Update-Task stoppen
        stopUpdateTask();
        
        // Alle Scoreboards entfernen
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeScoreboard(player);
        }
        
        // Maps leeren
        playerScoreboards.clear();
        playerObjectives.clear();
        
        plugin.getLogger().info("ScoreboardManager heruntergefahren");
    }
    
    /**
     * Lädt die Scoreboard-Konfiguration neu
     */
    public void reload() {
        // Alle bestehenden Scoreboards entfernen
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeScoreboard(player);
        }
        
        // Update-Task stoppen
        stopUpdateTask();
        
        // Neu starten wenn aktiviert
        if (isScoreboardEnabled()) {
            startUpdateTask();
            
            // Scoreboards für alle Online-Spieler erstellen
            for (Player player : Bukkit.getOnlinePlayers()) {
                createScoreboard(player);
            }
        }
        
        plugin.getLogger().info("ScoreboardManager neu geladen");
    }
    
    /**
     * Gibt die Anzahl der aktiven Scoreboards zurück
     * 
     * @return Anzahl aktiver Scoreboards
     */
    public int getActiveScoreboardCount() {
        return playerScoreboards.size();
    }
    
    /**
     * Gibt die Plugin-Instanz zurück
     * 
     * @return Plugin-Instanz
     */
    public SimpleEcoPlugin getPlugin() {
        return plugin;
    }
} 