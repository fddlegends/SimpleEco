package de.simpleeco.currency;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.database.DatabaseManager;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Grundlegende Währungsklasse für die Verwaltung von Spielerkonten
 * 
 * Bietet eine einfache API für alle Währungsoperationen und
 * delegiert die Datenpersistierung an den DatabaseManager.
 */
public class BasicCurrency {
    
    private final SimpleEcoPlugin plugin;
    private final DatabaseManager databaseManager;
    
    public BasicCurrency(SimpleEcoPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }
    
    /**
     * Holt den Kontostand eines Spielers
     * 
     * @param playerId UUID des Spielers
     * @return CompletableFuture mit dem Kontostand
     */
    public CompletableFuture<Double> getBalance(UUID playerId) {
        return databaseManager.getBalance(playerId);
    }
    
    /**
     * Holt den Kontostand eines Spielers (synchrone Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @return CompletableFuture mit dem Kontostand
     */
    public CompletableFuture<Double> getBalance(Player player) {
        return getBalance(player.getUniqueId());
    }
    
    /**
     * Setzt den Kontostand eines Spielers
     * 
     * @param playerId UUID des Spielers
     * @param balance Neuer Kontostand
     * @return CompletableFuture das abgeschlossen wird wenn die Operation fertig ist
     */
    public CompletableFuture<Void> setBalance(UUID playerId, double balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("Kontostand kann nicht negativ sein");
        }
        
        return databaseManager.setBalance(playerId, balance);
    }
    
    /**
     * Setzt den Kontostand eines Spielers (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param balance Neuer Kontostand
     * @return CompletableFuture das abgeschlossen wird wenn die Operation fertig ist
     */
    public CompletableFuture<Void> setBalance(Player player, double balance) {
        return setBalance(player.getUniqueId(), balance);
    }
    
    /**
     * Addiert einen Betrag zum Kontostand
     * 
     * @param playerId UUID des Spielers
     * @param amount Betrag zum Addieren (kann negativ sein für Abzug)
     * @return CompletableFuture mit dem neuen Kontostand
     */
    public CompletableFuture<Double> addBalance(UUID playerId, double amount) {
        return databaseManager.addBalance(playerId, amount);
    }
    
    /**
     * Addiert einen Betrag zum Kontostand (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Betrag zum Addieren
     * @return CompletableFuture mit dem neuen Kontostand
     */
    public CompletableFuture<Double> addBalance(Player player, double amount) {
        return addBalance(player.getUniqueId(), amount);
    }
    
    /**
     * Entfernt einen Betrag vom Kontostand
     * 
     * @param playerId UUID des Spielers
     * @param amount Betrag zum Entfernen (positiver Wert)
     * @return CompletableFuture mit dem neuen Kontostand
     */
    public CompletableFuture<Double> removeBalance(UUID playerId, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Betrag zum Entfernen muss positiv sein");
        }
        
        return addBalance(playerId, -amount);
    }
    
    /**
     * Entfernt einen Betrag vom Kontostand (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Betrag zum Entfernen
     * @return CompletableFuture mit dem neuen Kontostand
     */
    public CompletableFuture<Double> removeBalance(Player player, double amount) {
        return removeBalance(player.getUniqueId(), amount);
    }
    
    /**
     * Prüft ob ein Spieler genügend Guthaben hat
     * 
     * @param playerId UUID des Spielers
     * @param amount Erforderlicher Betrag
     * @return CompletableFuture<Boolean> ob genügend Guthaben vorhanden ist
     */
    public CompletableFuture<Boolean> hasBalance(UUID playerId, double amount) {
        return getBalance(playerId).thenApply(balance -> balance >= amount);
    }
    
    /**
     * Prüft ob ein Spieler genügend Guthaben hat (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Erforderlicher Betrag
     * @return CompletableFuture<Boolean> ob genügend Guthaben vorhanden ist
     */
    public CompletableFuture<Boolean> hasBalance(Player player, double amount) {
        return hasBalance(player.getUniqueId(), amount);
    }
    
    /**
     * Überweist Geld von einem Spieler zu einem anderen
     * 
     * @param fromId UUID des Senders
     * @param toId UUID des Empfängers
     * @param amount Zu überweisender Betrag
     * @return CompletableFuture<Boolean> ob die Überweisung erfolgreich war
     */
    public CompletableFuture<Boolean> transferBalance(UUID fromId, UUID toId, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Überweisungsbetrag muss positiv sein");
        }
        
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Spieler kann nicht an sich selbst überweisen");
        }
        
        return hasBalance(fromId, amount).thenCompose(hasSufficientBalance -> {
            if (!hasSufficientBalance) {
                return CompletableFuture.completedFuture(false);
            }
            
            // Gleichzeitige Ausführung beider Operationen
            CompletableFuture<Double> removeFromSender = removeBalance(fromId, amount);
            CompletableFuture<Double> addToReceiver = addBalance(toId, amount);
            
            return CompletableFuture.allOf(removeFromSender, addToReceiver)
                .thenApply(v -> true)
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Fehler bei Geldüberweisung: " + throwable.getMessage());
                    return false;
                });
        });
    }
    
    /**
     * Überweist Geld zwischen Spielern (Convenience-Methode)
     * 
     * @param from Sender
     * @param to Empfänger
     * @param amount Betrag
     * @return CompletableFuture<Boolean> ob die Überweisung erfolgreich war
     */
    public CompletableFuture<Boolean> transferBalance(Player from, Player to, double amount) {
        return transferBalance(from.getUniqueId(), to.getUniqueId(), amount);
    }
    
    /**
     * Erstellt ein neues Konto für einen Spieler mit Startguthaben
     * 
     * @param playerId UUID des Spielers
     * @return CompletableFuture das abgeschlossen wird wenn das Konto erstellt wurde
     */
    public CompletableFuture<Void> createAccount(UUID playerId) {
        double startBalance = plugin.getConfigManager().getStartBalance();
        return setBalance(playerId, startBalance);
    }
    
    /**
     * Erstellt ein neues Konto für einen Spieler (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @return CompletableFuture das abgeschlossen wird wenn das Konto erstellt wurde
     */
    public CompletableFuture<Void> createAccount(Player player) {
        return createAccount(player.getUniqueId());
    }
    
    /**
     * Prüft ob ein Spieler ein Konto hat
     * 
     * @param playerId UUID des Spielers
     * @return CompletableFuture<Boolean> ob das Konto existiert
     */
    public CompletableFuture<Boolean> hasAccount(UUID playerId) {
        return databaseManager.playerExists(playerId);
    }
    
    /**
     * Prüft ob ein Spieler ein Konto hat (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @return CompletableFuture<Boolean> ob das Konto existiert
     */
    public CompletableFuture<Boolean> hasAccount(Player player) {
        return hasAccount(player.getUniqueId());
    }
    
    /**
     * Formatiert einen Betrag als String mit Währungseinheit
     * 
     * @param amount Betrag
     * @return Formatierter String
     */
    public String formatAmount(double amount) {
        String currencyName = plugin.getConfigManager().getCurrencyName();
        return String.format("%.2f %s", amount, currencyName);
    }
    
    /**
     * Formatiert einen Betrag als String mit Währungssymbol
     * 
     * @param amount Betrag
     * @return Formatierter String mit Symbol
     */
    public String formatAmountWithSymbol(double amount) {
        String currencySymbol = plugin.getConfigManager().getCurrencySymbol();
        return String.format("%.2f %s", amount, currencySymbol);
    }
} 