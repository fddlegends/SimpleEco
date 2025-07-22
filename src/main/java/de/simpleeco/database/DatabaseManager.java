package de.simpleeco.database;

import de.simpleeco.SimpleEcoPlugin;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Verwaltet alle Datenbankoperationen für das SimpleEco Plugin
 * 
 * Implementiert SQLite-Persistierung mit asynchronen Operationen für:
 * - Spieler-Kontostände (player_balance)
 * - Item-Handelsstatistiken (item_stats)
 * 
 * Alle Datenbankzugriffe erfolgen asynchron, um den Haupt-Thread nicht zu blockieren.
 */
public class DatabaseManager {
    
    private final SimpleEcoPlugin plugin;
    private Connection connection;
    private final String databasePath;
    
    // Cache für häufig abgerufene Daten
    private final ConcurrentHashMap<UUID, Double> balanceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Double> bankBalanceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ItemStats> itemStatsCache = new ConcurrentHashMap<>();
    
    public DatabaseManager(SimpleEcoPlugin plugin) {
        this.plugin = plugin;
        this.databasePath = plugin.getConfigManager().getDatabasePath();
    }
    
    /**
     * Initialisiert die Datenbankverbindung und erstellt Tabellen
     * 
     * @return true bei Erfolg, false bei Fehler
     */
    public boolean initialize() {
        try {
            // Datenbank-Verzeichnis erstellen falls nicht vorhanden
            File dbFile = new File(databasePath);
            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // SQLite-Verbindung aufbauen
            String jdbcUrl = "jdbc:sqlite:" + databasePath;
            connection = DriverManager.getConnection(jdbcUrl);
            
            // WAL-Modus für bessere Concurrency
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("PRAGMA cache_size=10000;");
                stmt.execute("PRAGMA temp_store=MEMORY;");
            }
            
            // Tabellen erstellen
            createTables();
            
            // Cache laden
            loadCaches();
            
            plugin.getLogger().info("Datenbank erfolgreich initialisiert: " + databasePath);
            return true;
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Initialisieren der Datenbank:", e);
            return false;
        }
    }
    
    /**
     * Erstellt die erforderlichen Datenbanktabellen
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Spieler-Balance-Tabelle (für Bargeld)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_balance (
                    uuid TEXT PRIMARY KEY,
                    balance REAL NOT NULL DEFAULT 0.0,
                    last_updated INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                )
            """);
            
            // Bank-Balance-Tabelle
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_bank_balance (
                    uuid TEXT PRIMARY KEY,
                    bank_balance REAL NOT NULL DEFAULT 0.0,
                    last_updated INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                )
            """);
            
            // Item-Statistik-Tabelle
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS item_stats (
                    item TEXT PRIMARY KEY,
                    sold BIGINT NOT NULL DEFAULT 0,
                    bought BIGINT NOT NULL DEFAULT 0,
                    last_trade_time INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                    last_updated INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
                )
            """);
            
            // Indices für bessere Performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_balance_updated ON player_balance(last_updated)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bank_balance_updated ON player_bank_balance(last_updated)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stats_updated ON item_stats(last_updated)");
        }
    }
    
    /**
     * Lädt die Caches mit aktuellen Daten aus der Datenbank
     */
    private void loadCaches() {
        // Balance-Cache laden
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid, balance FROM player_balance")) {
            
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                double balance = rs.getDouble("balance");
                balanceCache.put(uuid, balance);
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Laden des Balance-Cache:", e);
        }
        
        // Bank-Balance-Cache laden
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid, bank_balance FROM player_bank_balance")) {
            
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                double bankBalance = rs.getDouble("bank_balance");
                bankBalanceCache.put(uuid, bankBalance);
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Laden des Bank-Balance-Cache:", e);
        }
        
        // Item-Stats-Cache laden
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT item, sold, bought, last_trade_time FROM item_stats")) {
            
            while (rs.next()) {
                String item = rs.getString("item");
                long sold = rs.getLong("sold");
                long bought = rs.getLong("bought");
                long lastTradeTime = rs.getLong("last_trade_time");
                itemStatsCache.put(item, new ItemStats(sold, bought, lastTradeTime));
            }
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Laden des Item-Stats-Cache:", e);
        }
        
        plugin.getLogger().info("Cache geladen: " + balanceCache.size() + " Spieler (Cash), " + 
                               bankBalanceCache.size() + " Spieler (Bank), " + 
                               itemStatsCache.size() + " Items");
    }
    
    /**
     * Holt den Kontostand eines Spielers (asynchron)
     */
    public CompletableFuture<Double> getBalance(UUID playerId) {
        // Zuerst im Cache suchen
        Double cachedBalance = balanceCache.get(playerId);
        if (cachedBalance != null) {
            return CompletableFuture.completedFuture(cachedBalance);
        }
        
        // Wenn nicht im Cache, aus Datenbank laden
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT balance FROM player_balance WHERE uuid = ?")) {
                
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    double balance = rs.getDouble("balance");
                    balanceCache.put(playerId, balance);
                    return balance;
                } else {
                    // Spieler existiert nicht, Startguthaben setzen
                    double startBalance = plugin.getConfigManager().getStartBalance();
                    setBalance(playerId, startBalance);
                    return startBalance;
                }
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden des Kontostands:", e);
                return 0.0;
            }
        });
    }
    
    /**
     * Setzt den Kontostand eines Spielers (asynchron)
     */
    public CompletableFuture<Void> setBalance(UUID playerId, double balance) {
        balanceCache.put(playerId, balance);
        
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_balance (uuid, balance, last_updated) VALUES (?, ?, strftime('%s', 'now'))")) {
                
                stmt.setString(1, playerId.toString());
                stmt.setDouble(2, balance);
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Speichern des Kontostands:", e);
            }
        });
    }
    
    /**
     * Addiert einen Betrag zum Kontostand (asynchron)
     */
    public CompletableFuture<Double> addBalance(UUID playerId, double amount) {
        return getBalance(playerId).thenCompose(currentBalance -> {
            double newBalance = currentBalance + amount;
            return setBalance(playerId, newBalance).thenApply(v -> newBalance);
        });
    }
    
    /**
     * Holt die Item-Statistiken (asynchron)
     */
    public CompletableFuture<ItemStats> getItemStats(Material material) {
        String materialName = material.name();
        
        // Zuerst im Cache suchen
        ItemStats cachedStats = itemStatsCache.get(materialName);
        if (cachedStats != null) {
            return CompletableFuture.completedFuture(cachedStats);
        }
        
        // Wenn nicht im Cache, aus Datenbank laden
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT sold, bought, last_trade_time FROM item_stats WHERE item = ?")) {
                
                stmt.setString(1, materialName);
                ResultSet rs = stmt.executeQuery();
                
                ItemStats stats;
                if (rs.next()) {
                    stats = new ItemStats(rs.getLong("sold"), rs.getLong("bought"), rs.getLong("last_trade_time"));
                } else {
                    long currentTime = System.currentTimeMillis() / 1000;
                    stats = new ItemStats(0, 0, currentTime);
                }
                
                itemStatsCache.put(materialName, stats);
                return stats;
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden der Item-Statistiken:", e);
                long currentTime = System.currentTimeMillis() / 1000;
                return new ItemStats(0, 0, currentTime);
            }
        });
    }
    
    /**
     * Aktualisiert Item-Statistiken nach einem Kauf/Verkauf (asynchron)
     */
    public CompletableFuture<Void> updateItemStats(Material material, long soldChange, long boughtChange) {
        String materialName = material.name();
        
        return CompletableFuture.runAsync(() -> {
            long currentTime = System.currentTimeMillis() / 1000;
            
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT OR REPLACE INTO item_stats (item, sold, bought, last_trade_time, last_updated) " +
                    "VALUES (?, COALESCE((SELECT sold FROM item_stats WHERE item = ?), 0) + ?, " +
                    "COALESCE((SELECT bought FROM item_stats WHERE item = ?), 0) + ?, ?, strftime('%s', 'now'))")) {
                
                stmt.setString(1, materialName);
                stmt.setString(2, materialName);
                stmt.setLong(3, soldChange);
                stmt.setString(4, materialName);
                stmt.setLong(5, boughtChange);
                stmt.setLong(6, currentTime);
                stmt.executeUpdate();
                
                // Cache aktualisieren
                ItemStats currentStats = itemStatsCache.getOrDefault(materialName, new ItemStats(0, 0, currentTime));
                ItemStats newStats = new ItemStats(
                    currentStats.sold() + soldChange,
                    currentStats.bought() + boughtChange,
                    currentTime
                );
                itemStatsCache.put(materialName, newStats);
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Aktualisieren der Item-Statistiken:", e);
            }
        });
    }
    
    /**
     * Schließt die Datenbankverbindung
     */
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Datenbankverbindung geschlossen");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Schließen der Datenbankverbindung:", e);
        }
    }
    
    /**
     * Holt das Bank-Guthaben eines Spielers (asynchron)
     */
    public CompletableFuture<Double> getBankBalance(UUID playerId) {
        // Zuerst im Cache suchen
        Double cachedBalance = bankBalanceCache.get(playerId);
        if (cachedBalance != null) {
            return CompletableFuture.completedFuture(cachedBalance);
        }
        
        // Wenn nicht im Cache, aus Datenbank laden
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT bank_balance FROM player_bank_balance WHERE uuid = ?")) {
                
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    double bankBalance = rs.getDouble("bank_balance");
                    bankBalanceCache.put(playerId, bankBalance);
                    return bankBalance;
                } else {
                    // Spieler Bank-Konto existiert nicht, mit 0.0 starten
                    setBankBalance(playerId, 0.0);
                    return 0.0;
                }
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Laden des Bank-Guthabens:", e);
                return 0.0;
            }
        });
    }
    
    /**
     * Setzt das Bank-Guthaben eines Spielers (asynchron)
     */
    public CompletableFuture<Void> setBankBalance(UUID playerId, double balance) {
        bankBalanceCache.put(playerId, balance);
        
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_bank_balance (uuid, bank_balance, last_updated) VALUES (?, ?, strftime('%s', 'now'))")) {
                
                stmt.setString(1, playerId.toString());
                stmt.setDouble(2, balance);
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Speichern des Bank-Guthabens:", e);
            }
        });
    }
    
    /**
     * Addiert einen Betrag zum Bank-Guthaben (asynchron)
     */
    public CompletableFuture<Double> addBankBalance(UUID playerId, double amount) {
        return getBankBalance(playerId).thenCompose(currentBalance -> {
            double newBalance = currentBalance + amount;
            return setBankBalance(playerId, newBalance).thenApply(v -> newBalance);
        });
    }

    /**
     * Prüft ob ein Spieler existiert
     */
    public CompletableFuture<Boolean> playerExists(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT 1 FROM player_balance WHERE uuid = ?")) {
                
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();
                return rs.next();
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fehler beim Prüfen der Spielerexistenz:", e);
                return false;
            }
        });
    }
    
    /**
     * Record für Item-Statistiken
     */
    public record ItemStats(long sold, long bought, long lastTradeTime) {
        public long getNetSold() {
            return sold - bought;
        }
        
        public long getTimeSinceLastTrade() {
            return System.currentTimeMillis() / 1000 - lastTradeTime;
        }
    }
} 