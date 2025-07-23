package de.simpleeco.scoreboard;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.currency.BasicCurrency;
import de.simpleeco.bank.BankManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager für Scoreboards zur Anzeige der Spieler-Balance
 * 
 * Verwaltet individuelle Scoreboards für jeden Spieler und aktualisiert
 * die Balance-Anzeige (Bargeld und Bank-Guthaben) in regelmäßigen Intervallen.
 */
public class ScoreboardManager {
    
    private final SimpleEcoPlugin plugin;
    private final ConfigManager configManager;
    private final BasicCurrency currency;
    private final BankManager bankManager;
    
    // Map zum Tracking der Player-Scoreboards (Thread-safe)
    private final ConcurrentHashMap<UUID, Scoreboard> playerScoreboards;
    private final ConcurrentHashMap<UUID, Objective> playerObjectives;
    
    // Rate-Limiting für Scoreboard Updates (Thread-safe)
    private final ConcurrentHashMap<UUID, Long> lastScoreboardUpdate;
    
    // Update-Task
    private BukkitTask updateTask;
    
    public ScoreboardManager(SimpleEcoPlugin plugin, ConfigManager configManager, BasicCurrency currency, BankManager bankManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.currency = currency;
        this.bankManager = bankManager;
        this.playerScoreboards = new ConcurrentHashMap<>();
        this.playerObjectives = new ConcurrentHashMap<>();
        this.lastScoreboardUpdate = new ConcurrentHashMap<>();
        
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
        
        // Update-Tracking entfernen
        lastScoreboardUpdate.remove(playerUUID);
        
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
        updatePlayerScoreboard(player, false);
    }
    
    /**
     * Aktualisiert das Scoreboard eines einzelnen Spielers
     * 
     * @param player Der Spieler
     * @param forceUpdate Wenn true, wird das Update erzwungen auch bei Rate-Limiting
     */
    public void updatePlayerScoreboard(Player player, boolean forceUpdate) {
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
        
        // Rate-Limiting um zu häufige Updates zu vermeiden (außer bei forceUpdate)
        if (!forceUpdate) {
            long currentTime = System.currentTimeMillis();
            Long lastUpdate = lastScoreboardUpdate.get(playerUUID);
            if (lastUpdate != null && (currentTime - lastUpdate) < 500) { // Max alle 0.5 Sekunden
                return;
            }
            lastScoreboardUpdate.put(playerUUID, currentTime);
        }
        
        try {
            // Beide Balances asynchron laden
            CompletableFuture<Double> cashFuture = bankManager.getCashBalance(player);
            CompletableFuture<Double> bankFuture = bankManager.getBankBalance(player);
            
            CompletableFuture.allOf(cashFuture, bankFuture).thenAccept(ignored -> {
                // Sicherstellen dass der Spieler noch online ist
                if (!player.isOnline()) {
                    return;
                }
                
                try {
                    double cashBalance = cashFuture.get();
                    double bankBalance = bankFuture.get();
                    double totalBalance = cashBalance + bankBalance;
                    
                    // Hauptthread für Scoreboard-Updates verwenden
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            // Prüfen ob Objective noch gültig ist (Player könnte disconnect gewesen sein)
                            Objective currentObjective = playerObjectives.get(playerUUID);
                            if (currentObjective != null && currentObjective.getScoreboard() != null) {
                                updateScoreboardDisplay(player, currentObjective, cashBalance, bankBalance, totalBalance);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Fehler beim Aktualisieren der Scoreboard-Anzeige für " + player.getName() + ": " + e.getMessage());
                        }
                    });
                    
                } catch (Exception e) {
                    plugin.getLogger().warning("Fehler beim Abrufen der Kontostände für " + player.getName() + ": " + e.getMessage());
                }
            }).exceptionally(throwable -> {
                plugin.getLogger().warning("Fehler beim Laden der Balances für Scoreboard von " + player.getName() + ": " + throwable.getMessage());
                return null;
            });
            
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Aktualisieren des Scoreboards für " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Aktualisiert die Scoreboard-Anzeige mit den Kontodaten
     * 
     * @param player Der Spieler
     * @param objective Das Scoreboard-Objective
     * @param cashBalance Bargeld-Betrag
     * @param bankBalance Bank-Guthaben
     * @param totalBalance Gesamt-Guthaben
     */
    private void updateScoreboardDisplay(Player player, Objective objective, double cashBalance, double bankBalance, double totalBalance) {
        try {
            // Alle bestehenden Scores löschen (sichere Methode)
            Set<String> entries = new HashSet<>(objective.getScoreboard().getEntries());
            for (String entry : entries) {
                try {
                    objective.getScoreboard().resetScores(entry);
                } catch (Exception e) {
                    // Ignoriere Fehler beim Entfernen einzelner Einträge
                }
            }
            
            // Scoreboard-Zeilen aus Config laden oder Standard verwenden
            List<String> lines = configManager.getConfig().getStringList("scoreboard.lines");
            
            // Falls keine Zeilen konfiguriert sind, Standard-Design verwenden
            if (lines.isEmpty()) {
                lines = getDefaultScoreboardLines();
            }
            
            // Zeilen durchgehen und anzeigen (von unten nach oben)
            int score = lines.size();
            int emptyLineCounter = 0; // Zähler für leere Zeilen um Duplikate zu vermeiden
            
            for (String line : lines) {
                // Platzhalter ersetzen
                String processedLine = line
                    .replace("{cash}", formatAmountForScoreboard(cashBalance))
                    .replace("{bank}", formatAmountForScoreboard(bankBalance))
                    .replace("{total}", formatAmountForScoreboard(totalBalance))
                    .replace("{balance}", formatAmountForScoreboard(cashBalance)) // Für Rückwärtskompatibilität
                    .replace("{currency}", configManager.getCurrencyName())
                    .replace("{symbol}", configManager.getCurrencySymbol())
                    .replace("{player}", player.getName());
                
                // Leere Zeilen behandeln (für bessere Formatierung)
                if (processedLine.trim().isEmpty()) {
                    // Jede leere Zeile muss einzigartig sein, sonst wird sie nicht angezeigt
                    processedLine = " ".repeat(++emptyLineCounter);
                }
                
                // Zeile kürzen falls zu lang (Scoreboard max 40 Zeichen)
                processedLine = truncateScoreboardLine(processedLine);
                
                // Sicherstellen dass die Zeile einzigartig ist (Minecraft Scoreboard Requirement)
                processedLine = ensureUniqueScoreboardEntry(processedLine, objective, score);
                
                // Score setzen
                Score scoreEntry = objective.getScore(processedLine);
                scoreEntry.setScore(score--);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Fehler beim Anzeigen des Scoreboards für " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Formatiert einen Betrag speziell für Scoreboard-Anzeige (kürzere Darstellung)
     * 
     * @param amount Der Betrag
     * @return Formatierter String
     */
    private String formatAmountForScoreboard(double amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000);
        } else {
            return String.format("%.0f", amount);
        }
    }
    
    /**
     * Kürzt eine Scoreboard-Zeile auf die maximale Länge
     * 
     * @param line Die ursprüngliche Zeile
     * @return Gekürzte Zeile
     */
    private String truncateScoreboardLine(String line) {
        if (line.length() <= 40) {
            return line;
        }
        
        // Intelligentes Kürzen: Versuche an Leerzeichen zu trennen
        if (line.length() > 37) {
            String truncated = line.substring(0, 37);
            int lastSpace = truncated.lastIndexOf(' ');
            if (lastSpace > 20) { // Nur wenn genug Text übrig bleibt
                return truncated.substring(0, lastSpace) + "...";
            } else {
                return truncated + "...";
            }
        }
        
        return line.substring(0, 40);
    }
    
    /**
     * Stellt sicher, dass ein Scoreboard-Eintrag einzigartig ist
     * (Minecraft zeigt doppelte Einträge nicht an)
     * 
     * @param line Die ursprüngliche Zeile
     * @param objective Das Scoreboard-Objective
     * @param score Der Score-Wert
     * @return Einzigartige Zeile
     */
    private String ensureUniqueScoreboardEntry(String line, Objective objective, int score) {
        String originalLine = line;
        int attempts = 0;
        
        // Prüfen ob die Zeile bereits existiert
        while (objective.getScoreboard().getEntries().contains(line) && attempts < 10) {
            attempts++;
            // Füge unsichtbare Zeichen hinzu um Einzigartigkeit zu gewährleisten
            if (line.trim().isEmpty()) {
                // Für leere Zeilen: zusätzliche Leerzeichen
                line = " ".repeat(attempts + line.length());
            } else {
                // Für normale Zeilen: unsichtbare Farbcodes am Ende
                String[] colors = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9"};
                line = originalLine + colors[attempts % colors.length] + "§r";
            }
        }
        
        return line;
    }
    
    /**
     * Gibt die Standard-Scoreboard-Zeilen zurück
     * 
     * @return Liste der Standard-Zeilen
     */
    private List<String> getDefaultScoreboardLines() {
        return List.of(
            "§7§m────────────────────",
            "§e§l💰 Finanzen",
            "",
            "§a💵 Bargeld:",
            "§f  {cash}",
            "",
            "§6🏦 Bank:",
            "§f  {bank}",
            "",
            "§e📊 Gesamt:",
            "§f  {total}",
            "",
            "§7§m────────────────────"
        );
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
        lastScoreboardUpdate.clear();
        
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
        
        // Neu starten wenn aktiviert (mit kleiner Verzögerung)
        if (isScoreboardEnabled()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                startUpdateTask();
                
                // Scoreboards für alle Online-Spieler erstellen
                for (Player player : Bukkit.getOnlinePlayers()) {
                    createScoreboard(player);
                }
            }, 5L); // 0.25 Sekunden Verzögerung
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
     * Wird aufgerufen wenn sich die Balance eines Spielers ändert
     * Aktualisiert das Scoreboard sofort
     * 
     * @param player Der Spieler dessen Balance sich geändert hat
     */
    public void onBalanceChanged(Player player) {
        if (player != null && player.isOnline()) {
            updatePlayerScoreboard(player, true); // Force Update
        }
    }
    
    /**
     * Wird aufgerufen wenn sich die Bank-Balance eines Spielers ändert
     * Aktualisiert das Scoreboard sofort
     * 
     * @param player Der Spieler dessen Bank-Balance sich geändert hat
     */
    public void onBankBalanceChanged(Player player) {
        if (player != null && player.isOnline()) {
            updatePlayerScoreboard(player, true); // Force Update
        }
    }
    
    /**
     * Aktualisiert das Scoreboard für einen Spieler basierend auf seiner UUID
     * 
     * @param playerUuid Die UUID des Spielers
     */
    public void onBalanceChanged(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            updatePlayerScoreboard(player, true); // Force Update
        }
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