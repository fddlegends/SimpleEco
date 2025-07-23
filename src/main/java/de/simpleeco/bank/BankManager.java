package de.simpleeco.bank;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.database.DatabaseManager;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manager für das Bank-System
 * 
 * Verwaltet getrennte Cash- und Bank-Guthaben für Spieler.
 * Cash wird primär für Trading verwendet, Bank-Guthaben für Langzeitspeicherung.
 */
public class BankManager {
    
    private final SimpleEcoPlugin plugin;
    private final DatabaseManager databaseManager;
    
    public BankManager(SimpleEcoPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }
    
    // ====== CASH BALANCE METHODEN ======
    
    /**
     * Holt das Bargeld eines Spielers
     * 
     * @param playerId UUID des Spielers
     * @return CompletableFuture mit dem Bargeld-Betrag
     */
    public CompletableFuture<Double> getCashBalance(UUID playerId) {
        return databaseManager.getBalance(playerId);
    }
    
    /**
     * Holt das Bargeld eines Spielers (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @return CompletableFuture mit dem Bargeld-Betrag
     */
    public CompletableFuture<Double> getCashBalance(Player player) {
        return getCashBalance(player.getUniqueId());
    }
    
    /**
     * Setzt das Bargeld eines Spielers
     * 
     * @param playerId UUID des Spielers
     * @param amount Neuer Bargeld-Betrag
     * @return CompletableFuture das abgeschlossen wird wenn die Operation fertig ist
     */
    public CompletableFuture<Void> setCashBalance(UUID playerId, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Bargeld kann nicht negativ sein");
        }
        return databaseManager.setBalance(playerId, amount);
    }
    
    /**
     * Setzt das Bargeld eines Spielers (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Neuer Bargeld-Betrag
     * @return CompletableFuture das abgeschlossen wird wenn die Operation fertig ist
     */
    public CompletableFuture<Void> setCashBalance(Player player, double amount) {
        return setCashBalance(player.getUniqueId(), amount).thenRun(() -> {
            // Scoreboard benachrichtigen
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().onBalanceChanged(player);
            }
        });
    }
    
    /**
     * Addiert Bargeld zu einem Spieler
     * 
     * @param playerId UUID des Spielers
     * @param amount Betrag zum Addieren
     * @return CompletableFuture mit dem neuen Bargeld-Betrag
     */
    public CompletableFuture<Double> addCashBalance(UUID playerId, double amount) {
        return databaseManager.addBalance(playerId, amount);
    }
    
    /**
     * Addiert Bargeld zu einem Spieler (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Betrag zum Addieren
     * @return CompletableFuture mit dem neuen Bargeld-Betrag
     */
    public CompletableFuture<Double> addCashBalance(Player player, double amount) {
        return addCashBalance(player.getUniqueId(), amount).thenApply(newBalance -> {
            // Scoreboard benachrichtigen
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().onBalanceChanged(player);
            }
            return newBalance;
        });
    }
    
    /**
     * Entfernt Bargeld von einem Spieler
     * 
     * @param playerId UUID des Spielers
     * @param amount Betrag zum Entfernen (positiver Wert)
     * @return CompletableFuture mit dem neuen Bargeld-Betrag
     */
    public CompletableFuture<Double> removeCashBalance(UUID playerId, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Betrag zum Entfernen muss positiv sein");
        }
        return addCashBalance(playerId, -amount);
    }
    
    /**
     * Entfernt Bargeld von einem Spieler (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Betrag zum Entfernen
     * @return CompletableFuture mit dem neuen Bargeld-Betrag
     */
    public CompletableFuture<Double> removeCashBalance(Player player, double amount) {
        return removeCashBalance(player.getUniqueId(), amount);
    }
    
    /**
     * Prüft ob ein Spieler genügend Bargeld hat
     * 
     * @param playerId UUID des Spielers
     * @param amount Erforderlicher Betrag
     * @return CompletableFuture<Boolean> ob genügend Bargeld vorhanden ist
     */
    public CompletableFuture<Boolean> hasCashBalance(UUID playerId, double amount) {
        return getCashBalance(playerId).thenApply(balance -> balance >= amount);
    }
    
    /**
     * Prüft ob ein Spieler genügend Bargeld hat (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Erforderlicher Betrag
     * @return CompletableFuture<Boolean> ob genügend Bargeld vorhanden ist
     */
    public CompletableFuture<Boolean> hasCashBalance(Player player, double amount) {
        return hasCashBalance(player.getUniqueId(), amount);
    }
    
    // ====== BANK BALANCE METHODEN ======
    
    /**
     * Holt das Bank-Guthaben eines Spielers
     * 
     * @param playerId UUID des Spielers
     * @return CompletableFuture mit dem Bank-Guthaben
     */
    public CompletableFuture<Double> getBankBalance(UUID playerId) {
        return databaseManager.getBankBalance(playerId);
    }
    
    /**
     * Holt das Bank-Guthaben eines Spielers (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @return CompletableFuture mit dem Bank-Guthaben
     */
    public CompletableFuture<Double> getBankBalance(Player player) {
        return getBankBalance(player.getUniqueId());
    }
    
    /**
     * Setzt das Bank-Guthaben eines Spielers
     * 
     * @param playerId UUID des Spielers
     * @param amount Neuer Bank-Guthaben-Betrag
     * @return CompletableFuture das abgeschlossen wird wenn die Operation fertig ist
     */
    public CompletableFuture<Void> setBankBalance(UUID playerId, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Bank-Guthaben kann nicht negativ sein");
        }
        return databaseManager.setBankBalance(playerId, amount);
    }
    
    /**
     * Setzt das Bank-Guthaben eines Spielers (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Neuer Bank-Guthaben-Betrag
     * @return CompletableFuture das abgeschlossen wird wenn die Operation fertig ist
     */
    public CompletableFuture<Void> setBankBalance(Player player, double amount) {
        return setBankBalance(player.getUniqueId(), amount).thenRun(() -> {
            // Scoreboard benachrichtigen
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().onBankBalanceChanged(player);
            }
        });
    }
    
    /**
     * Addiert Bank-Guthaben zu einem Spieler
     * 
     * @param playerId UUID des Spielers
     * @param amount Betrag zum Addieren
     * @return CompletableFuture mit dem neuen Bank-Guthaben
     */
    public CompletableFuture<Double> addBankBalance(UUID playerId, double amount) {
        return databaseManager.addBankBalance(playerId, amount);
    }
    
    /**
     * Addiert Bank-Guthaben zu einem Spieler (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Betrag zum Addieren
     * @return CompletableFuture mit dem neuen Bank-Guthaben
     */
    public CompletableFuture<Double> addBankBalance(Player player, double amount) {
        return addBankBalance(player.getUniqueId(), amount).thenApply(newBalance -> {
            // Scoreboard benachrichtigen
            if (plugin.getScoreboardManager() != null) {
                plugin.getScoreboardManager().onBankBalanceChanged(player);
            }
            return newBalance;
        });
    }
    
    /**
     * Entfernt Bank-Guthaben von einem Spieler
     * 
     * @param playerId UUID des Spielers
     * @param amount Betrag zum Entfernen (positiver Wert)
     * @return CompletableFuture mit dem neuen Bank-Guthaben
     */
    public CompletableFuture<Double> removeBankBalance(UUID playerId, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Betrag zum Entfernen muss positiv sein");
        }
        return addBankBalance(playerId, -amount);
    }
    
    /**
     * Entfernt Bank-Guthaben von einem Spieler (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Betrag zum Entfernen
     * @return CompletableFuture mit dem neuen Bank-Guthaben
     */
    public CompletableFuture<Double> removeBankBalance(Player player, double amount) {
        return removeBankBalance(player.getUniqueId(), amount);
    }
    
    /**
     * Prüft ob ein Spieler genügend Bank-Guthaben hat
     * 
     * @param playerId UUID des Spielers
     * @param amount Erforderlicher Betrag
     * @return CompletableFuture<Boolean> ob genügend Bank-Guthaben vorhanden ist
     */
    public CompletableFuture<Boolean> hasBankBalance(UUID playerId, double amount) {
        return getBankBalance(playerId).thenApply(balance -> balance >= amount);
    }
    
    /**
     * Prüft ob ein Spieler genügend Bank-Guthaben hat (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Erforderlicher Betrag
     * @return CompletableFuture<Boolean> ob genügend Bank-Guthaben vorhanden ist
     */
    public CompletableFuture<Boolean> hasBankBalance(Player player, double amount) {
        return hasBankBalance(player.getUniqueId(), amount);
    }
    
    // ====== TRANSFER METHODEN ======
    
    /**
     * Überweist Geld von Bargeld zu Bank
     * 
     * @param playerId UUID des Spielers
     * @param amount Zu überweisender Betrag
     * @return CompletableFuture<Boolean> ob die Überweisung erfolgreich war
     */
    public CompletableFuture<Boolean> depositToBank(UUID playerId, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Einzahlungsbetrag muss positiv sein");
        }
        
        return hasCashBalance(playerId, amount).thenCompose(hasSufficientCash -> {
            if (!hasSufficientCash) {
                return CompletableFuture.completedFuture(false);
            }
            
            // Gleichzeitige Ausführung beider Operationen
            CompletableFuture<Double> removeCash = removeCashBalance(playerId, amount);
            CompletableFuture<Double> addToBank = addBankBalance(playerId, amount);
            
            return CompletableFuture.allOf(removeCash, addToBank)
                .thenApply(v -> true)
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Fehler bei Bank-Einzahlung: " + throwable.getMessage());
                    return false;
                });
        });
    }
    
    /**
     * Überweist Geld von Bargeld zu Bank (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Zu überweisender Betrag
     * @return CompletableFuture<Boolean> ob die Überweisung erfolgreich war
     */
    public CompletableFuture<Boolean> depositToBank(Player player, double amount) {
        return depositToBank(player.getUniqueId(), amount);
    }
    
    /**
     * Überweist Geld von Bank zu Bargeld
     * 
     * @param playerId UUID des Spielers
     * @param amount Zu überweisender Betrag
     * @return CompletableFuture<Boolean> ob die Überweisung erfolgreich war
     */
    public CompletableFuture<Boolean> withdrawFromBank(UUID playerId, double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Abhebungsbetrag muss positiv sein");
        }
        
        return hasBankBalance(playerId, amount).thenCompose(hasSufficientBank -> {
            if (!hasSufficientBank) {
                return CompletableFuture.completedFuture(false);
            }
            
            // Gleichzeitige Ausführung beider Operationen
            CompletableFuture<Double> removeFromBank = removeBankBalance(playerId, amount);
            CompletableFuture<Double> addCash = addCashBalance(playerId, amount);
            
            return CompletableFuture.allOf(removeFromBank, addCash)
                .thenApply(v -> true)
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Fehler bei Bank-Abhebung: " + throwable.getMessage());
                    return false;
                });
        });
    }
    
    /**
     * Überweist Geld von Bank zu Bargeld (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @param amount Zu überweisender Betrag
     * @return CompletableFuture<Boolean> ob die Überweisung erfolgreich war
     */
    public CompletableFuture<Boolean> withdrawFromBank(Player player, double amount) {
        return withdrawFromBank(player.getUniqueId(), amount);
    }
    
    /**
     * Holt das Gesamt-Guthaben eines Spielers (Cash + Bank)
     * 
     * @param playerId UUID des Spielers
     * @return CompletableFuture mit dem Gesamt-Guthaben
     */
    public CompletableFuture<Double> getTotalBalance(UUID playerId) {
        CompletableFuture<Double> cashFuture = getCashBalance(playerId);
        CompletableFuture<Double> bankFuture = getBankBalance(playerId);
        
        return cashFuture.thenCombine(bankFuture, Double::sum);
    }
    
    /**
     * Holt das Gesamt-Guthaben eines Spielers (Convenience-Methode)
     * 
     * @param player Spieler-Objekt
     * @return CompletableFuture mit dem Gesamt-Guthaben
     */
    public CompletableFuture<Double> getTotalBalance(Player player) {
        return getTotalBalance(player.getUniqueId());
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