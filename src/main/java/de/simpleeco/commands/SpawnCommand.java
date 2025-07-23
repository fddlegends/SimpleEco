package de.simpleeco.commands;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.villager.ShopVillagerManager;
import de.simpleeco.bank.AtmVillagerManager;
import de.simpleeco.currency.BasicCurrency;
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
 * - /eco spawn shop - Spawnt einen Shop-Villager auf dem angeschauten Block
 * - /eco spawn atm - Spawnt einen ATM-Villager auf dem angeschauten Block
 */
public class SpawnCommand implements CommandExecutor, TabCompleter {
    
    private final SimpleEcoPlugin plugin;
    private final ConfigManager configManager;
    private final ShopVillagerManager shopVillagerManager;
    private final AtmVillagerManager atmVillagerManager;
    private final BasicCurrency currency;
    
    public SpawnCommand(SimpleEcoPlugin plugin, ConfigManager configManager, 
                       ShopVillagerManager shopVillagerManager, AtmVillagerManager atmVillagerManager,
                       BasicCurrency currency) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.shopVillagerManager = shopVillagerManager;
        this.atmVillagerManager = atmVillagerManager;
        this.currency = currency;
    }
    
    /**
     * Behandelt das Spawnen von Shop-Villagern
     * 
     * @param player Der Spieler der den Command ausführt
     */
    private void handleShopSpawn(Player player) {
        // Permissions für Shop-Spawn prüfen
        if (!player.hasPermission("simpleeco.spawn.shop")) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("noPermission"));
            return;
        }
        
        // Kosten prüfen und abziehen
        if (!checkAndChargeSpawnCost(player, "shopCost")) {
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
                // Shop-Villager erfolgreich gespawnt
            } else {
                player.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("villagerSpawnFailed"));
                // Kosten zurückerstatten bei Fehler
                refundSpawnCost(player, "shopCost");
            }
            
        } catch (Exception e) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("villagerSpawnFailed"));
            plugin.getLogger().severe("Fehler beim Spawnen des Shop-Villagers: " + e.getMessage());
            // Kosten zurückerstatten bei Fehler
            refundSpawnCost(player, "shopCost");
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
        
        // Kosten prüfen und abziehen
        if (!checkAndChargeSpawnCost(player, "atmCost")) {
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
                                 configManager.getMessage("atmSpawned"));
                // ATM-Villager erfolgreich gespawnt
            } else {
                player.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("atmSpawnFailed"));
                // Kosten zurückerstatten bei Fehler
                refundSpawnCost(player, "atmCost");
            }
            
        } catch (Exception e) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("atmSpawnFailed"));
            plugin.getLogger().severe("Fehler beim Spawnen des ATM-Villagers: " + e.getMessage());
            // Kosten zurückerstatten bei Fehler
            refundSpawnCost(player, "atmCost");
        }
    }

    /**
     * Prüft und berechnet die Spawn-Kosten
     * 
     * @param player Der Spieler
     * @param costConfigKey Der Konfigurationsschlüssel für die Kosten
     * @return true wenn Kosten erfolgreich abgezogen oder nicht erforderlich
     */
    private boolean checkAndChargeSpawnCost(Player player, String costConfigKey) {
        // Prüfen ob Kosten aktiviert sind
        if (!configManager.getConfig().getBoolean("spawnCosts.enabled", true)) {
            return true;
        }
        
        // Kosten aus Konfiguration laden
        double cost = configManager.getConfig().getDouble("spawnCosts." + costConfigKey, 0.0);
        
        if (cost <= 0) {
            return true; // Keine Kosten konfiguriert
        }
        
        // Prüfen ob Kosten für alle Spieler erzwungen werden sollen
        boolean enforceForAll = configManager.getConfig().getBoolean("spawnCosts.enforceForAll", true);
        
        if (!enforceForAll) {
            // Nur normale Spieler zahlen - Admins sind befreit
            boolean freeForAdmins = configManager.getConfig().getBoolean("spawnCosts.freeForAdmins", false);
            if (freeForAdmins && player.hasPermission("simpleeco.spawn.free")) {
                player.sendMessage(configManager.getMessage("prefix") + 
                                 "§a§lKostenloses Spawning §7(Admin-Berechtigung)");
                return true;
            }
        }
        
        // Wenn enforceForAll = true ist, zahlen alle Spieler (auch OPs/Admins)
        
        try {
            // Guthaben prüfen (synchron für bessere UX)
            double balance = currency.getBalance(player.getUniqueId()).get();
            if (balance < cost) {
                String message = configManager.getMessage("insufficientFundsForSpawn",
                    "amount", String.valueOf(cost),
                    "currency", configManager.getConfig().getString("currency.symbol", "G"));
                player.sendMessage(configManager.getMessage("prefix") + message);
                return false;
            }
            
            // Geld abziehen
            currency.removeBalance(player.getUniqueId(), cost).get();
            String message = configManager.getMessage("spawnCostCharged",
                "amount", String.valueOf(cost),
                "currency", configManager.getConfig().getString("currency.symbol", "G"));
            player.sendMessage(configManager.getMessage("prefix") + message);
            
            return true;
        } catch (Exception e) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             "§cFehler beim Verarbeiten der Zahlung!");
            plugin.getLogger().severe("Fehler beim Spawn-Kosten abziehen: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Erstattet Spawn-Kosten zurück bei Fehlern
     * 
     * @param player Der Spieler
     * @param costConfigKey Der Konfigurationsschlüssel für die Kosten
     */
    private void refundSpawnCost(Player player, String costConfigKey) {
        // Prüfen ob Kosten aktiviert sind
        if (!configManager.getConfig().getBoolean("spawnCosts.enabled", true)) {
            return;
        }
        
        // Prüfen ob Spieler kostenlose Berechtigung hat
        if (player.hasPermission("simpleeco.spawn.free") && 
            configManager.getConfig().getBoolean("spawnCosts.freeForAdmins", true)) {
            return;
        }
        
        double cost = configManager.getConfig().getDouble("spawnCosts." + costConfigKey, 0.0);
        
        if (cost > 0) {
            try {
                currency.addBalance(player.getUniqueId(), cost).get();
                String message = configManager.getMessage("spawnCostRefunded",
                    "amount", String.valueOf(cost),
                    "currency", configManager.getConfig().getString("currency.symbol", "G"));
                player.sendMessage(configManager.getMessage("prefix") + message);
            } catch (Exception e) {
                plugin.getLogger().severe("Fehler beim Zurückerstatten der Spawn-Kosten: " + e.getMessage());
            }
        }
    }

    /**
     * Sendet die Verwendungshinweise
     * 
     * @param sender Der Command-Sender
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§8§m          §r §6§lSimpleEco Spawn Commands §8§m          ");
        sender.sendMessage("§e/eco spawn shop §8- §7Spawnt einen Shop-Villager");
        sender.sendMessage("§e/eco spawn atm §8- §7Spawnt einen ATM-Villager");
        sender.sendMessage("§e/eco spawn help §8- §7Zeigt diese Hilfe an");
        
        // Kosteneninformationen anzeigen
        if (configManager.getConfig().getBoolean("spawnCosts.enabled", true)) {
            sender.sendMessage("§8§m                                        ");
            sender.sendMessage("§c§lKosten:");
            double shopCost = configManager.getConfig().getDouble("spawnCosts.shopCost", 0.0);
            double atmCost = configManager.getConfig().getDouble("spawnCosts.atmCost", 0.0);
            String symbol = configManager.getConfig().getString("currency.symbol", "G");
            
            if (shopCost > 0) {
                sender.sendMessage("§7Shop: §e" + shopCost + " " + symbol);
            }
            if (atmCost > 0) {
                sender.sendMessage("§7ATM: §e" + atmCost + " " + symbol);
            }
            
            // Informationen über kostenlose Berechtigung
            boolean enforceForAll = configManager.getConfig().getBoolean("spawnCosts.enforceForAll", true);
            boolean freeForAdmins = configManager.getConfig().getBoolean("spawnCosts.freeForAdmins", false);
            
            if (enforceForAll) {
                sender.sendMessage("§7§o(Alle Spieler zahlen Kosten)");
            } else if (freeForAdmins && sender.hasPermission("simpleeco.spawn.free")) {
                sender.sendMessage("§a§o(Du hast kostenlose Berechtigung)");
            }
        }
        
        sender.sendMessage("§8§m                                        ");
    }
    
    // Diese Methode wird jetzt von der übergeordneten EcoCommand aufgerufen
    public boolean handleSpawnCommand(CommandSender sender, String[] args) {
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
            case "shop" -> handleShopSpawn(player);
            case "atm" -> handleAtmSpawn(player);
            case "help", "?" -> sendUsage(sender);
            default -> sendUsage(sender);
        }
        
        return true;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handleSpawnCommand(sender, args);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("simpleeco.spawn")) {
            return new ArrayList<>();
        }
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Erste Ebene: Subcommands
            List<String> subCommands = Arrays.asList("shop", "atm", "help");
            String input = args[0].toLowerCase();
            
            completions = subCommands.stream()
                .filter(sub -> sub.startsWith(input))
                .filter(sub -> {
                    // Permissions für spezifische Subcommands prüfen
                    if (sub.equals("shop")) {
                        return sender.hasPermission("simpleeco.spawn.shop");
                    } else if (sub.equals("atm")) {
                        return sender.hasPermission("simpleeco.spawn.atm");
                    }
                    return true;
                })
                .collect(Collectors.toList());
        }
        
        return completions;
    }
    
    public List<String> getSpawnTabComplete(CommandSender sender, String[] args) {
        return onTabComplete(sender, null, "spawn", args);
    }
} 