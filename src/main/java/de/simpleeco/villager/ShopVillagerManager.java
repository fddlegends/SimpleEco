package de.simpleeco.villager;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Manager für Shop-Villager
 * 
 * Verwaltet das Spawnen, Identifizieren und Verfolgen von speziellen Shop-Villagern
 * die das Trading-Menü öffnen können.
 */
public class ShopVillagerManager {
    
    private final SimpleEcoPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey shopVillagerKey;
    
    // Set zum Tracking von Shop-Villager UUIDs
    private final Set<UUID> shopVillagers;
    
    public ShopVillagerManager(SimpleEcoPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopVillagerKey = new NamespacedKey(plugin, "shop_villager");
        this.shopVillagers = new HashSet<>();
    }
    
    /**
     * Spawnt einen neuen Shop-Villager an der angegebenen Location
     * 
     * @param location Die Location wo der Villager gespawnt werden soll
     * @param spawner Der Spieler der den Villager spawnt
     * @return true wenn erfolgreich, false bei Fehler
     */
    public boolean spawnShopVillager(Location location, Player spawner) {
        try {
            // Villager spawnen
            Villager villager = location.getWorld().spawn(location, Villager.class);
            
            // Villager konfigurieren
            configureShopVillager(villager);
            
            // Als Shop-Villager markieren
            markAsShopVillager(villager);
            
            // Zur Tracking-Liste hinzufügen
            shopVillagers.add(villager.getUniqueId());
            
            plugin.getLogger().info("Shop-Villager " + villager.getUniqueId() + 
                                  " gespawnt von " + spawner.getName() + 
                                  " bei " + location.getBlockX() + ", " + 
                                  location.getBlockY() + ", " + location.getBlockZ());
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Spawnen des Shop-Villagers: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Konfiguriert einen Villager als Shop-Villager
     * 
     * @param villager Der zu konfigurierende Villager
     */
    private void configureShopVillager(Villager villager) {
        // Name setzen (aus Config)
        String villagerName = configManager.getConfig().getString("shopVillager.name", "Shop");
        villager.setCustomName("§e§l" + villagerName);
        villager.setCustomNameVisible(true);
        
        // Bewegung verhindern
        villager.setAI(false);
        villager.setSilent(true);
        villager.setInvulnerable(true);
        villager.setRemoveWhenFarAway(false);
        
        // Beruf setzen (aus Config)
        String profession = configManager.getConfig().getString("shopVillager.profession", "TOOLSMITH");
        try {
            Villager.Profession villagerProfession = Villager.Profession.valueOf(profession.toUpperCase());
            villager.setProfession(villagerProfession);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ungültiger Villager-Beruf in Config: " + profession + ". Verwende TOOLSMITH.");
            villager.setProfession(Villager.Profession.TOOLSMITH);
        }
        
        // Villager-Typ setzen (aus Config)
        String villagerType = configManager.getConfig().getString("shopVillager.villagerType", "PLAINS");
        try {
            Villager.Type vType = Villager.Type.valueOf(villagerType.toUpperCase());
            villager.setVillagerType(vType);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ungültiger Villager-Typ in Config: " + villagerType + ". Verwende PLAINS.");
            villager.setVillagerType(Villager.Type.PLAINS);
        }
        
        // Villager-Level setzen
        villager.setVillagerLevel(5); // Maximales Level für besseres Aussehen
        
        // Trades leeren um Verwirrung zu vermeiden
        villager.setRecipes(new java.util.ArrayList<>());
    }
    
    /**
     * Markiert einen Villager als Shop-Villager in den Persistent Data
     * 
     * @param villager Der zu markierende Villager
     */
    private void markAsShopVillager(Villager villager) {
        villager.getPersistentDataContainer().set(shopVillagerKey, PersistentDataType.BOOLEAN, true);
        
        // Zusätzlich Metadata setzen (backup method)
        villager.setMetadata("shop_villager", new FixedMetadataValue(plugin, true));
    }
    
    /**
     * Prüft ob ein Villager ein Shop-Villager ist
     * 
     * @param villager Der zu prüfende Villager
     * @return true wenn es ein Shop-Villager ist, false sonst
     */
    public boolean isShopVillager(Villager villager) {
        // Prüfe Persistent Data
        if (villager.getPersistentDataContainer().has(shopVillagerKey, PersistentDataType.BOOLEAN)) {
            Boolean isShop = villager.getPersistentDataContainer().get(shopVillagerKey, PersistentDataType.BOOLEAN);
            if (isShop != null && isShop) {
                return true;
            }
        }
        
        // Fallback: Prüfe Metadata
        if (villager.hasMetadata("shop_villager")) {
            return villager.getMetadata("shop_villager").get(0).asBoolean();
        }
        
        // Fallback: Prüfe UUID in Set
        return shopVillagers.contains(villager.getUniqueId());
    }
    
    /**
     * Registriert einen existierenden Villager als Shop-Villager
     * (z.B. beim Server-Restart)
     * 
     * @param villager Der Villager
     */
    public void registerExistingShopVillager(Villager villager) {
        if (isShopVillager(villager)) {
            shopVillagers.add(villager.getUniqueId());
            plugin.getLogger().info("Existierender Shop-Villager registriert: " + villager.getUniqueId());
        }
    }
    
    /**
     * Entfernt einen Shop-Villager aus dem Tracking
     * 
     * @param villagerUuid Die UUID des Villagers
     */
    public void removeShopVillager(UUID villagerUuid) {
        shopVillagers.remove(villagerUuid);
    }
    
    /**
     * Gibt die Anzahl der registrierten Shop-Villager zurück
     * 
     * @return Anzahl der Shop-Villager
     */
    public int getShopVillagerCount() {
        return shopVillagers.size();
    }
    
    /**
     * Gibt alle Shop-Villager UUIDs zurück
     * 
     * @return Set mit allen Shop-Villager UUIDs
     */
    public Set<UUID> getShopVillagerUUIDs() {
        return new HashSet<>(shopVillagers);
    }
} 