package de.simpleeco.bank;

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
 * Manager für ATM-Villager
 * 
 * Verwaltet das Spawnen, Identifizieren und Verfolgen von speziellen ATM-Villagern
 * die das Bank-Interface öffnen können.
 */
public class AtmVillagerManager {
    
    private final SimpleEcoPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey atmVillagerKey;
    
    // Set zum Tracking von ATM-Villager UUIDs
    private final Set<UUID> atmVillagers;
    
    public AtmVillagerManager(SimpleEcoPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.atmVillagerKey = new NamespacedKey(plugin, "atm_villager");
        this.atmVillagers = new HashSet<>();
    }
    
    /**
     * Spawnt einen neuen ATM-Villager an der angegebenen Location
     * 
     * @param location Die Location wo der ATM-Villager gespawnt werden soll
     * @param spawner Der Spieler der den ATM-Villager spawnt
     * @return true wenn erfolgreich, false bei Fehler
     */
    public boolean spawnAtmVillager(Location location, Player spawner) {
        try {
            // Villager spawnen
            Villager villager = location.getWorld().spawn(location, Villager.class);
            
            // Villager konfigurieren
            configureAtmVillager(villager);
            
            // Als ATM-Villager markieren
            markAsAtmVillager(villager);
            
            // Zur Tracking-Liste hinzufügen
            atmVillagers.add(villager.getUniqueId());
            
            plugin.getLogger().info("ATM-Villager " + villager.getUniqueId() + 
                                  " gespawnt von " + spawner.getName() + 
                                  " bei " + location.getBlockX() + ", " + 
                                  location.getBlockY() + ", " + location.getBlockZ());
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("Fehler beim Spawnen des ATM-Villagers: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Konfiguriert einen Villager als ATM-Villager
     * 
     * @param villager Der zu konfigurierende Villager
     */
    private void configureAtmVillager(Villager villager) {
        // Name setzen
        String villagerName = configManager.getConfig().getString("atmVillager.name", "Bank-Automat");
        villager.setCustomName("§6§l" + villagerName);
        villager.setCustomNameVisible(true);
        
        // Bewegung verhindern
        villager.setAI(false);
        villager.setSilent(true);
        villager.setInvulnerable(true);
        villager.setRemoveWhenFarAway(false);
        
        // Beruf setzen (Librarian für Bank-Look)
        String profession = configManager.getConfig().getString("atmVillager.profession", "LIBRARIAN");
        try {
            Villager.Profession villagerProfession = Villager.Profession.valueOf(profession.toUpperCase());
            villager.setProfession(villagerProfession);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ungültiger ATM-Villager-Beruf in Config: " + profession + ". Verwende LIBRARIAN.");
            villager.setProfession(Villager.Profession.LIBRARIAN);
        }
        
        // Villager-Typ setzen
        String villagerType = configManager.getConfig().getString("atmVillager.villagerType", "PLAINS");
        try {
            Villager.Type vType = Villager.Type.valueOf(villagerType.toUpperCase());
            villager.setVillagerType(vType);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ungültiger ATM-Villager-Typ in Config: " + villagerType + ". Verwende PLAINS.");
            villager.setVillagerType(Villager.Type.PLAINS);
        }
        
        // Villager-Level setzen
        villager.setVillagerLevel(5); // Maximales Level für besseres Aussehen
        
        // Trades leeren um Verwirrung zu vermeiden
        villager.setRecipes(new java.util.ArrayList<>());
    }
    
    /**
     * Markiert einen Villager als ATM-Villager in den Persistent Data
     * 
     * @param villager Der zu markierende Villager
     */
    private void markAsAtmVillager(Villager villager) {
        villager.getPersistentDataContainer().set(atmVillagerKey, PersistentDataType.BOOLEAN, true);
        
        // Zusätzlich Metadata setzen (backup method)
        villager.setMetadata("atm_villager", new FixedMetadataValue(plugin, true));
    }
    
    /**
     * Prüft ob ein Villager ein ATM-Villager ist
     * 
     * @param villager Der zu prüfende Villager
     * @return true wenn es ein ATM-Villager ist, false sonst
     */
    public boolean isAtmVillager(Villager villager) {
        // Prüfe Persistent Data
        if (villager.getPersistentDataContainer().has(atmVillagerKey, PersistentDataType.BOOLEAN)) {
            Boolean isAtm = villager.getPersistentDataContainer().get(atmVillagerKey, PersistentDataType.BOOLEAN);
            if (isAtm != null && isAtm) {
                return true;
            }
        }
        
        // Fallback: Prüfe Metadata
        if (villager.hasMetadata("atm_villager")) {
            return villager.getMetadata("atm_villager").get(0).asBoolean();
        }
        
        // Fallback: Prüfe UUID in Set
        return atmVillagers.contains(villager.getUniqueId());
    }
    
    /**
     * Registriert einen existierenden Villager als ATM-Villager
     * (z.B. beim Server-Restart)
     * 
     * @param villager Der Villager
     */
    public void registerExistingAtmVillager(Villager villager) {
        if (isAtmVillager(villager)) {
            atmVillagers.add(villager.getUniqueId());
            plugin.getLogger().info("Existierender ATM-Villager registriert: " + villager.getUniqueId());
        }
    }
    
    /**
     * Entfernt einen ATM-Villager aus dem Tracking
     * 
     * @param villagerUuid Die UUID des Villagers
     */
    public void removeAtmVillager(UUID villagerUuid) {
        atmVillagers.remove(villagerUuid);
    }
    
    /**
     * Gibt die Anzahl der registrierten ATM-Villager zurück
     * 
     * @return Anzahl der ATM-Villager
     */
    public int getAtmVillagerCount() {
        return atmVillagers.size();
    }
    
    /**
     * Gibt alle ATM-Villager UUIDs zurück
     * 
     * @return Set mit allen ATM-Villager UUIDs
     */
    public Set<UUID> getAtmVillagerUUIDs() {
        return new HashSet<>(atmVillagers);
    }
} 