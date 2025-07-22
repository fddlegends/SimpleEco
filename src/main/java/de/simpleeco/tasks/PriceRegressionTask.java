package de.simpleeco.tasks;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.database.DatabaseManager;
import de.simpleeco.pricing.PriceManager;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.logging.Level;

/**
 * Scheduled Task für regelmäßige Preis-Regression-Updates
 * 
 * Dieser Task läuft regelmäßig und sorgt dafür, dass sich die Preise
 * über Zeit wieder zu ihren Basiswerten zurückbewegen.
 */
public class PriceRegressionTask extends BukkitRunnable {
    
    private final SimpleEcoPlugin plugin;
    private final ConfigManager configManager;
    private final PriceManager priceManager;
    private final DatabaseManager databaseManager;
    
    public PriceRegressionTask(SimpleEcoPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.priceManager = plugin.getPriceManager();
        this.databaseManager = plugin.getDatabaseManager();
    }
    
    @Override
    public void run() {
        try {
            updatePriceRegression();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Preis-Regression-Update:", e);
        }
    }
    
    /**
     * Führt die Preis-Regression für alle Items durch
     */
    private void updatePriceRegression() {
        // Alle konfigurierten Items durchgehen
        for (Material material : configManager.getItemPrices().keySet()) {
            updateItemRegression(material);
        }
    }
    
    /**
     * Aktualisiert die Regression für ein einzelnes Item
     * 
     * @param material Das Material
     */
    private void updateItemRegression(Material material) {
        databaseManager.getItemStats(material).thenAccept(stats -> {
            long timeSinceLastTrade = stats.getTimeSinceLastTrade();
            long regressionTimeMinutes = configManager.getRegressionTimeMinutes();
            
            // Nur wenn seit dem letzten Handel genügend Zeit vergangen ist
            if (timeSinceLastTrade > 60) { // Mindestens 1 Minute
                applyRegression(material, stats, regressionTimeMinutes);
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.WARNING, 
                "Fehler beim Aktualisieren der Regression für " + material.name(), throwable);
            return null;
        });
    }
    
    /**
     * Wendet die Regression auf ein Item an
     * 
     * @param material Das Material
     * @param stats Aktuelle Item-Statistiken
     * @param regressionTimeMinutes Regressions-Zeit in Minuten
     */
    private void applyRegression(Material material, DatabaseManager.ItemStats stats, long regressionTimeMinutes) {
        long timeSinceLastTrade = stats.getTimeSinceLastTrade();
        long regressionTimeSeconds = regressionTimeMinutes * 60;
        
        // Wenn die volle Regressions-Zeit abgelaufen ist
        if (timeSinceLastTrade >= regressionTimeSeconds) {
            // Statistiken auf 0 zurücksetzen (vollständige Regression)
            resetItemStats(material);
            return;
        }
        
        // Partielle Regression anwenden
        double regressionFactor = 1.0 - ((double) timeSinceLastTrade / regressionTimeSeconds);
        
        // Neue Statistiken berechnen (schrittweise Reduzierung)
        long newSold = Math.round(stats.sold() * regressionFactor);
        long newBought = Math.round(stats.bought() * regressionFactor);
        
        // Nur aktualisieren wenn sich tatsächlich etwas geändert hat
        if (newSold != stats.sold() || newBought != stats.bought()) {
            updateItemStats(material, newSold, newBought);
        }
    }
    
    /**
     * Setzt die Item-Statistiken auf 0 zurück
     * 
     * @param material Das Material
     */
    private void resetItemStats(Material material) {
        updateItemStats(material, 0, 0);
        
        plugin.getLogger().info("Preise für " + material.name() + 
                               " wurden vollständig zum Basispreis zurückgesetzt (Regression abgeschlossen)");
    }
    
    /**
     * Aktualisiert die Item-Statistiken in der Datenbank
     * 
     * @param material Das Material
     * @param newSold Neue Verkaufs-Anzahl
     * @param newBought Neue Kauf-Anzahl
     */
    private void updateItemStats(Material material, long newSold, long newBought) {
        // Direkte Update-Query für bessere Performance
        String materialName = material.name();
        long currentTime = System.currentTimeMillis() / 1000;
        
        // Asynchrone Datenbankaktualisierung
        try {
            // Berechne die Differenz zu den aktuellen Werten
            databaseManager.getItemStats(material).thenAccept(currentStats -> {
                long soldDiff = newSold - currentStats.sold();
                long boughtDiff = newBought - currentStats.bought();
                
                // Aktualisierung nur wenn nötig
                if (soldDiff != 0 || boughtDiff != 0) {
                    databaseManager.updateItemStats(material, soldDiff, boughtDiff);
                }
            }).exceptionally(throwable -> {
                plugin.getLogger().log(Level.WARNING, 
                    "Fehler beim Aktualisieren der Statistiken für " + materialName, throwable);
                return null;
            });
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, 
                "Fehler beim Aktualisieren der Statistiken für " + materialName, e);
        }
    }
    
    /**
     * Startet den Preis-Regression-Task
     * 
     * @param plugin Das Plugin
     * @return Der gestartete Task
     */
    public static PriceRegressionTask start(SimpleEcoPlugin plugin) {
        PriceRegressionTask task = new PriceRegressionTask(plugin);
        
        long intervalMinutes = plugin.getConfigManager().getRegressionUpdateInterval();
        long intervalTicks = intervalMinutes * 60 * 20; // Minuten zu Ticks (20 Ticks = 1 Sekunde)
        
        // Task alle X Minuten ausführen
        task.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);
        
        plugin.getLogger().info("Preis-Regression-Task gestartet (Intervall: " + intervalMinutes + " Minuten)");
        
        return task;
    }
} 