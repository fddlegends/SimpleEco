package de.simpleeco.commands;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.villager.ShopVillagerManager;
import de.simpleeco.bank.AtmVillagerManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command-Handler für Spawn-Commands
 * 
 * Behandelt:
 * - /spawn villager - Spawnt einen Shop-Villager auf dem angeschauten Block
 * - /spawn atm - Spawnt einen ATM-Villager auf dem angeschauten Block
 */
public class SpawnCommand implements CommandExecutor, TabCompleter {
    
    private final SimpleEcoPlugin plugin;
    private final ConfigManager configManager;
    private final ShopVillagerManager shopVillagerManager;
    private final AtmVillagerManager atmVillagerManager;
    
    public SpawnCommand(SimpleEcoPlugin plugin, ConfigManager configManager, 
                       ShopVillagerManager shopVillagerManager, AtmVillagerManager atmVillagerManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopVillagerManager = shopVillagerManager;
        this.atmVillagerManager = atmVillagerManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Permissions prüfen
        if (!sender.hasPermission("simpleeco.spawn")) {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("noPermission"));
            return true;
        }
        
        // Nur Spieler können diesen Command ausführen
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§cDieser Befehl kann nur von Spielern ausgeführt werden!");
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "villager" -> handleVillagerSpawn(player);
            case "atm" -> handleAtmSpawn(player);
            case "help", "?" -> sendUsage(sender);
            default -> sendUsage(sender);
        }
        
        return true;
    }
    
    /**
     * Behandelt das Spawnen von Shop-Villagern
     * 
     * @param player Der Spieler der den Command ausführt
     */
    private void handleVillagerSpawn(Player player) {
        // Permissions für Villager-Spawn prüfen
        if (!player.hasPermission("simpleeco.spawn.villager")) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("noPermission"));
            return;
        }
        
        // Block finden, auf den der Spieler schaut
        RayTraceResult rayTrace = player.rayTraceBlocks(5.0);
        if (rayTrace == null || rayTrace.getHitBlock() == null) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("lookingAtNoBlock"));
            return;
        }
        
        Block targetBlock = rayTrace.getHitBlock();
        Location spawnLocation = targetBlock.getLocation().add(0.5, 1.0, 0.5); // Auf dem Block spawnen
        
        // Shop-Villager spawnen
        try {
            boolean success = shopVillagerManager.spawnShopVillager(spawnLocation, player);
            
            if (success) {
                player.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("villagerSpawned"));
                plugin.getLogger().info("Shop-Villager gespawnt von " + player.getName() + 
                                      " bei " + spawnLocation.getBlockX() + ", " + 
                                      spawnLocation.getBlockY() + ", " + spawnLocation.getBlockZ());
            } else {
                player.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("villagerSpawnFailed"));
            }
            
        } catch (Exception e) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("villagerSpawnFailed"));
            plugin.getLogger().severe("Fehler beim Spawnen des Shop-Villagers: " + e.getMessage());
        }
    }
    
    /**
     * Behandelt das Spawnen von ATM-Villagern
     * 
     * @param player Der Spieler der den Command ausführt
     */
    private void handleAtmSpawn(Player player) {
        // Permissions für ATM-Spawn prüfen
        if (!player.hasPermission("simpleeco.spawn.atm")) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("noPermission"));
            return;
        }
        
        // Block finden, auf den der Spieler schaut
        RayTraceResult rayTrace = player.rayTraceBlocks(5.0);
        if (rayTrace == null || rayTrace.getHitBlock() == null) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("lookingAtNoBlock"));
            return;
        }
        
        Block targetBlock = rayTrace.getHitBlock();
        Location spawnLocation = targetBlock.getLocation().add(0.5, 1.0, 0.5); // Auf dem Block spawnen
        
        // ATM-Villager spawnen
        try {
            boolean success = atmVillagerManager.spawnAtmVillager(spawnLocation, player);
            
            if (success) {
                player.sendMessage(configManager.getMessage("prefix") + 
                                 "§a§lATM-Villager erfolgreich gespawnt!");
                plugin.getLogger().info("ATM-Villager gespawnt von " + player.getName() + 
                                      " bei " + spawnLocation.getBlockX() + ", " + 
                                      spawnLocation.getBlockY() + ", " + spawnLocation.getBlockZ());
            } else {
                player.sendMessage(configManager.getMessage("prefix") + 
                                 "§c§lFehler beim Spawnen des ATM-Villagers!");
            }
            
        } catch (Exception e) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             "§c§lFehler beim Spawnen des ATM-Villagers!");
            plugin.getLogger().severe("Fehler beim Spawnen des ATM-Villagers: " + e.getMessage());
        }
    }

    /**
     * Sendet die Verwendungshinweise
     * 
     * @param sender Der Command-Sender
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§8§m          §r §6§lSimpleEco Spawn Commands §8§m          ");
        sender.sendMessage("§e/spawn villager §8- §7Spawnt einen Shop-Villager");
        sender.sendMessage("§e/spawn atm §8- §7Spawnt einen ATM-Villager");
        sender.sendMessage("§e/spawn help §8- §7Zeigt diese Hilfe an");
        sender.sendMessage("§8§m                                        ");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("simpleeco.spawn")) {
            return new ArrayList<>();
        }
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Erste Ebene: Subcommands
            List<String> subCommands = Arrays.asList("villager", "atm", "help");
            String input = args[0].toLowerCase();
            
            completions = subCommands.stream()
                .filter(sub -> sub.startsWith(input))
                .filter(sub -> {
                    // Permissions für spezifische Subcommands prüfen
                    if (sub.equals("villager")) {
                        return sender.hasPermission("simpleeco.spawn.villager");
                    } else if (sub.equals("atm")) {
                        return sender.hasPermission("simpleeco.spawn.atm");
                    }
                    return true;
                })
                .collect(Collectors.toList());
        }
        
        return completions;
    }
} 