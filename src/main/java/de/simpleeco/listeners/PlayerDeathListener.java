package de.simpleeco.listeners;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.bank.BankManager;
import de.simpleeco.scoreboard.ScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Listener für Spieler-Tod Events
 * 
 * Behandelt den konfigurierbaren Bargeldverlust beim Tod.
 * Nur Bargeld geht verloren, Bank-Guthaben bleibt sicher.
 */
public class PlayerDeathListener implements Listener {
    
    private final SimpleEcoPlugin plugin;
    private final ConfigManager configManager;
    private final BankManager bankManager;
    private final ScoreboardManager scoreboardManager;
    
    public PlayerDeathListener(SimpleEcoPlugin plugin, ConfigManager configManager, 
                              BankManager bankManager, ScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bankManager = bankManager;
        this.scoreboardManager = scoreboardManager;
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        
        // Prüfen ob Todesstrafe aktiviert ist
        if (!configManager.getConfig().getBoolean("deathPenalty.enabled", true)) {
            return;
        }
        
        // Prüfen ob Spieler Berechtigung zum Umgehen hat
        String exemptPermission = configManager.getConfig().getString("deathPenalty.exemptPermission", "simpleeco.death.exempt");
        if (player.hasPermission(exemptPermission)) {
            // Optional: Nachricht senden dass Spieler geschützt ist
            String exemptMessage = configManager.getMessage("deathPenaltyExempt");
            if (exemptMessage != null && !exemptMessage.isEmpty()) {
                player.sendMessage(configManager.getMessage("prefix") + exemptMessage);
            }
            return;
        }
        
        // Prüfen ob nur PvP-Tode berücksichtigt werden sollen
        boolean onlyPvPDeath = configManager.getConfig().getBoolean("deathPenalty.onlyPvPDeath", false);
        if (onlyPvPDeath && player.getKiller() == null) {
            return; // Kein PvP-Tod, keine Strafe
        }
        
        // Bargeld-Verlust berechnen und anwenden
        applyCashLossPenalty(player);
    }
    
    /**
     * Wendet die Bargeld-Verlust-Strafe auf einen Spieler an
     * 
     * @param player Der Spieler der die Strafe erhält
     */
    private void applyCashLossPenalty(Player player) {
        bankManager.getCashBalance(player).thenAccept(currentCash -> {
            if (currentCash <= 0) {
                return; // Kein Bargeld vorhanden
            }
            
            // Verlust-Prozentsatz aus Konfiguration
            double lossPercentage = configManager.getConfig().getDouble("deathPenalty.cashLossPercentage", 0.25);
            double minLoss = configManager.getConfig().getDouble("deathPenalty.minLossAmount", 1.0);
            double maxLoss = configManager.getConfig().getDouble("deathPenalty.maxLossAmount", 10000.0);
            
            // Verlust berechnen
            double calculatedLoss = currentCash * lossPercentage;
            
            // Min/Max-Grenzen anwenden
            calculatedLoss = Math.max(minLoss, Math.min(maxLoss, calculatedLoss));
            
            // Nicht mehr verlieren als vorhanden ist
            final double lossAmount = Math.min(calculatedLoss, currentCash);
            
            // Verlust anwenden
            if (lossAmount > 0) {
                bankManager.removeCashBalance(player, lossAmount).thenAccept(newBalance -> {
                    // Nachricht an Spieler senden
                    String currencySymbol = configManager.getConfig().getString("currency.symbol", "G");
                    String message = configManager.getMessage("deathPenaltyCash",
                        "amount", String.format("%.2f", lossAmount),
                        "currency", currencySymbol);
                    
                    // Nachricht mit Verzögerung senden (nach Respawn)
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.sendMessage(configManager.getMessage("prefix") + message);
                        
                        // Scoreboard aktualisieren
                        if (scoreboardManager != null) {
                            scoreboardManager.updatePlayerScoreboard(player);
                        }
                    }, 20L); // 1 Sekunde Verzögerung
                }).exceptionally(throwable -> {
                    plugin.getLogger().severe("Fehler beim Anwenden der Strafe für " + 
                                            player.getName() + ": " + throwable.getMessage());
                    return null;
                });
            }
            
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Fehler beim Laden des Bargeldes für Strafe von " + 
                                    player.getName() + ": " + throwable.getMessage());
            return null;
        });
    }
} 