package de.simpleeco.trading;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.currency.BasicCurrency;
import de.simpleeco.pricing.PriceManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet das Villager-Trading-Interface
 * 
 * Erstellt dynamische Inventory-Menüs für den Handel mit Items.
 * Zeigt aktuelle Kauf- und Verkaufspreise an und verarbeitet Transaktionen.
 */
public class CustomVillagerTrader {
    
    private final SimpleEcoPlugin plugin;
    private final BasicCurrency currency;
    private final PriceManager priceManager;
    private final ConfigManager configManager;
    
    // Cache für geöffnete Trading-Menüs
    private final ConcurrentHashMap<Player, TradingSession> activeSessions = new ConcurrentHashMap<>();
    
    public CustomVillagerTrader(SimpleEcoPlugin plugin, BasicCurrency currency, 
                               PriceManager priceManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.currency = currency;
        this.priceManager = priceManager;
        this.configManager = configManager;
    }
    
    /**
     * Öffnet das Trading-Menü für einen Spieler
     * 
     * @param player Der Spieler
     */
    public void openTradingMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, configManager.getMenuTitle());
        
        TradingSession session = new TradingSession(player, inventory);
        activeSessions.put(player, session);
        
        // Menü asynchron füllen
        populateMenu(session).thenRun(() -> {
            // Menü öffnen (muss im Haupt-Thread passieren)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.openInventory(inventory);
                }
            });
        });
    }
    
    /**
     * Füllt das Trading-Menü mit Items
     * 
     * @param session Die Trading-Session
     * @return CompletableFuture das abgeschlossen wird wenn das Menü gefüllt ist
     */
    private CompletableFuture<Void> populateMenu(TradingSession session) {
        Inventory inventory = session.getInventory();
        
        // Liste aller handelbaren Items (nur Items die kaufbar oder verkaufbar sind)
        List<Material> tradeableItems = configManager.getItemPrices().entrySet()
            .stream()
            .filter(entry -> entry.getValue().isBuyable() || entry.getValue().isSellable())
            .map(Map.Entry::getKey)
            .sorted((a, b) -> a.name().compareTo(b.name()))
            .toList();
        
        List<CompletableFuture<Void>> itemFutures = new ArrayList<>();
        
        int slot = 0;
        for (Material material : tradeableItems) {
            if (slot >= 45) break; // Platz für Navigationselemente lassen
            
            final int itemSlot = slot;
            CompletableFuture<Void> itemFuture = createTradeItem(material)
                .thenAccept(itemStack -> {
                    if (itemStack != null) {
                        inventory.setItem(itemSlot, itemStack);
                    }
                });
            
            itemFutures.add(itemFuture);
            slot++;
        }
        
        // Navigation und Info-Items hinzufügen
        addNavigationItems(inventory);
        
        return CompletableFuture.allOf(itemFutures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Erstellt ein ItemStack für ein handelbares Item mit Preisinformationen
     * 
     * @param material Das Material
     * @return CompletableFuture mit dem ItemStack
     */
    private CompletableFuture<ItemStack> createTradeItem(Material material) {
        return priceManager.getPriceInfo(material).thenApply(priceInfo -> {
            if (priceInfo == null) {
                return null;
            }
            
            ConfigManager.ItemPriceConfig config = configManager.getItemPriceConfig(material);
            if (config == null) {
                return null;
            }
            
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            
            if (meta != null) {
                // Name setzen
                meta.setDisplayName("§f§l" + getGermanItemName(material));
                
                // Lore mit Preisinformationen
                List<String> lore = new ArrayList<>();
                lore.add("§7");
                
                // Preise nur anzeigen wenn verfügbar
                if (config.isBuyable()) {
                    lore.add("§a§l» Kaufpreis: §e" + currency.formatAmountWithSymbol(priceInfo.buyPrice()));
                } else {
                    lore.add("§7§l» Kaufpreis: §cNicht verfügbar");
                }
                
                if (config.isSellable()) {
                    lore.add("§c§l» Verkaufspreis: §e" + currency.formatAmountWithSymbol(priceInfo.sellPrice()));
                } else {
                    lore.add("§7§l» Verkaufspreis: §cNicht verfügbar");
                }
                
                lore.add("§7");
                lore.add("§8▪ Basispreis: §7" + currency.formatAmountWithSymbol(priceInfo.basePrice()));
                lore.add("§8▪ Trend: " + priceInfo.getPriceTrend());
                lore.add("§8▪ Volatilität: " + priceInfo.getVolatilityDescription());
                lore.add("§7");
                lore.add("§8▪ Verkauft: §7" + priceInfo.totalSold());
                lore.add("§8▪ Gekauft: §7" + priceInfo.totalBought());
                lore.add("§8▪ Netto: §7" + priceInfo.getNetSales());
                lore.add("§7");
                
                // Aktionen nur anzeigen wenn verfügbar
                if (config.isBuyable()) {
                    lore.add("§e§l⚡ Linksklick: §a" + configManager.getBuyButtonName());
                    if (config.isSellable()) {
                        lore.add("§e§l⚡ Rechtsklick: §c" + configManager.getSellButtonName());
                    }
                    lore.add("§e§l⚡ Shift+Klick: §664x Handel");
                } else if (config.isSellable()) {
                    lore.add("§e§l⚡ Rechtsklick: §c" + configManager.getSellButtonName());
                    lore.add("§e§l⚡ Shift+Rechtsklick: §c64x Verkauf");
                }
                
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            
            return item;
        });
    }
    
    /**
     * Fügt Navigations- und Info-Items zum Inventar hinzu
     * 
     * @param inventory Das Inventar
     */
    private void addNavigationItems(Inventory inventory) {
        // Info-Item
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§e§l" + configManager.getInfoButtonName());
            List<String> infoLore = new ArrayList<>();
            infoLore.add("§7");
            infoLore.add("§7Willkommen beim Wirtschaftshandel!");
            infoLore.add("§7");
            infoLore.add("§a§l» Linksklick: §fItem kaufen");
            infoLore.add("§c§l» Rechtsklick: §fItem verkaufen");
            infoLore.add("§e§l» Shift+Klick: §f64x Handel");
            infoLore.add("§7");
            infoLore.add("§7Die Preise ändern sich dynamisch");
            infoLore.add("§7basierend auf Angebot und Nachfrage!");
            infoMeta.setLore(infoLore);
            infoItem.setItemMeta(infoMeta);
        }
        inventory.setItem(49, infoItem);
        
        // Schließen-Button
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName("§c§lMenü schließen");
            closeItem.setItemMeta(closeMeta);
        }
        inventory.setItem(53, closeItem);
    }
    
    /**
     * Verarbeitet einen Klick im Trading-Menü
     * 
     * @param player Der Spieler
     * @param clickedItem Das geklickte Item
     * @param clickType Art des Klicks
     * @param slot Der geklickte Slot
     */
    public void handleMenuClick(Player player, ItemStack clickedItem, ClickType clickType, int slot) {
        TradingSession session = activeSessions.get(player);
        if (session == null) {
            return;
        }
        
        // Spezielle Slots behandeln
        if (slot == 53) { // Schließen-Button
            player.closeInventory();
            return;
        }
        
        if (slot == 49) { // Info-Button
            return; // Nur anzeigen, keine Aktion
        }
        
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        Material material = clickedItem.getType();
        
        // Prüfen ob das Item handelbar ist
        ConfigManager.ItemPriceConfig config = configManager.getItemPriceConfig(material);
        if (config == null) {
            return;
        }
        
        // Handelsaktion bestimmen
        boolean isBuying = clickType == ClickType.LEFT || clickType == ClickType.SHIFT_LEFT;
        boolean isSelling = clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT;
        boolean isMultiple = clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT;
        
        int quantity = isMultiple ? 64 : 1;
        
        if (isBuying && config.isBuyable()) {
            processBuyTransaction(player, material, quantity);
        } else if (isSelling && config.isSellable()) {
            processSellTransaction(player, material, quantity);
        } else {
            // Ungültige Aktion
            if (isBuying && !config.isBuyable()) {
                player.sendMessage(configManager.getMessage("prefix") + "§cDieses Item ist nicht kaufbar!");
            } else if (isSelling && !config.isSellable()) {
                player.sendMessage(configManager.getMessage("prefix") + "§cDieses Item ist nicht verkaufbar!");
            }
        }
    }
    
    /**
     * Verarbeitet eine Kauftransaktion
     * 
     * @param player Der Spieler
     * @param material Das zu kaufende Material
     * @param quantity Die Anzahl
     */
    private void processBuyTransaction(Player player, Material material, int quantity) {
        priceManager.getBuyPrice(material).thenCompose(price -> {
            double totalCost = price * quantity;
            
            return currency.hasBalance(player, totalCost).thenCompose(hasBalance -> {
                if (!hasBalance) {
                    player.sendMessage(configManager.getMessage("prefix") + 
                                     configManager.getMessage("insufficientFunds"));
                    return CompletableFuture.completedFuture(false);
                }
                
                // Inventar-Platz prüfen
                if (!hasInventorySpace(player, material, quantity)) {
                    player.sendMessage(configManager.getMessage("prefix") + 
                                     configManager.getMessage("inventoryFull"));
                    return CompletableFuture.completedFuture(false);
                }
                
                // Transaktion durchführen
                return currency.removeBalance(player, totalCost).thenCompose(newBalance -> {
                    // Items ins Inventar geben
                    player.getInventory().addItem(new ItemStack(material, quantity));
                    
                    // Statistiken aktualisieren
                    return priceManager.processPurchase(material, quantity).thenApply(v -> {
                        // Erfolgsnachricht
                        String message = configManager.getMessage("tradeSuccess") + 
                                       " §7Gekauft: §e" + quantity + "x " + getGermanItemName(material) + 
                                       " §7für §e" + currency.formatAmountWithSymbol(totalCost);
                        player.sendMessage(configManager.getMessage("prefix") + message);
                        
                        // Menü aktualisieren
                        refreshMenu(player);
                        return true;
                    });
                });
            });
        }).exceptionally(throwable -> {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("tradeFailed"));
            plugin.getLogger().severe("Fehler bei Kauftransaktion: " + throwable.getMessage());
            return false;
        });
    }
    
    /**
     * Verarbeitet eine Verkaufstransaktion
     * 
     * @param player Der Spieler
     * @param material Das zu verkaufende Material
     * @param quantity Die Anzahl
     */
    private void processSellTransaction(Player player, Material material, int quantity) {
        // Prüfen ob Spieler genügend Items hat
        if (!hasItems(player, material, quantity)) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("insufficientItems"));
            return;
        }
        
        priceManager.getSellPrice(material).thenCompose(price -> {
            double totalEarnings = price * quantity;
            
            // Items aus Inventar entfernen
            removeItems(player, material, quantity);
            
            // Geld hinzufügen
            return currency.addBalance(player, totalEarnings).thenCompose(newBalance -> {
                // Statistiken aktualisieren
                return priceManager.processSale(material, quantity).thenApply(v -> {
                    // Erfolgsnachricht
                    String message = configManager.getMessage("tradeSuccess") + 
                                   " §7Verkauft: §e" + quantity + "x " + getGermanItemName(material) + 
                                   " §7für §e" + currency.formatAmountWithSymbol(totalEarnings);
                    player.sendMessage(configManager.getMessage("prefix") + message);
                    
                    // Menü aktualisieren
                    refreshMenu(player);
                    return true;
                });
            });
        }).exceptionally(throwable -> {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("tradeFailed"));
            plugin.getLogger().severe("Fehler bei Verkaufstransaktion: " + throwable.getMessage());
            return false;
        });
    }
    
    /**
     * Aktualisiert das Trading-Menü eines Spielers
     * 
     * @param player Der Spieler
     */
    private void refreshMenu(Player player) {
        TradingSession session = activeSessions.get(player);
        if (session != null) {
            populateMenu(session);
        }
    }
    
    /**
     * Prüft ob ein Spieler genügend Inventar-Platz hat
     * 
     * @param player Der Spieler
     * @param material Das Material
     * @param quantity Die Anzahl
     * @return true wenn genügend Platz vorhanden ist
     */
    private boolean hasInventorySpace(Player player, Material material, int quantity) {
        int maxStackSize = material.getMaxStackSize();
        int needed = quantity;
        
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                needed -= maxStackSize;
            } else if (item.getType() == material) {
                needed -= (maxStackSize - item.getAmount());
            }
            
            if (needed <= 0) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Prüft ob ein Spieler genügend Items hat
     * 
     * @param player Der Spieler
     * @param material Das Material
     * @param quantity Die benötigte Anzahl
     * @return true wenn genügend Items vorhanden sind
     */
    private boolean hasItems(Player player, Material material, int quantity) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count >= quantity;
    }
    
    /**
     * Entfernt Items aus dem Spieler-Inventar
     * 
     * @param player Der Spieler
     * @param material Das Material
     * @param quantity Die Anzahl zu entfernender Items
     */
    private void removeItems(Player player, Material material, int quantity) {
        int remaining = quantity;
        ItemStack[] contents = player.getInventory().getStorageContents();
        
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int removeAmount = Math.min(remaining, item.getAmount());
                remaining -= removeAmount;
                
                if (removeAmount >= item.getAmount()) {
                    contents[i] = null;
                } else {
                    item.setAmount(item.getAmount() - removeAmount);
                }
            }
        }
        
        player.getInventory().setStorageContents(contents);
    }
    
    /**
     * Schließt eine Trading-Session
     * 
     * @param player Der Spieler
     */
    public void closeSession(Player player) {
        activeSessions.remove(player);
    }
    
    /**
     * Gibt den deutschen Namen eines Materials zurück
     * 
     * @param material Das Material
     * @return Der deutsche Name
     */
    private String getGermanItemName(Material material) {
        // Einfache deutsche Übersetzungen für die wichtigsten Items
        return switch (material) {
            case WHEAT -> "Weizen";
            case CARROT -> "Karotte";
            case POTATO -> "Kartoffel";
            case BEETROOT -> "Rote Bete";
            case APPLE -> "Apfel";
            case BREAD -> "Brot";
            case COOKED_BEEF -> "Gebratenes Rindfleisch";
            case COOKED_PORKCHOP -> "Gebratenes Schweinefleisch";
            case COOKED_CHICKEN -> "Gebratenes Hühnchen";
            case DIAMOND -> "Diamant";
            case IRON_INGOT -> "Eisenbarren";
            case GOLD_INGOT -> "Goldbarren";
            case EMERALD -> "Smaragd";
            case COAL -> "Kohle";
            default -> material.name().replace("_", " ").toLowerCase();
        };
    }
    
    /**
     * Datenklasse für Trading-Sessions
     */
    private static class TradingSession {
        private final Player player;
        private final Inventory inventory;
        
        public TradingSession(Player player, Inventory inventory) {
            this.player = player;
            this.inventory = inventory;
        }
        
        public Player getPlayer() {
            return player;
        }
        
        public Inventory getInventory() {
            return inventory;
        }
    }
} 