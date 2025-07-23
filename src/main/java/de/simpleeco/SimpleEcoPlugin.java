package de.simpleeco;

import de.simpleeco.commands.EcoCommand;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.currency.BasicCurrency;
import de.simpleeco.database.DatabaseManager;
import de.simpleeco.listeners.PlayerJoinListener;
import de.simpleeco.listeners.PlayerDeathListener;
import de.simpleeco.listeners.VillagerInteractListener;
import de.simpleeco.pricing.PriceManager;
import de.simpleeco.scoreboard.ScoreboardManager;
import de.simpleeco.tasks.PriceRegressionTask;
import de.simpleeco.tasks.VillagerLookTask;
import de.simpleeco.trading.CustomVillagerTrader;
import de.simpleeco.villager.ShopVillagerManager;
import de.simpleeco.bank.BankManager;
import de.simpleeco.bank.AtmVillagerManager;
import de.simpleeco.bank.AtmTrader;
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
    private BankManager bankManager;
    private PriceManager priceManager;
    private CustomVillagerTrader villagerTrader;
    private ShopVillagerManager shopVillagerManager;
    private AtmVillagerManager atmVillagerManager;
    private AtmTrader atmTrader;
    private ScoreboardManager scoreboardManager;
    private PriceRegressionTask regressionTask;
    private VillagerLookTask villagerLookTask;
    
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
            
            // 4. Bank-System initialisieren
            this.bankManager = new BankManager(this, databaseManager);
            // BankManager in Currency injizieren für Kompatibilität
            currency.setBankManager(bankManager);
            getLogger().info("Bank-System initialisiert");
            
            // 5. Preismanager initialisieren
            this.priceManager = new PriceManager(this, databaseManager, configManager);
            getLogger().info("Preismanager initialisiert");
            
            // 6. Shop-Villager-Manager initialisieren
            this.shopVillagerManager = new ShopVillagerManager(this, configManager);
            getLogger().info("Shop-Villager-Manager initialisiert");
            
            // 7. ATM-Villager-Manager initialisieren
            this.atmVillagerManager = new AtmVillagerManager(this, configManager);
            getLogger().info("ATM-Villager-Manager initialisiert");
            
            // 8. ATM-Trader initialisieren
            this.atmTrader = new AtmTrader(this, bankManager, configManager);
            getLogger().info("ATM-Trading-System initialisiert");
            
            // 9. Scoreboard-Manager initialisieren
            this.scoreboardManager = new ScoreboardManager(this, configManager, currency, bankManager);
            getLogger().info("Scoreboard-Manager initialisiert");
            
            // 10. Villager-Trading initialisieren
            this.villagerTrader = new CustomVillagerTrader(this, currency, priceManager, configManager);
            getLogger().info("Villager-Trading-System initialisiert");
            
            // 11. Commands registrieren
            registerCommands();
            getLogger().info("Commands registriert");
            
            // 12. Event-Listener registrieren
            registerListeners();
            getLogger().info("Event-Listener registriert");
            
            // 13. Preis-Regression-Task starten
            this.regressionTask = PriceRegressionTask.start(this);
            getLogger().info("Preis-Regression-Task gestartet");
            
            // 14. Villager-Look-Task starten (falls aktiviert)
            if (configManager.getConfig().getBoolean("villagerBehavior.lookAtPlayers", true)) {
                double lookDistance = configManager.getConfig().getDouble("villagerBehavior.lookDistance", 8.0);
                long updateInterval = configManager.getConfig().getLong("villagerBehavior.lookUpdateInterval", 20);
                
                this.villagerLookTask = VillagerLookTask.start(this, shopVillagerManager, atmVillagerManager, 
                                                              lookDistance, updateInterval);
                getLogger().info("Villager-Look-Task gestartet");
            }
                        
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
            
            // Villager-Look-Task stoppen
            if (villagerLookTask != null && !villagerLookTask.isCancelled()) {
                villagerLookTask.cancel();
                getLogger().info("Villager-Look-Task gestoppt");
            }
            
            // Scoreboard-Manager herunterfahren
            if (scoreboardManager != null) {
                scoreboardManager.shutdown();
                getLogger().info("Scoreboard-Manager heruntergefahren");
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
        // SimpleEco Command (vereinigt alle Subcommands)
        EcoCommand ecoCommand = new EcoCommand(this, currency, configManager, shopVillagerManager, atmVillagerManager);
        getCommand("eco").setExecutor(ecoCommand);
        getCommand("eco").setTabCompleter(ecoCommand);
    }
    
    /**
     * Registriert alle Event-Listener
     */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
            new PlayerJoinListener(currency, configManager, scoreboardManager), this);
        getServer().getPluginManager().registerEvents(
            new PlayerDeathListener(this, configManager, bankManager, scoreboardManager), this);
        getServer().getPluginManager().registerEvents(
            new VillagerInteractListener(villagerTrader, shopVillagerManager, atmTrader, atmVillagerManager, scoreboardManager), this);
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
    
    public ShopVillagerManager getShopVillagerManager() {
        return shopVillagerManager;
    }
    
    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
    
    public BankManager getBankManager() {
        return bankManager;
    }
    
    public AtmVillagerManager getAtmVillagerManager() {
        return atmVillagerManager;
    }
    
    public AtmTrader getAtmTrader() {
        return atmTrader;
    }
} 