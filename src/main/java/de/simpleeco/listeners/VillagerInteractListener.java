package de.simpleeco.listeners;

import de.simpleeco.trading.CustomVillagerTrader;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Event-Listener für Villager-Interaktionen und Trading-Menü
 * 
 * Behandelt:
 * - Rechtsklick auf Villager → Öffnet Trading-Menü
 * - Klicks im Trading-Menü → Verarbeitet Käufe/Verkäufe
 * - Schließen des Trading-Menüs → Cleanup
 */
public class VillagerInteractListener implements Listener {
    
    private final CustomVillagerTrader villagerTrader;
    
    public VillagerInteractListener(CustomVillagerTrader villagerTrader) {
        this.villagerTrader = villagerTrader;
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
        
        // Überprüfen ob Spieler sneakt (geduckt ist) - dann normales Trading
        if (player.isSneaking()) {
            return; // Normales Villager-Trading erlauben
        }
        
        // Event canceln um normales Trading zu verhindern
        event.setCancelled(true);
        
        // Eigenes Trading-Menü öffnen
        villagerTrader.openTradingMenu(player);
        
        // Feedback für den Spieler
        player.sendMessage("§a§lHandelsmenü geöffnet! §7(Halte Shift für normales Trading)");
    }
    
    /**
     * Behandelt Klicks im Trading-Inventar
     * 
     * @param event Das InventoryClickEvent
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        // Nur bei Spielern
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        // Prüfen ob es sich um ein Trading-Menü handelt
        String inventoryTitle = event.getView().getTitle();
        if (!inventoryTitle.contains("Wirtschaftshandel")) {
            return;
        }
        
        // Event canceln um normale Inventory-Interaktionen zu verhindern
        event.setCancelled(true);
        
        // Geklicktes Item und Slot ermitteln
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();
        
        // Klick an VillagerTrader weiterleiten
        villagerTrader.handleMenuClick(player, clickedItem, event.getClick(), slot);
    }
    
    /**
     * Behandelt das Schließen des Trading-Inventars
     * 
     * @param event Das InventoryCloseEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        // Nur bei Spielern
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        
        // Prüfen ob es sich um ein Trading-Menü handelt
        String inventoryTitle = event.getView().getTitle();
        if (!inventoryTitle.contains("Wirtschaftshandel")) {
            return;
        }
        
        // Trading-Session schließen
        villagerTrader.closeSession(player);
    }
} 