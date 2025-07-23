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
        
        // Inventar leeren
        inventory.clear();
        
        // Liste aller handelbaren Items (nur Items die kaufbar oder verkaufbar sind)
        List<Material> allTradeableItems = configManager.getItemPrices().entrySet()
            .stream()
            .filter(entry -> entry.getValue().isBuyable() || entry.getValue().isSellable())
            .map(Map.Entry::getKey)
            .sorted((a, b) -> a.name().compareTo(b.name()))
            .toList();
        
        // Items in Session speichern und Seitenzahl berechnen
        session.setAllTradeableItems(allTradeableItems);
        
        // Aktuelle Seite validieren
        if (session.getCurrentPage() >= session.getTotalPages()) {
            session.setCurrentPage(0);
        }
        
        // Items für aktuelle Seite berechnen
        int itemsPerPage = getItemsPerPage();
        int startIndex = session.getCurrentPage() * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allTradeableItems.size());
        
        List<Material> pageItems = allTradeableItems.subList(startIndex, endIndex);
        List<CompletableFuture<Void>> itemFutures = new ArrayList<>();
        
        int slot = 0;
        for (Material material : pageItems) {
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
        addNavigationItems(inventory, session);
        
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
                lore.add("§8▪ Preis-Faktor: §7" + String.format("%.3f", priceInfo.effectivePriceFactor()));
                lore.add("§8▪ Referenz-Menge: §7" + priceInfo.effectiveReferenceAmount());
                lore.add("§7");
                
                // Aktionen nur anzeigen wenn verfügbar
                if (config.isBuyable()) {
                    lore.add("§e§l⚡ Linksklick: §a" + configManager.getBuyButtonName());
                    lore.add("§e§l⚡ Shift+Linksklick: §a64x Kauf");
                    if (config.isSellable()) {
                        lore.add("§e§l⚡ Rechtsklick: §c" + configManager.getSellButtonName());
                        lore.add("§e§l⚡ Shift+Rechtsklick: §cAlle verkaufen");
                    }
                } else if (config.isSellable()) {
                    lore.add("§e§l⚡ Rechtsklick: §c" + configManager.getSellButtonName());
                    lore.add("§e§l⚡ Shift+Rechtsklick: §cAlle verkaufen");
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
     * @param session Die Trading-Session
     */
    private void addNavigationItems(Inventory inventory, TradingSession session) {
        // Vorherige Seite Button (nur anzeigen wenn verfügbar)
        if (session.hasPreviousPage()) {
            ItemStack prevItem = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevItem.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName("§a§l← Vorherige Seite");
                List<String> prevLore = new ArrayList<>();
                prevLore.add("§7Gehe zur Seite " + session.getCurrentPage());
                prevMeta.setLore(prevLore);
                prevItem.setItemMeta(prevMeta);
            }
            inventory.setItem(45, prevItem);
        }
        
        // Nächste Seite Button (nur anzeigen wenn verfügbar)
        if (session.hasNextPage()) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName("§a§lNächste Seite →");
                List<String> nextLore = new ArrayList<>();
                nextLore.add("§7Gehe zur Seite " + (session.getCurrentPage() + 2));
                nextMeta.setLore(nextLore);
                nextItem.setItemMeta(nextMeta);
            }
            inventory.setItem(53, nextItem);
        }
        
        // Seiten-Info in der Mitte
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        if (pageInfoMeta != null) {
            pageInfoMeta.setDisplayName("§6§lSeite " + (session.getCurrentPage() + 1) + " von " + session.getTotalPages());
            List<String> pageInfoLore = new ArrayList<>();
            pageInfoLore.add("§7");
            pageInfoLore.add("§7Zeigt " + session.getAllTradeableItems().size() + " handelbare Items");
            pageInfoLore.add("§7auf " + session.getTotalPages() + " Seiten");
            pageInfoMeta.setLore(pageInfoLore);
            pageInfo.setItemMeta(pageInfoMeta);
        }
        inventory.setItem(49, pageInfo);
        
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
            infoLore.add("§a§l» Shift+Linksklick: §f64x kaufen");
            infoLore.add("§c§l» Rechtsklick: §fItem verkaufen");
            infoLore.add("§c§l» Shift+Rechtsklick: §fAlle verkaufen");
            infoLore.add("§7");
            infoLore.add("§7Die Preise ändern sich dynamisch");
            infoLore.add("§7basierend auf Angebot und Nachfrage!");
            infoMeta.setLore(infoLore);
            infoItem.setItemMeta(infoMeta);
        }
        inventory.setItem(47, infoItem);
        
        // Schließen-Button (nur wenn keine nächste Seite verfügbar ist, sonst wird der Slot verwendet)
        if (!session.hasNextPage()) {
            ItemStack closeItem = new ItemStack(Material.BARRIER);
            ItemMeta closeMeta = closeItem.getItemMeta();
            if (closeMeta != null) {
                closeMeta.setDisplayName("§c§lMenü schließen");
                closeItem.setItemMeta(closeMeta);
            }
            inventory.setItem(53, closeItem);
        } else {
            // Schließen-Button auf anderen Slot verschieben
            ItemStack closeItem = new ItemStack(Material.BARRIER);
            ItemMeta closeMeta = closeItem.getItemMeta();
            if (closeMeta != null) {
                closeMeta.setDisplayName("§c§lMenü schließen");
                closeItem.setItemMeta(closeMeta);
            }
            inventory.setItem(51, closeItem);
        }
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
        
        // Pagination Navigation behandeln
        if (slot == 45 && session.hasPreviousPage()) { // Vorherige Seite
            session.setCurrentPage(session.getCurrentPage() - 1);
            populateMenu(session);
            return;
        }
        
        if (slot == 53 && session.hasNextPage()) { // Nächste Seite
            session.setCurrentPage(session.getCurrentPage() + 1);
            populateMenu(session);
            return;
        }
        
        // Spezielle Slots behandeln
        if (slot == 53 && !session.hasNextPage()) { // Schließen-Button (wenn keine nächste Seite)
            player.closeInventory();
            return;
        }
        
        if (slot == 51) { // Schließen-Button (alternativer Slot)
            player.closeInventory();
            return;
        }
        
        if (slot == 49) { // Seiten-Info
            return; // Nur anzeigen, keine Aktion
        }
        
        if (slot == 47) { // Info-Button
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
        
        // Prüfen ob der Slot tatsächlich ein handelbares Item enthält (0-44 sind handelbare Items)
        if (slot >= 45) {
            return; // Navigation-Bereich, kein handelbares Item
        }
        
        // Handelsaktion bestimmen
        boolean isBuying = clickType == ClickType.LEFT || clickType == ClickType.SHIFT_LEFT;
        boolean isSelling = clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT;
        boolean isMultiple = clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT;
        
        int quantity;
        if (isMultiple) {
            if (isSelling) {
                // Bei Shift-Rechtsklick: Alle verfügbaren Items verkaufen
                quantity = countItems(player, material);
            } else {
                // Bei Shift-Linksklick: 64 Stück kaufen
                quantity = 64;
            }
        } else {
            quantity = 1;
        }
        
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
        // Prüfen ob Spieler genügend Items hat und quantity > 0 ist
        if (quantity <= 0 || !hasItems(player, material, quantity)) {
            if (quantity <= 0) {
                player.sendMessage(configManager.getMessage("prefix") + 
                                 "§cSie haben keine " + getGermanItemName(material) + " zum Verkaufen!");
            } else {
                player.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("insufficientItems"));
            }
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
     * Springt zu einer bestimmten Seite im Shop-Menü
     * 
     * @param player Der Spieler
     * @param page Die Seitennummer (0-basiert)
     */
    public void goToPage(Player player, int page) {
        TradingSession session = activeSessions.get(player);
        if (session != null) {
            session.setCurrentPage(page);
            populateMenu(session);
        }
    }
    
    /**
     * Berechnet die maximale Anzahl von Items pro Seite
     * 
     * @return Anzahl Items pro Seite
     */
    private int getItemsPerPage() {
        return 45; // 9 * 5 Zeilen für Items, letzte Zeile für Navigation
    }
    
    /**
     * Berechnet die Gesamtzahl der Seiten basierend auf der Anzahl der Items
     * 
     * @param totalItems Gesamtanzahl der Items
     * @return Anzahl der Seiten
     */
    private int calculateTotalPages(int totalItems) {
        return Math.max(1, (int) Math.ceil((double) totalItems / getItemsPerPage()));
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
        return countItems(player, material) >= quantity;
    }
    
    /**
     * Zählt die Anzahl an Items eines bestimmten Materials im Spieler-Inventar
     * 
     * @param player Der Spieler
     * @param material Das Material
     * @return Die Anzahl der Items
     */
    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
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
        // Deutsche Übersetzungen für alle handelbaren Items
        return switch (material) {
            // Traditionelle Lebensmittel
            case WHEAT -> "Weizen";
            case CARROT -> "Karotte";
            case POTATO -> "Kartoffel";
            case BEETROOT -> "Rote Bete";
            case APPLE -> "Apfel";
            case BREAD -> "Brot";
            case COOKED_BEEF -> "Gebratenes Rindfleisch";
            case COOKED_PORKCHOP -> "Gebratenes Schweinefleisch";
            case COOKED_CHICKEN -> "Gebratenes Hühnchen";
            
            // Holz und Holzprodukte
            case OAK_LOG -> "Eichenstamm";
            case OAK_LEAVES -> "Eichenlaub";
            case OAK_SAPLING -> "Eichensetzling";
            case SPRUCE_LOG -> "Fichtenstamm";
            case SPRUCE_LEAVES -> "Fichtenlaub";
            case SPRUCE_SAPLING -> "Fichtensetzling";
            case BIRCH_LOG -> "Birkenstamm";
            case BIRCH_LEAVES -> "Birkenlaub";
            case BIRCH_SAPLING -> "Birkensetzling";
            case JUNGLE_LOG -> "Tropenstamm";
            case JUNGLE_LEAVES -> "Tropenlaub";
            case JUNGLE_SAPLING -> "Tropensetzling";
            case ACACIA_LOG -> "Akazienstamm";
            case ACACIA_LEAVES -> "Akazienlaub";
            case ACACIA_SAPLING -> "Akaziensetzling";
            case DARK_OAK_LOG -> "Schwarzeichenstamm";
            case DARK_OAK_LEAVES -> "Schwarzeichenlaub";
            case DARK_OAK_SAPLING -> "Schwarzeichensetzling";
            case MANGROVE_LOG -> "Mangrovenstamm";
            case MANGROVE_LEAVES -> "Mangrovenlaub";
            case MANGROVE_PROPAGULE -> "Mangrovenkeim";
            case CHERRY_LOG -> "Kirschstamm";
            case CHERRY_LEAVES -> "Kirschlaub";
            case CHERRY_SAPLING -> "Kirschsetzling";
            case BAMBOO_BLOCK -> "Bambusblock";
            case BAMBOO -> "Bambus";
            case CRIMSON_STEM -> "Karmesinroter Stamm";
            case CRIMSON_FUNGUS -> "Karmesinroter Pilz";
            case CRIMSON_ROOTS -> "Karmesinrote Wurzeln";
            case WARPED_STEM -> "Wirriger Stamm";
            case WARPED_FUNGUS -> "Wirriger Pilz";
            case WARPED_ROOTS -> "Wirrige Wurzeln";
            
            // Mob-Drops
            case BEEHIVE -> "Bienenstock";
            case BEE_NEST -> "Bienennest";
            case HONEY_BLOCK -> "Honigblock";
            case HONEYCOMB_BLOCK -> "Wabenblock";
            case HONEY_BOTTLE -> "Honigflasche";
            case EGG -> "Ei";
            case FEATHER -> "Feder";
            case LEATHER -> "Leder";
            case RABBIT_HIDE -> "Kaninchenfell";
            case TURTLE_EGG -> "Schildkrötenei";
            case SCUTE -> "Schildkrötenpanzer";
            case PUFFERFISH -> "Kugelfisch";
            case INK_SAC -> "Tintenbeutel";
            case GLOW_INK_SAC -> "Leucht-Tintenbeutel";
            case BONE -> "Knochen";
            case ARROW -> "Pfeil";
            case BONE_MEAL -> "Knochenmehl";
            case BONE_BLOCK -> "Knochenblock";
            case STRING -> "Faden";
            case SPIDER_EYE -> "Spinnenauge";
            case SLIME_BALL -> "Schleimball";
            case SLIME_BLOCK -> "Schleimblock";
            case GUNPOWDER -> "Schwarzpulver";
            case PHANTOM_MEMBRANE -> "Phantomhaut";
            case ROTTEN_FLESH -> "Verrottetes Fleisch";
            case BLAZE_ROD -> "Lohenrute";
            case BLAZE_POWDER -> "Lohenpulver";
            case MAGMA_CREAM -> "Magmacreme";
            case GHAST_TEAR -> "Ghastträne";
            case ENDER_PEARL -> "Enderperle";
            case ENDER_EYE -> "Enderauge";
            case SHULKER_SHELL -> "Shulkerschale";
            case DRAGON_BREATH -> "Drachenatem";
            
            // Blumen und Pflanzen
            case ALLIUM -> "Zierlauch";
            case AZURE_BLUET -> "Porzellansternchen";
            case BLUE_ORCHID -> "Blaue Orchidee";
            case CORNFLOWER -> "Kornblume";
            case DANDELION -> "Löwenzahn";
            case LILAC -> "Flieder";
            case LILY_OF_THE_VALLEY -> "Maiglöckchen";
            case PEONY -> "Pfingstrose";
            case POPPY -> "Mohn";
            case ROSE_BUSH -> "Rosenstrauch";
            case SUNFLOWER -> "Sonnenblume";
            case RED_TULIP -> "Rote Tulpe";
            case ORANGE_TULIP -> "Orange Tulpe";
            case WHITE_TULIP -> "Weiße Tulpe";
            case PINK_TULIP -> "Rosa Tulpe";
            case OXEYE_DAISY -> "Margerite";
            case DEAD_BUSH -> "Toter Busch";
            case CACTUS -> "Kaktus";
            case FERN -> "Farn";
            case LARGE_FERN -> "Großer Farn";
            case SHORT_GRASS -> "Gras";
            case TALL_GRASS -> "Hohes Gras";
            case LILY_PAD -> "Seerosenblatt";
            
            // Spezielle Pflanzen
            case AZALEA -> "Azalee";
            case FLOWERING_AZALEA -> "Blühende Azalee";
            case HANGING_ROOTS -> "Hängewurzeln";
            case MOSS_BLOCK -> "Moosblock";
            case MOSS_CARPET -> "Moosteppich";
            case CHORUS_FLOWER -> "Chorusblüte";
            case CHORUS_PLANT -> "Choruspflanze";
            case BIG_DRIPLEAF -> "Großes Tropfblatt";
            case SMALL_DRIPLEAF -> "Kleines Tropfblatt";
            case BROWN_MUSHROOM -> "Brauner Pilz";
            case BROWN_MUSHROOM_BLOCK -> "Brauner Pilzblock";
            case RED_MUSHROOM -> "Roter Pilz";
            case RED_MUSHROOM_BLOCK -> "Roter Pilzblock";
            case MUSHROOM_STEM -> "Pilzstiel";
            case NETHER_SPROUTS -> "Nether-Sprossen";
            case TWISTING_VINES -> "Gedrehte Ranken";
            case WEEPING_VINES -> "Weinende Ranken";
            case VINE -> "Ranken";
            case SHROOMLIGHT -> "Pilzlicht";
            case GLOW_BERRIES -> "Leuchtbeeren";
            case GLOW_LICHEN -> "Leuchtflechte";
            case SPORE_BLOSSOM -> "Sporenblüte";
            case SWEET_BERRY_BUSH -> "Süßbeerenstrauch";
            
            // Blöcke und Baumaterialien
            case ANDESITE -> "Andesit";
            case DIORITE -> "Diorit";
            case GRANITE -> "Granit";
            case TUFF -> "Tuffstein";
            case CALCITE -> "Kalzit";
            case BLACKSTONE -> "Schwarzstein";
            case POLISHED_BLACKSTONE -> "Polierter Schwarzstein";
            case POLISHED_BLACKSTONE_BRICKS -> "Polierte Schwarzstein-Ziegel";
            case DRIPSTONE_BLOCK -> "Tropfstein";
            case POINTED_DRIPSTONE -> "Spitzer Tropfstein";
            case MAGMA_BLOCK -> "Magmablock";
            case SAND -> "Sand";
            case RED_SAND -> "Roter Sand";
            case SANDSTONE -> "Sandstein";
            case RED_SANDSTONE -> "Roter Sandstein";
            case TERRACOTTA -> "Terrakotta";
            case CLAY -> "Ton";
            case BRICK -> "Ziegel";
            case GRAVEL -> "Kies";
            case DIRT -> "Erde";
            case COARSE_DIRT -> "Grobe Erde";
            case GRASS_BLOCK -> "Grasblock";
            case SOUL_SAND -> "Seelensand";
            case SOUL_SOIL -> "Seelenerde";
            case BLUE_ICE -> "Blaues Eis";
            case ICE -> "Eis";
            case PACKED_ICE -> "Packeis";
            case SNOW_BLOCK -> "Schneeblock";
            
            // Seltene Blöcke
            case NETHER_BRICKS -> "Netherziegel";
            case QUARTZ_BLOCK -> "Quarzblock";
            case GLOWSTONE -> "Glowstone";
            case OBSIDIAN -> "Obsidian";
            case CRYING_OBSIDIAN -> "Weinender Obsidian";
            case REDSTONE_BLOCK -> "Redstone-Block";
            case CHAIN -> "Kette";
            case IRON_BARS -> "Eisengitter";
            case LANTERN -> "Laterne";
            case SOUL_LANTERN -> "Seelenlaterne";
            case TORCH -> "Fackel";
            case SOUL_TORCH -> "Seelenfackel";
            case TNT -> "TNT";
            case SCAFFOLDING -> "Gerüst";
            case LEAD -> "Leine";
            case NAME_TAG -> "Namensschild";
            
            // Funktionale Blöcke
            case ENDER_CHEST -> "Endertruhe";
            case BARREL -> "Fass";
            case TRAPPED_CHEST -> "Redstone-Truhe";
            case BLAST_FURNACE -> "Schmelzofen";
            case SMOKER -> "Räucherofen";
            case CAMPFIRE -> "Lagerfeuer";
            case COMPOSTER -> "Komposter";
            case GRINDSTONE -> "Schleifstein";
            case CARTOGRAPHY_TABLE -> "Kartentisch";
            case SMITHING_TABLE -> "Schmiedetisch";
            case FLETCHING_TABLE -> "Bognerisch";
            case LOOM -> "Webstuhl";
            case LECTERN -> "Lesepult";
            case ANVIL -> "Amboss";
            
            // Erze
            case COAL_ORE -> "Kohleerz";
            case IRON_ORE -> "Eisenerz";
            case COPPER_ORE -> "Kupfererz";
            case GOLD_ORE -> "Golderz";
            case DIAMOND_ORE -> "Diamanterz";
            case EMERALD_ORE -> "Smaragderz";
            case NETHER_QUARTZ_ORE -> "Netherquarzerz";
            case NETHER_GOLD_ORE -> "Nethergolderz";
            case LAPIS_ORE -> "Lapislazulierz";
            case ANCIENT_DEBRIS -> "Antike Trümmer";
            case REDSTONE_ORE -> "Redstone-Erz";
            
            // Ingots und raffinierte Materialien
            case COAL -> "Kohle";
            case IRON_INGOT -> "Eisenbarren";
            case COPPER_INGOT -> "Kupferbarren";
            case GOLD_INGOT -> "Goldbarren";
            case DIAMOND -> "Diamant";
            case EMERALD -> "Smaragd";
            case QUARTZ -> "Netherquarz";
            case LAPIS_LAZULI -> "Lapislazuli";
            case REDSTONE -> "Redstone-Staub";
            
            // Spawn Eggs
            case ALLAY_SPAWN_EGG -> "Allay-Spawn-Ei";
            // case ARMADILLO_SPAWN_EGG -> "Gürteltier-Spawn-Ei"; // Not available in this version
            case ENDERMITE_SPAWN_EGG -> "Endermilbe-Spawn-Ei";
            case FROG_SPAWN_EGG -> "Frosch-Spawn-Ei";
            case GOAT_SPAWN_EGG -> "Ziegen-Spawn-Ei";
            case ZOMBIE_SPAWN_EGG -> "Zombie-Spawn-Ei";
            case SHULKER_SPAWN_EGG -> "Shulker-Spawn-Ei";
            case STRIDER_SPAWN_EGG -> "Schreiter-Spawn-Ei";
            case VILLAGER_SPAWN_EGG -> "Dorfbewohner-Spawn-Ei";
            
            default -> material.name().replace("_", " ").toLowerCase();
        };
    }
    
    /**
     * Datenklasse für Trading-Sessions
     */
    private static class TradingSession {
        private final Player player;
        private final Inventory inventory;
        private int currentPage;
        private int totalPages;
        private List<Material> allTradeableItems;
        
        public TradingSession(Player player, Inventory inventory) {
            this.player = player;
            this.inventory = inventory;
            this.currentPage = 0;
            this.totalPages = 1;
            this.allTradeableItems = new ArrayList<>();
        }
        
        public Player getPlayer() {
            return player;
        }
        
        public Inventory getInventory() {
            return inventory;
        }
        
        public int getCurrentPage() {
            return currentPage;
        }
        
        public void setCurrentPage(int currentPage) {
            this.currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));
        }
        
        public int getTotalPages() {
            return totalPages;
        }
        
        public void setTotalPages(int totalPages) {
            this.totalPages = Math.max(1, totalPages);
        }
        
        public List<Material> getAllTradeableItems() {
            return allTradeableItems;
        }
        
        public void setAllTradeableItems(List<Material> allTradeableItems) {
            this.allTradeableItems = allTradeableItems != null ? allTradeableItems : new ArrayList<>();
            // Berechne die Gesamtseitenzahl basierend auf Items pro Seite
            this.totalPages = Math.max(1, (int) Math.ceil((double) this.allTradeableItems.size() / 45.0));
        }
        
        public boolean hasNextPage() {
            return currentPage < totalPages - 1;
        }
        
        public boolean hasPreviousPage() {
            return currentPage > 0;
        }
    }
} 