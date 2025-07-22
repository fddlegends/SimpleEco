package de.simpleeco.listeners;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.currency.BasicCurrency;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Event-Listener für Spieler-Beitritt
 * 
 * Erstellt automatisch ein Konto mit Startguthaben für neue Spieler.
 */
public class PlayerJoinListener implements Listener {
    
    private final BasicCurrency currency;
    private final ConfigManager configManager;
    private final SimpleEcoPlugin plugin;
    
    public PlayerJoinListener(BasicCurrency currency, ConfigManager configManager) {
        this.currency = currency;
        this.configManager = configManager;
        this.plugin = SimpleEcoPlugin.getInstance();
    }
    
    /**
     * Behandelt das PlayerJoinEvent
     * 
     * Überprüft ob der Spieler bereits ein Konto hat und erstellt
     * bei Bedarf ein neues Konto mit dem konfigurierten Startguthaben.
     * 
     * @param event Das PlayerJoinEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Asynchron prüfen ob Spieler bereits ein Konto hat
        currency.hasAccount(player).thenAccept(hasAccount -> {
            if (!hasAccount) {
                // Neues Konto mit Startguthaben erstellen
                currency.createAccount(player).thenRun(() -> {
                    // Willkommensnachricht mit Startguthaben senden
                    double startBalance = configManager.getStartBalance();
                    String currencyName = configManager.getCurrencyName();
                    
                    String welcomeMessage = String.format(
                        "§a§lWillkommen! §7Du hast §e%s §7als Startguthaben erhalten!",
                        currency.formatAmount(startBalance)
                    );
                    
                                         // Nachricht im nächsten Tick senden (nach vollständigem Join)
                     player.getServer().getScheduler().runTaskLater(
                         plugin, 
                         () -> {
                             if (player.isOnline()) {
                                 player.sendMessage(configManager.getMessage("prefix") + welcomeMessage);
                             }
                         }, 
                         20L // 1 Sekunde Verzögerung
                     );
                });
            }
        }).exceptionally(throwable -> {
            player.getServer().getLogger().severe(
                "Fehler beim Überprüfen/Erstellen des Kontos für " + player.getName() + ": " + 
                throwable.getMessage()
            );
            return null;
        });
    }
} 