package de.simpleeco.listeners;

import de.simpleeco.trading.CustomVillagerTrader;
import de.simpleeco.villager.ShopVillagerManager;
import de.simpleeco.bank.AtmTrader;
import de.simpleeco.bank.AtmVillagerManager;
import de.simpleeco.scoreboard.ScoreboardManager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Event-Listener für Villager-Interaktionen und Trading-Menü
 * 
 * Behandelt:
 * - Rechtsklick auf Shop-Villager → Öffnet Trading-Menü
 * - Rechtsklick auf ATM-Villager → Öffnet ATM-Menü
 * - Klicks im Trading-Menü → Verarbeitet Käufe/Verkäufe
 * - Klicks im ATM-Menü → Verarbeitet Bank-Operationen
 * - Schließen der Menüs → Cleanup
 * - Entfernung von Villagern → Tracking-Update
 */
public class VillagerInteractListener implements Listener {
    
    private final CustomVillagerTrader villagerTrader;
    private final ShopVillagerManager shopVillagerManager;
    private final AtmTrader atmTrader;
    private final AtmVillagerManager atmVillagerManager;
    private final ScoreboardManager scoreboardManager;
    
    public VillagerInteractListener(CustomVillagerTrader villagerTrader, ShopVillagerManager shopVillagerManager,
                                  AtmTrader atmTrader, AtmVillagerManager atmVillagerManager, ScoreboardManager scoreboardManager) {
        this.villagerTrader = villagerTrader;
        this.shopVillagerManager = shopVillagerManager;
        this.atmTrader = atmTrader;
        this.atmVillagerManager = atmVillagerManager;
        this.scoreboardManager = scoreboardManager;
    }
    
    /**
     * Behandelt Interaktionen mit Entities (speziell Villager)
     * 
     * @param event Das PlayerInteractEntityEvent
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Nur bei Villager-Interaktionen
        if (event.getRightClicked().getType() != EntityType.VILLAGER) {
            return;
        }
        
        Player player = event.getPlayer();
        Villager villager = (Villager) event.getRightClicked();
        
        // Prüfen ob es sich um einen Shop-Villager handelt
        if (shopVillagerManager.isShopVillager(villager)) {
            // Überprüfen ob Spieler sneakt (geduckt ist) - dann normales Trading erlauben
            if (player.isSneaking()) {
                return; // Normales Villager-Trading erlauben (auch bei Shop-Villagern)
            }
            
            // Event canceln um normales Trading zu verhindern
            event.setCancelled(true);
            
            // Eigenes Trading-Menü öffnen
            villagerTrader.openTradingMenu(player);
            return;
        }
        
        // Prüfen ob es sich um einen ATM-Villager handelt
        if (atmVillagerManager.isAtmVillager(villager)) {
            // Überprüfen ob Spieler sneakt (geduckt ist) - dann normales Trading erlauben
            if (player.isSneaking()) {
                return; // Normales Villager-Trading erlauben (auch bei ATM-Villagern)
            }
            
            // Event canceln um normales Trading zu verhindern
            event.setCancelled(true);
            
            // ATM-Menü öffnen
            atmTrader.openAtmMenu(player);
            return;
        }
        
        // Normaler Villager - kein Custom Trading
    }
    
    /**
     * Behandelt Klicks im Trading-Inventar und ATM-Inventar
     * 
     * @param event Das InventoryClickEvent
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        // Nur bei Spielern
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        String inventoryTitle = event.getView().getTitle();
        
        // Prüfen ob es sich um ein Trading-Menü handelt
        if (inventoryTitle.contains("Wirtschaftshandel")) {
            // Event canceln um normale Inventory-Interaktionen zu verhindern
            event.setCancelled(true);
            
            // Geklicktes Item und Slot ermitteln
            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getSlot();
            
            // Klick an VillagerTrader weiterleiten
            villagerTrader.handleMenuClick(player, clickedItem, event.getClick(), slot);
            
            // Scoreboard nach möglicher Transaktion aktualisieren (verzögert)
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                scoreboardManager.getPlugin(), 
                () -> scoreboardManager.updatePlayerScoreboard(player), 
                5L
            );
            return;
        }
        
        // Prüfen ob es sich um ein ATM-Menü handelt
        if (inventoryTitle.contains("Bank-Automat") || inventoryTitle.contains("Geld einzahlen") || inventoryTitle.contains("Geld abheben")) {
            // Event canceln um normale Inventory-Interaktionen zu verhindern
            event.setCancelled(true);
            
            // Geklicktes Item und Slot ermitteln
            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getSlot();
            
            // Klick an AtmTrader weiterleiten
            atmTrader.handleInventoryClick(player, slot, event.getClick(), clickedItem);
            
            // Scoreboard nach möglicher Transaktion aktualisieren (verzögert)
            org.bukkit.Bukkit.getScheduler().runTaskLater(
                scoreboardManager.getPlugin(), 
                () -> scoreboardManager.updatePlayerScoreboard(player), 
                5L
            );
            return;
        }
    }
    
    /**
     * Behandelt das Schließen des Trading-Inventars und ATM-Inventars
     * 
     * @param event Das InventoryCloseEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        // Nur bei Spielern
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        
        String inventoryTitle = event.getView().getTitle();
        
        // Prüfen ob es sich um ein Trading-Menü handelt
        if (inventoryTitle.contains("Wirtschaftshandel")) {
            // Trading-Session schließen
            villagerTrader.closeSession(player);
            return;
        }
        
                 // Prüfen ob es sich um ein ATM-Menü handelt
         if (inventoryTitle.contains("Bank-Automat") || inventoryTitle.contains("Geld einzahlen") || inventoryTitle.contains("Geld abheben")) {
             // ATM-Session schließen
             atmTrader.removeSession(player);
             return;
         }
    }
    
    /**
     * Behandelt das Sterben von Entities (Shop-Villager Cleanup)
     * 
     * @param event Das EntityDeathEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getEntity();
            if (shopVillagerManager.isShopVillager(villager)) {
                shopVillagerManager.removeShopVillager(villager.getUniqueId());
            }
        }
    }
    
    /**
     * Behandelt das Entfernen von Entities (Shop-Villager Cleanup)
     * 
     * @param event Das EntityRemoveEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getEntity().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getEntity();
            if (shopVillagerManager.isShopVillager(villager)) {
                shopVillagerManager.removeShopVillager(villager.getUniqueId());
            }
        }
    }
    
    /**
     * Behandelt das Laden von Chunks (Shop-Villager Registrierung)
     * 
     * @param event Das ChunkLoadEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        // Nach dem Laden eines Chunks alle Villager prüfen und Shop-Villager registrieren
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager villager) {
                shopVillagerManager.registerExistingShopVillager(villager);
            }
        }
    }
} 