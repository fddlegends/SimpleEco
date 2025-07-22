package de.simpleeco;

import de.simpleeco.commands.EcoCommand;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.currency.BasicCurrency;
import de.simpleeco.database.DatabaseManager;
import de.simpleeco.listeners.PlayerJoinListener;
import de.simpleeco.listeners.VillagerInteractListener;
import de.simpleeco.pricing.PriceManager;
import de.simpleeco.tasks.PriceRegressionTask;
import de.simpleeco.trading.CustomVillagerTrader;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Haupt-Plugin-Klasse für SimpleEco
 * 
 * Verwaltet die Initialisierung und das Herunterfahren aller Plugin-Komponenten.
 * Implementiert ein vollständiges Wirtschaftssystem mit dynamischer Preisbildung
 * und Villager-Trading-Interface.
 */
public class SimpleEcoPlugin extends JavaPlugin {
    
    private static SimpleEcoPlugin instance;
    
    // Core-Komponenten
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private BasicCurrency currency;
    private PriceManager priceManager;
    private CustomVillagerTrader villagerTrader;
    private PriceRegressionTask regressionTask;
    
    @Override
    public void onEnable() {
        instance = this;
        
        getLogger().info("Starte SimpleEco Plugin...");
        
        try {
            // 1. Konfiguration laden
            this.configManager = new ConfigManager(this);
            getLogger().info("Konfiguration geladen");
            
            // 2. Datenbank initialisieren
            this.databaseManager = new DatabaseManager(this);
            if (!databaseManager.initialize()) {
                getLogger().severe("Fehler beim Initialisieren der Datenbank!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            getLogger().info("Datenbank initialisiert");
            
            // 3. Währungssystem initialisieren
            this.currency = new BasicCurrency(this, databaseManager);
            getLogger().info("Währungssystem initialisiert");
            
            // 4. Preismanager initialisieren
            this.priceManager = new PriceManager(this, databaseManager, configManager);
            getLogger().info("Preismanager initialisiert");
            
            // 5. Villager-Trading initialisieren
            this.villagerTrader = new CustomVillagerTrader(this, currency, priceManager, configManager);
            getLogger().info("Villager-Trading-System initialisiert");
            
            // 6. Commands registrieren
            registerCommands();
            getLogger().info("Commands registriert");
            
            // 7. Event-Listener registrieren
            registerListeners();
            getLogger().info("Event-Listener registriert");
            
            // 8. Preis-Regression-Task starten
            this.regressionTask = PriceRegressionTask.start(this);
            getLogger().info("Preis-Regression-Task gestartet");
            
            getLogger().info("SimpleEco Plugin erfolgreich aktiviert!");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Fehler beim Starten des Plugins:", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        getLogger().info("Fahre SimpleEco Plugin herunter...");
        
        try {
            // Preis-Regression-Task stoppen
            if (regressionTask != null && !regressionTask.isCancelled()) {
                regressionTask.cancel();
                getLogger().info("Preis-Regression-Task gestoppt");
            }
            
            // Datenbank-Verbindungen schließen
            if (databaseManager != null) {
                databaseManager.shutdown();
                getLogger().info("Datenbank-Verbindungen geschlossen");
            }
            
            getLogger().info("SimpleEco Plugin erfolgreich deaktiviert!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Fehler beim Herunterfahren:", e);
        }
    }
    
    /**
     * Registriert alle Plugin-Commands
     */
    private void registerCommands() {
        EcoCommand ecoCommand = new EcoCommand(this, currency, configManager);
        getCommand("eco").setExecutor(ecoCommand);
        getCommand("eco").setTabCompleter(ecoCommand);
    }
    
    /**
     * Registriert alle Event-Listener
     */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
            new PlayerJoinListener(currency, configManager), this);
        getServer().getPluginManager().registerEvents(
            new VillagerInteractListener(villagerTrader), this);
    }
    
    // Getter-Methoden für andere Klassen
    
    public static SimpleEcoPlugin getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public BasicCurrency getCurrency() {
        return currency;
    }
    
    public PriceManager getPriceManager() {
        return priceManager;
    }
    
    public CustomVillagerTrader getVillagerTrader() {
        return villagerTrader;
    }
} 