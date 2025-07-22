package de.simpleeco.config;

import de.simpleeco.SimpleEcoPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Verwaltet die Konfiguration des SimpleEco Plugins
 * 
 * Lädt und cached alle Konfigurationswerte für einfachen Zugriff
 * durch andere Plugin-Komponenten.
 */
public class ConfigManager {
    
    private final SimpleEcoPlugin plugin;
    private FileConfiguration config;
    
    // Cached Konfigurationswerte
    private String currencyName;
    private String currencySymbol;
    private double startBalance;
    private String databasePath;
    private double priceFactor;
    private long referenceAmount;
    private long regressionTimeMinutes;
    private long regressionUpdateInterval;
    private Map<Material, ItemPriceConfig> itemPrices;
    private Map<String, String> messages;
    
    public ConfigManager(SimpleEcoPlugin plugin) {
        this.plugin = plugin;
        this.itemPrices = new HashMap<>();
        this.messages = new HashMap<>();
        loadConfig();
    }
    
    /**
     * Lädt die Konfiguration und cached die Werte
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        
        // Währungseinstellungen
        this.currencyName = config.getString("currency.name", "Gold");
        this.currencySymbol = config.getString("currency.symbol", "G");
        this.startBalance = config.getDouble("currency.startBalance", 1000.0);
        
        // Datenbankeinstellungen
        this.databasePath = config.getString("database.path", "plugins/SimpleEco/economy.db");
        
        // Preiseinstellungen
        this.priceFactor = config.getDouble("pricing.priceFactor", 0.05);
        this.referenceAmount = config.getLong("pricing.referenceAmount", 1000);
        this.regressionTimeMinutes = config.getLong("pricing.regressionTimeMinutes", 60);
        this.regressionUpdateInterval = config.getLong("pricing.regressionUpdateInterval", 5);
        
        // Item-Preise laden
        loadItemPrices();
        
        // Nachrichten laden
        loadMessages();
        
        plugin.getLogger().info("Konfiguration geladen: " + itemPrices.size() + " Items konfiguriert");
    }
    
    /**
     * Lädt alle Item-Preiskonfigurationen
     */
    private void loadItemPrices() {
        itemPrices.clear();
        
        if (!config.isConfigurationSection("pricing.items")) {
            plugin.getLogger().warning("Keine Item-Preise in der Konfiguration gefunden!");
            return;
        }
        
        Set<String> itemNames = config.getConfigurationSection("pricing.items").getKeys(false);
        
        for (String itemName : itemNames) {
            try {
                Material material = Material.valueOf(itemName.toUpperCase());
                String basePath = "pricing.items." + itemName;
                
                double basePrice = config.getDouble(basePath + ".basePrice", 10.0);
                double minPrice = config.getDouble(basePath + ".minPrice", 1.0);
                double maxPrice = config.getDouble(basePath + ".maxPrice", 100.0);
                boolean buyable = config.getBoolean(basePath + ".buyable", true);
                boolean sellable = config.getBoolean(basePath + ".sellable", true);
                
                // Item-spezifische Parameter (optional, falls nicht gesetzt werden globale Werte verwendet)
                Double itemPriceFactor = null;
                Long itemReferenceAmount = null;
                
                if (config.isSet(basePath + ".priceFactor")) {
                    itemPriceFactor = config.getDouble(basePath + ".priceFactor");
                }
                if (config.isSet(basePath + ".referenceAmount")) {
                    itemReferenceAmount = config.getLong(basePath + ".referenceAmount");
                }
                
                ItemPriceConfig priceConfig = new ItemPriceConfig(
                    basePrice, minPrice, maxPrice, buyable, sellable, 
                    itemPriceFactor, itemReferenceAmount
                );
                itemPrices.put(material, priceConfig);
                
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unbekanntes Material in Konfiguration: " + itemName);
            }
        }
    }
    
    /**
     * Lädt alle Nachrichten aus der Konfiguration
     */
    private void loadMessages() {
        messages.clear();
        
        if (!config.isConfigurationSection("messages")) {
            plugin.getLogger().warning("Keine Nachrichten in der Konfiguration gefunden!");
            return;
        }
        
        Set<String> messageKeys = config.getConfigurationSection("messages").getKeys(false);
        
        for (String key : messageKeys) {
            String value = config.getString("messages." + key, "");
            messages.put(key, value);
        }
    }
    
    /**
     * Formatiert eine Nachricht mit Platzhaltern
     */
    public String getMessage(String key, Object... replacements) {
        String message = messages.getOrDefault(key, key);
        
        // Einfache Platzhalter-Ersetzung
        if (replacements.length > 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    String placeholder = "{" + replacements[i] + "}";
                    String value = String.valueOf(replacements[i + 1]);
                    message = message.replace(placeholder, value);
                }
            }
        }
        
        return message;
    }
    
    // Getter-Methoden
    
    public String getCurrencyName() {
        return currencyName;
    }
    
    public String getCurrencySymbol() {
        return currencySymbol;
    }
    
    public double getStartBalance() {
        return startBalance;
    }
    
    public String getDatabasePath() {
        return databasePath;
    }
    
    public double getPriceFactor() {
        return priceFactor;
    }
    
    public long getReferenceAmount() {
        return referenceAmount;
    }
    
    public long getRegressionTimeMinutes() {
        return regressionTimeMinutes;
    }
    
    public long getRegressionUpdateInterval() {
        return regressionUpdateInterval;
    }
    
    public Map<Material, ItemPriceConfig> getItemPrices() {
        return itemPrices;
    }
    
    public ItemPriceConfig getItemPriceConfig(Material material) {
        return itemPrices.get(material);
    }
    
    public FileConfiguration getConfig() {
        return config;
    }
    
    public String getMenuTitle() {
        return config.getString("trading.menuTitle", "§6§lWirtschaftshandel");
    }
    
    public String getBuyButtonName() {
        return config.getString("trading.buyButtonName", "§a§lKaufen");
    }
    
    public String getSellButtonName() {
        return config.getString("trading.sellButtonName", "§c§lVerkaufen");
    }
    
    public String getInfoButtonName() {
        return config.getString("trading.infoButtonName", "§e§lInformation");
    }
    
    /**
     * Datenklasse für Item-Preiskonfiguration
     */
    public static class ItemPriceConfig {
        private final double basePrice;
        private final double minPrice;
        private final double maxPrice;
        private final boolean buyable;
        private final boolean sellable;
        private final Double priceFactor;        // Null = globaler Wert verwenden
        private final Long referenceAmount;      // Null = globaler Wert verwenden
        
        public ItemPriceConfig(double basePrice, double minPrice, double maxPrice, 
                              boolean buyable, boolean sellable, 
                              Double priceFactor, Long referenceAmount) {
            this.basePrice = basePrice;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.buyable = buyable;
            this.sellable = sellable;
            this.priceFactor = priceFactor;
            this.referenceAmount = referenceAmount;
        }
        
        public double getBasePrice() {
            return basePrice;
        }
        
        public double getMinPrice() {
            return minPrice;
        }
        
        public double getMaxPrice() {
            return maxPrice;
        }
        
        public boolean isBuyable() {
            return buyable;
        }
        
        public boolean isSellable() {
            return sellable;
        }
        
        /**
         * Gibt den item-spezifischen Preisfaktor zurück, oder null wenn globaler Wert verwendet werden soll
         */
        public Double getPriceFactor() {
            return priceFactor;
        }
        
        /**
         * Gibt die item-spezifische Referenzmenge zurück, oder null wenn globaler Wert verwendet werden soll
         */
        public Long getReferenceAmount() {
            return referenceAmount;
        }
        
        /**
         * Gibt den effektiven Preisfaktor zurück (item-spezifisch oder global)
         */
        public double getEffectivePriceFactor(double globalPriceFactor) {
            return priceFactor != null ? priceFactor : globalPriceFactor;
        }
        
        /**
         * Gibt die effektive Referenzmenge zurück (item-spezifisch oder global)
         */
        public long getEffectiveReferenceAmount(long globalReferenceAmount) {
            return referenceAmount != null ? referenceAmount : globalReferenceAmount;
        }
    }
} 