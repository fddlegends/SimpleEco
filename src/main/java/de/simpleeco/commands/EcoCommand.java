package de.simpleeco.commands;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.currency.BasicCurrency;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command-Handler für alle Economy-Commands
 * 
 * Behandelt:
 * - /eco balance [Spieler] - Zeigt Kontostand an
 * - /eco pay <Spieler> <Betrag> - Überweist Geld
 */
public class EcoCommand implements CommandExecutor, TabCompleter {
    
    private final SimpleEcoPlugin plugin;
    private final BasicCurrency currency;
    private final ConfigManager configManager;
    
    public EcoCommand(SimpleEcoPlugin plugin, BasicCurrency currency, ConfigManager configManager) {
        this.plugin = plugin;
        this.currency = currency;
        this.configManager = configManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("simpleeco.use")) {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("noPermission"));
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "balance", "bal", "b" -> handleBalanceCommand(sender, args);
            case "pay", "transfer", "send" -> handlePayCommand(sender, args);
            case "help", "?" -> sendUsage(sender);
            default -> sendUsage(sender);
        }
        
        return true;
    }
    
    /**
     * Behandelt den Balance-Command
     * 
     * @param sender Der Command-Sender
     * @param args Command-Argumente
     */
    private void handleBalanceCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Eigenen Kontostand anzeigen
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 "§cDieser Befehl kann nur von Spielern ausgeführt werden!");
                return;
            }
            
            currency.getBalance(player).thenAccept(balance -> {
                String message = configManager.getMessage("balanceYour", 
                    "balance", currency.formatAmount(balance),
                    "currency", configManager.getCurrencyName());
                player.sendMessage(configManager.getMessage("prefix") + message);
            }).exceptionally(throwable -> {
                player.sendMessage(configManager.getMessage("prefix") + 
                                 "§cFehler beim Laden des Kontostands!");
                plugin.getLogger().severe("Fehler beim Laden des Kontostands: " + throwable.getMessage());
                return null;
            });
            
        } else if (args.length == 2) {
            // Kontostand eines anderen Spielers anzeigen
            if (!sender.hasPermission("simpleeco.balance.other")) {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("noPermission"));
                return;
            }
            
            String targetName = args[1];
            Player targetPlayer = Bukkit.getPlayer(targetName);
            
            if (targetPlayer == null) {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("playerNotFound"));
                return;
            }
            
            currency.getBalance(targetPlayer).thenAccept(balance -> {
                String message = configManager.getMessage("balanceOther",
                    "player", targetPlayer.getName(),
                    "balance", currency.formatAmount(balance),
                    "currency", configManager.getCurrencyName());
                sender.sendMessage(configManager.getMessage("prefix") + message);
            }).exceptionally(throwable -> {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 "§cFehler beim Laden des Kontostands!");
                plugin.getLogger().severe("Fehler beim Laden des Kontostands: " + throwable.getMessage());
                return null;
            });
            
        } else {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§cVerwendung: /eco balance [Spieler]");
        }
    }
    
    /**
     * Behandelt den Pay-Command
     * 
     * @param sender Der Command-Sender
     * @param args Command-Argumente
     */
    private void handlePayCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§cDieser Befehl kann nur von Spielern ausgeführt werden!");
            return;
        }
        
        if (args.length != 3) {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§cVerwendung: /eco pay <Spieler> <Betrag>");
            return;
        }
        
        String targetName = args[1];
        String amountString = args[2];
        
        // Ziel-Spieler finden
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer == null) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("playerNotFound"));
            return;
        }
        
        // Sich selbst kann man nicht Geld überweisen
        if (targetPlayer.equals(player)) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             "§cDu kannst dir nicht selbst Geld überweisen!");
            return;
        }
        
        // Betrag parsen
        double amount;
        try {
            amount = Double.parseDouble(amountString);
        } catch (NumberFormatException e) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("invalidAmount"));
            return;
        }
        
        // Betrag muss positiv sein
        if (amount <= 0) {
            player.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("invalidAmount"));
            return;
        }
        
        // Überweisung durchführen
        currency.transferBalance(player, targetPlayer, amount).thenAccept(success -> {
            if (success) {
                // Erfolgsnachrichten senden
                String senderMessage = configManager.getMessage("paymentSent",
                    "amount", currency.formatAmount(amount),
                    "currency", configManager.getCurrencyName(),
                    "player", targetPlayer.getName());
                player.sendMessage(configManager.getMessage("prefix") + senderMessage);
                
                String receiverMessage = configManager.getMessage("paymentReceived",
                    "amount", currency.formatAmount(amount),
                    "currency", configManager.getCurrencyName(),
                    "player", player.getName());
                targetPlayer.sendMessage(configManager.getMessage("prefix") + receiverMessage);
                
            } else {
                player.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("insufficientFunds"));
            }
        }).exceptionally(throwable -> {
            player.sendMessage(configManager.getMessage("prefix") + 
                             "§cFehler bei der Überweisung!");
            plugin.getLogger().severe("Fehler bei Überweisung: " + throwable.getMessage());
            return null;
        });
    }
    
    /**
     * Sendet die Verwendungshinweise
     * 
     * @param sender Der Command-Sender
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§8§m          §r §6§lSimpleEco Commands §8§m          ");
        sender.sendMessage("§e/eco balance [Spieler] §8- §7Zeigt Kontostand an");
        sender.sendMessage("§e/eco pay <Spieler> <Betrag> §8- §7Überweist Geld");
        sender.sendMessage("§e/eco help §8- §7Zeigt diese Hilfe an");
        sender.sendMessage("§8§m                                    ");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("simpleeco.use")) {
            return new ArrayList<>();
        }
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Erste Ebene: Subcommands
            List<String> subCommands = Arrays.asList("balance", "pay", "help");
            String input = args[0].toLowerCase();
            
            completions = subCommands.stream()
                .filter(sub -> sub.startsWith(input))
                .collect(Collectors.toList());
                
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("balance") && sender.hasPermission("simpleeco.balance.other")) {
                // Spielernamen für Balance-Command
                completions = getOnlinePlayerNames(args[1]);
                
            } else if (subCommand.equals("pay")) {
                // Spielernamen für Pay-Command (außer dem Sender selbst)
                completions = getOnlinePlayerNames(args[1]);
                if (sender instanceof Player player) {
                    completions.remove(player.getName());
                }
            }
            
        } else if (args.length == 3 && args[0].equalsIgnoreCase("pay")) {
            // Betrag-Vorschläge für Pay-Command
            completions = Arrays.asList("10", "50", "100", "500", "1000");
        }
        
        return completions;
    }
    
    /**
     * Holt eine Liste von Online-Spielernamen die mit dem Input beginnen
     * 
     * @param input Der eingegebene Text
     * @return Liste passender Spielernamen
     */
    private List<String> getOnlinePlayerNames(String input) {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
            .collect(Collectors.toList());
    }
} 