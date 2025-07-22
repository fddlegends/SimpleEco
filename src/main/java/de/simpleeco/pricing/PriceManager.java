package de.simpleeco.pricing;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.database.DatabaseManager;
import org.bukkit.Material;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Verwaltet die dynamische Preisbildung für Items
 * 
 * Implementiert eine Preisformel basierend auf Angebot und Nachfrage:
 * Preis = clamp(basisPreis * (1 + preisFaktor * (verkaufteMenge - gekaufteMenge) / referenzMenge), minPreis, maxPreis)
 * 
 * Alle Preise werden in Echtzeit basierend auf aktuellen Handelsstatistiken berechnet.
 */
public class PriceManager {
    
    private final SimpleEcoPlugin plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    
    public PriceManager(SimpleEcoPlugin plugin, DatabaseManager databaseManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configManager = configManager;
    }
    
    /**
     * Berechnet den aktuellen Kaufpreis für ein Item
     * 
     * @param material Das Material
     * @return CompletableFuture mit dem Kaufpreis
     */
    public CompletableFuture<Double> getBuyPrice(Material material) {
        ConfigManager.ItemPriceConfig priceConfig = configManager.getItemPriceConfig(material);
        
        if (priceConfig == null) {
            plugin.getLogger().warning("Keine Preiskonfiguration für " + material.name() + " gefunden!");
            return CompletableFuture.completedFuture(0.0);
        }
        
        return databaseManager.getItemStats(material).thenApply(stats -> {
            return calculatePrice(priceConfig, stats.sold(), stats.bought(), stats.lastTradeTime());
        }).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Berechnen des Kaufpreises für " + material.name(), throwable);
            return priceConfig.getBasePrice();
        });
    }
    
    /**
     * Berechnet den aktuellen Verkaufspreis für ein Item
     * 
     * Verkaufspreis ist normalerweise niedriger als der Kaufpreis,
     * um einen Gewinn für das System zu gewährleisten.
     * 
     * @param material Das Material
     * @return CompletableFuture mit dem Verkaufspreis
     */
    public CompletableFuture<Double> getSellPrice(Material material) {
        return getBuyPrice(material).thenApply(buyPrice -> {
            // Verkaufspreis ist 80% des Kaufpreises
            return buyPrice * 0.8;
        });
    }
    
    /**
     * Berechnet den Preis basierend auf der dynamischen Preisformel mit Preis-Regression
     * 
     * Formel: Preis = clamp(basisPreis * (1 + preisFaktor * nettoVerkäufe * regressionFaktor / referenzMenge), minPreis, maxPreis)
     * 
     * @param priceConfig Preiskonfiguration für das Item
     * @param sold Anzahl verkaufter Items
     * @param bought Anzahl gekaufter Items
     * @param lastTradeTime Zeitpunkt des letzten Handels (Unix-Timestamp in Sekunden)
     * @return Berechneter Preis
     */
    private double calculatePrice(ConfigManager.ItemPriceConfig priceConfig, long sold, long bought, long lastTradeTime) {
        double basePrice = priceConfig.getBasePrice();
        double minPrice = priceConfig.getMinPrice();
        double maxPrice = priceConfig.getMaxPrice();
        
        // Item-spezifische oder globale Parameter verwenden
        double priceFactor = priceConfig.getEffectivePriceFactor(configManager.getPriceFactor());
        long referenceAmount = priceConfig.getEffectiveReferenceAmount(configManager.getReferenceAmount());
        long regressionTimeMinutes = configManager.getRegressionTimeMinutes();
        
        // Netto-Verkäufe berechnen (verkauft - gekauft)
        long netSales = sold - bought;
        
        // Regressions-Faktor berechnen (0.0 bis 1.0)
        double regressionFactor = calculateRegressionFactor(lastTradeTime, regressionTimeMinutes);
        
        // Dynamischen Preisfaktor berechnen mit Regression
        double dynamicFactor = 1.0 + (priceFactor * netSales * regressionFactor / (double) referenceAmount);
        
        // Preis berechnen
        double calculatedPrice = basePrice * dynamicFactor;
        
        // In Min/Max-Grenzen einschränken
        return Math.max(minPrice, Math.min(maxPrice, calculatedPrice));
    }
    
    /**
     * Berechnet den Regressions-Faktor basierend auf der Zeit seit dem letzten Handel
     * 
     * @param lastTradeTime Zeitpunkt des letzten Handels (Unix-Timestamp in Sekunden)
     * @param regressionTimeMinutes Zeit in Minuten bis zur vollständigen Regression
     * @return Regressions-Faktor (0.0 = vollständige Regression, 1.0 = keine Regression)
     */
    private double calculateRegressionFactor(long lastTradeTime, long regressionTimeMinutes) {
        long currentTime = System.currentTimeMillis() / 1000;
        long timeSinceLastTrade = currentTime - lastTradeTime;
        long regressionTimeSeconds = regressionTimeMinutes * 60;
        
        if (timeSinceLastTrade >= regressionTimeSeconds) {
            return 0.0; // Vollständige Regression - Preis ist wieder beim Basispreis
        }
        
        // Lineare Regression: 1.0 bei sofortigem Handel bis 0.0 nach Regression-Zeit
        return 1.0 - ((double) timeSinceLastTrade / regressionTimeSeconds);
    }
    
    /**
     * Verarbeitet einen Kaufvorgang und aktualisiert die Statistiken
     * 
     * @param material Das gekaufte Material
     * @param quantity Anzahl der gekauften Items
     * @return CompletableFuture das abgeschlossen wird wenn die Statistiken aktualisiert wurden
     */
    public CompletableFuture<Void> processPurchase(Material material, long quantity) {
        return databaseManager.updateItemStats(material, 0, quantity);
    }
    
    /**
     * Verarbeitet einen Verkauf und aktualisiert die Statistiken
     * 
     * @param material Das verkaufte Material
     * @param quantity Anzahl der verkauften Items
     * @return CompletableFuture das abgeschlossen wird wenn die Statistiken aktualisiert wurden
     */
    public CompletableFuture<Void> processSale(Material material, long quantity) {
        return databaseManager.updateItemStats(material, quantity, 0);
    }
    
    /**
     * Holt die aktuellen Handelsstatistiken für ein Item
     * 
     * @param material Das Material
     * @return CompletableFuture mit den Statistiken
     */
    public CompletableFuture<DatabaseManager.ItemStats> getItemStats(Material material) {
        return databaseManager.getItemStats(material);
    }
    
    /**
     * Berechnet die Preisvolatilität eines Items
     * 
     * @param material Das Material
     * @return CompletableFuture mit der Volatilität (0.0 - 1.0)
     */
    public CompletableFuture<Double> getPriceVolatility(Material material) {
        ConfigManager.ItemPriceConfig priceConfig = configManager.getItemPriceConfig(material);
        
        if (priceConfig == null) {
            return CompletableFuture.completedFuture(0.0);
        }
        
        return getBuyPrice(material).thenApply(currentPrice -> {
            double basePrice = priceConfig.getBasePrice();
            double deviation = Math.abs(currentPrice - basePrice) / basePrice;
            return Math.min(1.0, deviation);
        });
    }
    
    /**
     * Erstellt eine Preisübersicht für ein Item
     * 
     * @param material Das Material
     * @return CompletableFuture mit der Preisübersicht
     */
    public CompletableFuture<PriceInfo> getPriceInfo(Material material) {
        ConfigManager.ItemPriceConfig priceConfig = configManager.getItemPriceConfig(material);
        
        if (priceConfig == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        CompletableFuture<Double> buyPriceFuture = getBuyPrice(material);
        CompletableFuture<Double> sellPriceFuture = getSellPrice(material);
        CompletableFuture<DatabaseManager.ItemStats> statsFuture = getItemStats(material);
        CompletableFuture<Double> volatilityFuture = getPriceVolatility(material);
        
        return CompletableFuture.allOf(buyPriceFuture, sellPriceFuture, statsFuture, volatilityFuture)
            .thenApply(v -> {
                try {
                    double buyPrice = buyPriceFuture.get();
                    double sellPrice = sellPriceFuture.get();
                    DatabaseManager.ItemStats stats = statsFuture.get();
                    double volatility = volatilityFuture.get();
                    
                                         return new PriceInfo(
                         material,
                         buyPrice,
                         sellPrice,
                         priceConfig.getBasePrice(),
                         priceConfig.getMinPrice(),
                         priceConfig.getMaxPrice(),
                         stats.sold(),
                         stats.bought(),
                         volatility,
                         priceConfig.getEffectivePriceFactor(configManager.getPriceFactor()),
                         priceConfig.getEffectiveReferenceAmount(configManager.getReferenceAmount())
                     );
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Fehler beim Erstellen der Preisübersicht", e);
                    return null;
                }
            });
    }
    
    /**
     * Prüft ob ein Material handelbar ist (in der Konfiguration definiert)
     * 
     * @param material Das zu prüfende Material
     * @return true wenn handelbar, false andernfalls
     */
    public boolean isTradeable(Material material) {
        return configManager.getItemPriceConfig(material) != null;
    }
    
    /**
     * Erstellt eine Handelssimulation um Preisänderungen vorherzusagen
     * 
     * @param material Das Material
     * @param quantity Simulierte Handelsmenge (positiv = verkaufen, negativ = kaufen)
     * @return CompletableFuture mit dem vorhergesagten Preis nach dem Handel
     */
    public CompletableFuture<Double> simulateTradePrice(Material material, long quantity) {
        ConfigManager.ItemPriceConfig priceConfig = configManager.getItemPriceConfig(material);
        
        if (priceConfig == null) {
            return CompletableFuture.completedFuture(0.0);
        }
        
        return databaseManager.getItemStats(material).thenApply(stats -> {
            long simulatedSold = stats.sold() + (quantity > 0 ? quantity : 0);
            long simulatedBought = stats.bought() + (quantity < 0 ? -quantity : 0);
            long currentTime = System.currentTimeMillis() / 1000; // Simuliere aktuellen Handel
            
            return calculatePrice(priceConfig, simulatedSold, simulatedBought, currentTime);
        });
    }
    
    /**
     * Datenklasse für umfassende Preisinformationen
     */
    public record PriceInfo(
        Material material,
        double buyPrice,
        double sellPrice,
        double basePrice,
        double minPrice,
        double maxPrice,
        long totalSold,
        long totalBought,
        double volatility,
        double effectivePriceFactor,
        long effectiveReferenceAmount
    ) {
        
        public long getNetSales() {
            return totalSold - totalBought;
        }
        
        public double getPriceDeviation() {
            return ((buyPrice - basePrice) / basePrice) * 100.0;
        }
        
        public String getPriceTrend() {
            double deviation = getPriceDeviation();
            if (deviation > 10) return "§c↗ Steigend";
            if (deviation < -10) return "§a↘ Fallend";
            return "§e→ Stabil";
        }
        
        public String getVolatilityDescription() {
            if (volatility < 0.1) return "§a§lNiedrig";
            if (volatility < 0.3) return "§e§lMittel";
            return "§c§lHoch";
        }
    }
} 