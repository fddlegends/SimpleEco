package de.simpleeco.tasks;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.villager.ShopVillagerManager;
import de.simpleeco.bank.AtmVillagerManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Task für Villager-Blickrichtung
 * 
 * Diese Task sorgt dafür, dass Shop- und ATM-Villager zum nächsten Spieler
 * in der Nähe schauen und sich entsprechend drehen.
 */
public class VillagerLookTask extends BukkitRunnable {
    
    private final SimpleEcoPlugin plugin;
    private final ShopVillagerManager shopVillagerManager;
    private final AtmVillagerManager atmVillagerManager;
    private final double maxLookDistance;
    
    // Cache für Original-Positionen der Villager
    private final ConcurrentHashMap<UUID, Location> villagerPositions = new ConcurrentHashMap<>();
    
    public VillagerLookTask(SimpleEcoPlugin plugin, ShopVillagerManager shopVillagerManager, 
                           AtmVillagerManager atmVillagerManager, double maxLookDistance) {
        this.plugin = plugin;
        this.shopVillagerManager = shopVillagerManager;
        this.atmVillagerManager = atmVillagerManager;
        this.maxLookDistance = maxLookDistance;
    }
    
    @Override
    public void run() {
        // Alle geladenen Welten durchgehen
        plugin.getServer().getWorlds().forEach(world -> {
            // Alle Villager in der Welt finden
            List<Entity> entities = world.getEntities();
            
            for (Entity entity : entities) {
                if (!(entity instanceof Villager villager)) {
                    continue;
                }
                
                // Prüfen ob es sich um einen speziellen Villager handelt
                if (!shopVillagerManager.isShopVillager(villager) && 
                    !atmVillagerManager.isAtmVillager(villager)) {
                    continue;
                }
                
                // Original-Position des Villagers speichern/überprüfen
                UUID villagerId = villager.getUniqueId();
                Location currentLocation = villager.getLocation();
                Location originalPosition = villagerPositions.get(villagerId);
                
                if (originalPosition == null) {
                    // Erste Begegnung mit diesem Villager - Position speichern
                    villagerPositions.put(villagerId, currentLocation.clone());
                    originalPosition = currentLocation;
                } else {
                    // Prüfen ob Villager sich bewegt hat (mehr als 0.5 Blöcke)
                    double distance = originalPosition.distance(currentLocation);
                    if (distance > 0.5) {
                        // Villager zurück zur Original-Position teleportieren
                        Location resetLoc = originalPosition.clone();
                        resetLoc.setYaw(currentLocation.getYaw()); // Blickrichtung beibehalten
                        resetLoc.setPitch(currentLocation.getPitch());
                        villager.teleport(resetLoc);
                        currentLocation = resetLoc;
                    }
                }
                
                // Nächsten Spieler in der Nähe finden
                Player nearestPlayer = findNearestPlayer(villager);
                
                if (nearestPlayer != null) {
                    // Villager zum Spieler drehen lassen (ohne Position zu ändern)
                    lookAtPlayer(villager, nearestPlayer, originalPosition);
                }
            }
        });
    }
    
    /**
     * Findet den nächsten Spieler in der Nähe des Villagers
     * 
     * @param villager Der Villager
     * @return Der nächste Spieler oder null wenn keiner in der Nähe ist
     */
    private Player findNearestPlayer(Villager villager) {
        Location villagerLocation = villager.getLocation();
        Player nearestPlayer = null;
        double nearestDistance = maxLookDistance;
        
        // Alle Spieler in der Welt durchgehen
        for (Player player : villager.getWorld().getPlayers()) {
            // Entfernung berechnen
            double distance = player.getLocation().distance(villagerLocation);
            
            // Prüfen ob Spieler näher ist als der bisherige nächste
            if (distance < nearestDistance) {
                nearestPlayer = player;
                nearestDistance = distance;
            }
        }
        
        return nearestPlayer;
    }
    
    /**
     * Lässt den Villager zum Spieler schauen
     * 
     * @param villager Der Villager
     * @param player Der Spieler
     * @param fixedPosition Die feste Position des Villagers (darf nicht verändert werden)
     */
    private void lookAtPlayer(Villager villager, Player player, Location fixedPosition) {
        Location playerLoc = player.getLocation();
        
        // Vektor vom Villager zum Spieler berechnen
        Vector direction = playerLoc.toVector().subtract(fixedPosition.toVector());
        
        // Y-Komponente ignorieren (nur horizontale Drehung)
        direction.setY(0);
        direction.normalize();
        
        // Yaw berechnen (horizontale Drehung)
        double yaw = Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        
        // Pitch berechnen (vertikale Drehung) - leicht nach unten schauen da Spieler meist höher sind
        double heightDiff = playerLoc.getY() - fixedPosition.getY();
        double horizontalDistance = Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ());
        double pitch = Math.toDegrees(Math.atan2(-heightDiff, horizontalDistance));
        
        // Pitch begrenzen für realistisches Aussehen
        pitch = Math.max(-30, Math.min(30, pitch));
        
        // Neue Location mit angepasster Blickrichtung erstellen (Position bleibt gleich)
        Location newLoc = fixedPosition.clone();
        newLoc.setYaw((float) yaw);
        newLoc.setPitch((float) pitch);
        
        // Villager teleportieren (mit neuer Blickrichtung, aber fester Position)
        villager.teleport(newLoc);
    }
    
    /**
     * Startet die VillagerLookTask
     * 
     * @param plugin Das Plugin
     * @param shopVillagerManager Der Shop-Villager-Manager
     * @param atmVillagerManager Der ATM-Villager-Manager
     * @param maxLookDistance Maximale Entfernung zum Spieler schauen
     * @param updateInterval Update-Intervall in Ticks
     * @return Die gestartete Task
     */
    public static VillagerLookTask start(SimpleEcoPlugin plugin, ShopVillagerManager shopVillagerManager,
                                        AtmVillagerManager atmVillagerManager, double maxLookDistance, 
                                        long updateInterval) {
        VillagerLookTask task = new VillagerLookTask(plugin, shopVillagerManager, atmVillagerManager, maxLookDistance);
        task.runTaskTimer(plugin, 20L, updateInterval); // Start nach 1 Sekunde, dann alle updateInterval Ticks
        return task;
    }
} 