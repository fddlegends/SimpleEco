package de.simpleeco.bank;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet das ATM-Interface für Bank-Operationen
 * 
 * Erstellt dynamische Inventory-Menüs für Bank-Operationen wie:
 * - Guthaben anzeigen (Cash und Bank getrennt)
 * - Geld einzahlen (Cash -> Bank)
 * - Geld abheben (Bank -> Cash)
 */
public class AtmTrader {
    
    private final SimpleEcoPlugin plugin;
    private final BankManager bankManager;
    private final ConfigManager configManager;
    
    // Cache für geöffnete ATM-Menüs
    private final ConcurrentHashMap<Player, AtmSession> activeSessions = new ConcurrentHashMap<>();
    
    public AtmTrader(SimpleEcoPlugin plugin, BankManager bankManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.bankManager = bankManager;
        this.configManager = configManager;
    }
    
    /**
     * Öffnet das ATM-Menü für einen Spieler
     * 
     * @param player Der Spieler
     */
    public void openAtmMenu(Player player) {
        // Session erstellen oder aktualisieren
        AtmSession session = new AtmSession(player);
        activeSessions.put(player, session);
        
        // Haupt-ATM-Menü erstellen
        createMainAtmMenu(player, session);
    }
    
    /**
     * Erstellt das Haupt-ATM-Menü
     * 
     * @param player Der Spieler
     * @param session Die ATM-Session
     */
    private void createMainAtmMenu(Player player, AtmSession session) {
        Inventory atmInventory = Bukkit.createInventory(null, 27, "§6§lBank-Automat");
        
        // Balances laden und Menü erstellen
        CompletableFuture<Double> cashFuture = bankManager.getCashBalance(player);
        CompletableFuture<Double> bankFuture = bankManager.getBankBalance(player);
        
        CompletableFuture.allOf(cashFuture, bankFuture).thenRun(() -> {
            try {
                double cashBalance = cashFuture.get();
                double bankBalance = bankFuture.get();
                
                session.cashBalance = cashBalance;
                session.bankBalance = bankBalance;
                
                // Menü aufbauen
                buildMainMenu(atmInventory, cashBalance, bankBalance);
                
                // Menü öffnen (auf Main Thread)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.openInventory(atmInventory);
                    session.currentInventory = atmInventory;
                });
                
            } catch (Exception e) {
                plugin.getLogger().severe("Fehler beim Laden der Kontostände: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(configManager.getMessage("prefix") + 
                                     "§cFehler beim Laden Ihrer Kontodaten!");
                });
            }
        });
    }
    
    /**
     * Baut das Haupt-ATM-Menü auf
     * 
     * @param inventory Das Inventory
     * @param cashBalance Bargeld-Betrag
     * @param bankBalance Bank-Guthaben
     */
    private void buildMainMenu(Inventory inventory, double cashBalance, double bankBalance) {
        // Rahmen mit Glasscheiben
        ItemStack glass = createGlassPane();
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, glass);
            }
        }
        
        // Bargeld-Anzeige
        inventory.setItem(10, createCashDisplay(cashBalance));
        
        // Bank-Guthaben-Anzeige
        inventory.setItem(12, createBankDisplay(bankBalance));
        
        // Einzahlen-Button
        inventory.setItem(14, createDepositButton());
        
        // Abheben-Button
        inventory.setItem(16, createWithdrawButton());
        
        // Schließen-Button
        inventory.setItem(22, createCloseButton());
    }
    
    /**
     * Erstellt die Bargeld-Anzeige
     * 
     * @param cashBalance Der Bargeld-Betrag
     * @return ItemStack für die Anzeige
     */
    private ItemStack createCashDisplay(double cashBalance) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a§lBargeld");
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Ihr aktuelles Bargeld:");
        lore.add("§e" + bankManager.formatAmount(cashBalance));
        lore.add("");
        lore.add("§7Das Bargeld wird für den");
        lore.add("§7Handel mit Villagern verwendet.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Erstellt die Bank-Guthaben-Anzeige
     * 
     * @param bankBalance Das Bank-Guthaben
     * @return ItemStack für die Anzeige
     */
    private ItemStack createBankDisplay(double bankBalance) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lBank-Guthaben");
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Ihr Bank-Guthaben:");
        lore.add("§e" + bankManager.formatAmount(bankBalance));
        lore.add("");
        lore.add("§7Sicherer Aufbewahrungsort");
        lore.add("§7für Ihr Geld.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Erstellt den Einzahlen-Button
     * 
     * @return ItemStack für den Button
     */
    private ItemStack createDepositButton() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§2§lGeld einzahlen");
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Zahlen Sie Bargeld auf");
        lore.add("§7Ihr Bank-Konto ein.");
        lore.add("");
        lore.add("§eKlicken Sie zum Öffnen!");
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Erstellt den Abheben-Button
     * 
     * @return ItemStack für den Button
     */
    private ItemStack createWithdrawButton() {
        ItemStack item = new ItemStack(Material.DISPENSER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§lGeld abheben");
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Heben Sie Geld von");
        lore.add("§7Ihrem Bank-Konto ab.");
        lore.add("");
        lore.add("§eKlicken Sie zum Öffnen!");
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Erstellt den Schließen-Button
     * 
     * @return ItemStack für den Button
     */
    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§lSchließen");
        
        List<String> lore = new ArrayList<>();
        lore.add("§7ATM-Menü schließen");
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Erstellt eine Glasscheibe für die Dekoration
     * 
     * @return ItemStack der Glasscheibe
     */
    private ItemStack createGlassPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Öffnet das Einzahl-Menü
     * 
     * @param player Der Spieler
     */
    public void openDepositMenu(Player player) {
        AtmSession session = activeSessions.get(player);
        if (session == null) return;
        
        Inventory depositInventory = Bukkit.createInventory(null, 27, "§2§lGeld einzahlen");
        
        // Glasscheiben als Rahmen
        ItemStack glass = createGlassPane();
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                depositInventory.setItem(i, glass);
            }
        }
        
        // Verschiedene Einzahl-Beträge
        depositInventory.setItem(10, createAmountButton(10.0, "§a10 einzahlen"));
        depositInventory.setItem(11, createAmountButton(100.0, "§a100 einzahlen"));
        depositInventory.setItem(12, createAmountButton(1000.0, "§a1000 einzahlen"));
        depositInventory.setItem(13, createAmountButton(session.cashBalance, "§aAlles einzahlen"));
        
        // Zurück-Button
        depositInventory.setItem(18, createBackButton());
        
        player.openInventory(depositInventory);
        session.currentInventory = depositInventory;
        session.menuType = AtmSession.MenuType.DEPOSIT;
    }
    
    /**
     * Öffnet das Abheb-Menü
     * 
     * @param player Der Spieler
     */
    public void openWithdrawMenu(Player player) {
        AtmSession session = activeSessions.get(player);
        if (session == null) return;
        
        Inventory withdrawInventory = Bukkit.createInventory(null, 27, "§c§lGeld abheben");
        
        // Glasscheiben als Rahmen
        ItemStack glass = createGlassPane();
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                withdrawInventory.setItem(i, glass);
            }
        }
        
        // Verschiedene Abheb-Beträge
        withdrawInventory.setItem(10, createAmountButton(10.0, "§c10 abheben"));
        withdrawInventory.setItem(11, createAmountButton(100.0, "§c100 abheben"));
        withdrawInventory.setItem(12, createAmountButton(1000.0, "§c1000 abheben"));
        withdrawInventory.setItem(13, createAmountButton(session.bankBalance, "§cAlles abheben"));
        
        // Zurück-Button
        withdrawInventory.setItem(18, createBackButton());
        
        player.openInventory(withdrawInventory);
        session.currentInventory = withdrawInventory;
        session.menuType = AtmSession.MenuType.WITHDRAW;
    }
    
    /**
     * Erstellt einen Betrags-Button
     * 
     * @param amount Der Betrag
     * @param displayName Der Anzeigename
     * @return ItemStack für den Button
     */
    private ItemStack createAmountButton(double amount, String displayName) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Betrag: §e" + bankManager.formatAmount(amount));
        lore.add("");
        lore.add("§eLinksklick: Ausführen");
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Erstellt den Zurück-Button
     * 
     * @return ItemStack für den Button
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7§lZurück");
        
        List<String> lore = new ArrayList<>();
        lore.add("§7Zurück zum Hauptmenü");
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Behandelt Inventory-Klicks
     * 
     * @param player Der Spieler
     * @param slot Der geklickte Slot
     * @param clickType Der Klick-Typ
     * @param item Das geklickte Item
     */
    public void handleInventoryClick(Player player, int slot, ClickType clickType, ItemStack item) {
        AtmSession session = activeSessions.get(player);
        if (session == null || item == null || !item.hasItemMeta()) return;
        
        String itemName = item.getItemMeta().getDisplayName();
        
        switch (session.menuType) {
            case MAIN -> handleMainMenuClick(player, session, slot, itemName);
            case DEPOSIT -> handleDepositMenuClick(player, session, slot, itemName);
            case WITHDRAW -> handleWithdrawMenuClick(player, session, slot, itemName);
        }
    }
    
    /**
     * Behandelt Klicks im Hauptmenü
     */
    private void handleMainMenuClick(Player player, AtmSession session, int slot, String itemName) {
        switch (slot) {
            case 14 -> openDepositMenu(player); // Einzahlen
            case 16 -> openWithdrawMenu(player); // Abheben
            case 22 -> { // Schließen
                player.closeInventory();
                activeSessions.remove(player);
            }
        }
    }
    
    /**
     * Behandelt Klicks im Einzahl-Menü
     */
    private void handleDepositMenuClick(Player player, AtmSession session, int slot, String itemName) {
        if (slot == 18) { // Zurück
            openAtmMenu(player);
            return;
        }
        
        double amount = getAmountFromSlot(slot, session, true);
        if (amount > 0) {
            performDeposit(player, amount);
        }
    }
    
    /**
     * Behandelt Klicks im Abheb-Menü
     */
    private void handleWithdrawMenuClick(Player player, AtmSession session, int slot, String itemName) {
        if (slot == 18) { // Zurück
            openAtmMenu(player);
            return;
        }
        
        double amount = getAmountFromSlot(slot, session, false);
        if (amount > 0) {
            performWithdraw(player, amount);
        }
    }
    
    /**
     * Ermittelt den Betrag basierend auf dem Slot
     */
    private double getAmountFromSlot(int slot, AtmSession session, boolean isDeposit) {
        return switch (slot) {
            case 10 -> 10.0;
            case 11 -> 100.0;
            case 12 -> 1000.0;
            case 13 -> isDeposit ? session.cashBalance : session.bankBalance;
            default -> 0.0;
        };
    }
    
    /**
     * Führt eine Einzahlung durch
     */
    private void performDeposit(Player player, double amount) {
        if (amount <= 0) return;
        
        bankManager.depositToBank(player, amount).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(configManager.getMessage("prefix") + 
                                     "§a" + bankManager.formatAmount(amount) + " erfolgreich eingezahlt!");
                    openAtmMenu(player); // Menü aktualisieren
                } else {
                    player.sendMessage(configManager.getMessage("prefix") + 
                                     "§cNicht genügend Bargeld verfügbar!");
                }
            });
        });
    }
    
    /**
     * Führt eine Abhebung durch
     */
    private void performWithdraw(Player player, double amount) {
        if (amount <= 0) return;
        
        bankManager.withdrawFromBank(player, amount).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(configManager.getMessage("prefix") + 
                                     "§a" + bankManager.formatAmount(amount) + " erfolgreich abgehoben!");
                    openAtmMenu(player); // Menü aktualisieren
                } else {
                    player.sendMessage(configManager.getMessage("prefix") + 
                                     "§cNicht genügend Bank-Guthaben verfügbar!");
                }
            });
        });
    }
    
    /**
     * Entfernt eine Session wenn ein Spieler das Inventory schließt
     * 
     * @param player Der Spieler
     */
    public void removeSession(Player player) {
        activeSessions.remove(player);
    }
    
    /**
     * Prüft ob ein Spieler eine aktive ATM-Session hat
     * 
     * @param player Der Spieler
     * @return true wenn aktive Session vorhanden
     */
    public boolean hasActiveSession(Player player) {
        return activeSessions.containsKey(player);
    }
    
    /**
     * Session-Klasse für ATM-Interaktionen
     */
    public static class AtmSession {
        public enum MenuType {
            MAIN, DEPOSIT, WITHDRAW
        }
        
        public final Player player;
        public Inventory currentInventory;
        public MenuType menuType = MenuType.MAIN;
        public double cashBalance = 0.0;
        public double bankBalance = 0.0;
        
        public AtmSession(Player player) {
            this.player = player;
        }
    }
} 