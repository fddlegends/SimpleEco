package de.simpleeco.commands;

import de.simpleeco.SimpleEcoPlugin;
import de.simpleeco.config.ConfigManager;
import de.simpleeco.currency.BasicCurrency;
import de.simpleeco.scoreboard.ScoreboardManager;
import de.simpleeco.villager.ShopVillagerManager;
import de.simpleeco.bank.AtmVillagerManager;
import de.simpleeco.bank.BankManager;
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
import java.util.concurrent.CompletableFuture;

/**
 * Haupt-Command-Handler für alle SimpleEco-Commands
 * 
 * Behandelt:
 * - /eco balance [Spieler] - Zeigt Kontostand an (Bargeld und Bank)
 * - /eco pay <Spieler> <Betrag> - Überweist Geld
 * - /eco spawn <shop|atm> - Spawnt Entities
 */
public class EcoCommand implements CommandExecutor, TabCompleter {
    
    private final SimpleEcoPlugin plugin;
    private final BasicCurrency currency;
    private final BankManager bankManager;
    private final ConfigManager configManager;
    private final ScoreboardManager scoreboardManager;
    private final SpawnCommand spawnCommand;
    
    public EcoCommand(SimpleEcoPlugin plugin, BasicCurrency currency, ConfigManager configManager,
                     ShopVillagerManager shopVillagerManager, AtmVillagerManager atmVillagerManager) {
        this.plugin = plugin;
        this.currency = currency;
        this.bankManager = plugin.getBankManager();
        this.configManager = configManager;
        this.scoreboardManager = plugin.getScoreboardManager();
        
        // SpawnCommand als Subcommand-Handler erstellen
        this.spawnCommand = new SpawnCommand(plugin, configManager, shopVillagerManager, atmVillagerManager, currency);
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
            case "balance", "bal" -> handleBalance(sender, args);
            case "pay" -> handlePay(sender, args);
            case "spawn" -> {
                // Spawn-Argumente weiterleiten (ohne das "spawn" Argument)
                String[] spawnArgs = Arrays.copyOfRange(args, 1, args.length);
                return spawnCommand.handleSpawnCommand(sender, spawnArgs);
            }
            case "reload" -> handleReload(sender);
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
    private void handleBalance(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Eigenen Kontostand anzeigen
            if (!(sender instanceof Player player)) {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 "§cDieser Befehl kann nur von Spielern ausgeführt werden!");
                return;
            }
            
            showPlayerBalance(sender, player);
            
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
            
            showPlayerBalance(sender, targetPlayer);
            
        } else if (args.length == 4) {
            // Balance add/remove: /eco balance <player> <add|remove> <amount>
            if (!sender.hasPermission("simpleeco.balance.admin")) {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("noPermission"));
                return;
            }
            
            String targetName = args[1];
            String operation = args[2].toLowerCase();
            String amountString = args[3];
            
            // Ziel-Spieler finden
            Player targetPlayer = Bukkit.getPlayer(targetName);
            if (targetPlayer == null) {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("playerNotFound"));
                return;
            }
            
            // Operation validieren
            if (!operation.equals("add") && !operation.equals("remove")) {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 "§cVerwendung: /eco balance <Spieler> <add|remove> <Betrag>");
                return;
            }
            
            // Betrag parsen
            double amount;
            try {
                amount = Double.parseDouble(amountString);
            } catch (NumberFormatException e) {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("invalidAmount"));
                return;
            }
            
            // Betrag muss positiv sein
            if (amount <= 0) {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 configManager.getMessage("invalidAmount"));
                return;
            }
            
            // Operation ausführen
            if (operation.equals("add")) {
                handleBalanceAdd(sender, targetPlayer, amount);
            } else {
                handleBalanceRemove(sender, targetPlayer, amount);
            }
            
        } else {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§cVerwendung: /eco balance [Spieler] [add|remove] [Betrag]");
        }
    }
    
    /**
     * Behandelt den Reload-Command
     * 
     * @param sender Der Command-Sender
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("simpleeco.admin")) {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             configManager.getMessage("noPermission"));
            return;
        }
        
        try {
            // Konfiguration neu laden
            configManager.reload();
            
            // Scoreboard-Manager neu laden
            if (scoreboardManager != null) {
                scoreboardManager.reload();
            }
            
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§a§l✓ §7Konfiguration erfolgreich neu geladen!");
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§7Scoreboards wurden aktualisiert.");
            
        } catch (Exception e) {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§c§l✗ §7Fehler beim Neuladen der Konfiguration!");
            plugin.getLogger().severe("Fehler beim Neuladen der Konfiguration: " + e.getMessage());
        }
    }
    
    /**
     * Zeigt die Balance eines Spielers an (Bargeld und Bank)
     * 
     * @param sender Der Command-Sender
     * @param targetPlayer Der Spieler dessen Balance angezeigt wird
     */
    private void showPlayerBalance(CommandSender sender, Player targetPlayer) {
        // Beide Guthaben parallel laden
        CompletableFuture<Double> cashFuture = bankManager.getCashBalance(targetPlayer);
        CompletableFuture<Double> bankFuture = bankManager.getBankBalance(targetPlayer);
        
        CompletableFuture.allOf(cashFuture, bankFuture).thenRun(() -> {
            try {
                double cashBalance = cashFuture.get();
                double bankBalance = bankFuture.get();
                double totalBalance = cashBalance + bankBalance;
                
                String currencySymbol = configManager.getConfig().getString("currency.symbol", "G");
                boolean isOwnBalance = sender.equals(targetPlayer);
                
                // Header
                sender.sendMessage("§8§m          §r §6§lKontostand" + 
                    (isOwnBalance ? "" : " von §e" + targetPlayer.getName()) + " §8§m          ");
                
                // Bargeld
                sender.sendMessage("§a💵 Bargeld: §f" + formatAmount(cashBalance) + " " + currencySymbol);
                
                // Bank
                sender.sendMessage("§6🏦 Bank: §f" + formatAmount(bankBalance) + " " + currencySymbol);
                
                // Trennlinie
                sender.sendMessage("§8§m                                        ");
                
                // Gesamt
                sender.sendMessage("§e💰 Gesamt: §f" + formatAmount(totalBalance) + " " + currencySymbol);
                
                sender.sendMessage("§8§m                                        ");
                
            } catch (Exception e) {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 "§cFehler beim Laden der Kontostände!");
                plugin.getLogger().severe("Fehler beim Laden der Kontostände: " + e.getMessage());
            }
        }).exceptionally(throwable -> {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§cFehler beim Laden der Kontostände!");
            plugin.getLogger().severe("Fehler beim Laden der Kontostände: " + throwable.getMessage());
            return null;
        });
    }
    
    /**
     * Formatiert einen Betrag
     * 
     * @param amount Der Betrag
     * @return Formatierter String
     */
    private String formatAmount(double amount) {
        return String.format("%.2f", amount);
    }
    
    /**
     * Behandelt den Pay-Command
     * 
     * @param sender Der Command-Sender
     * @param args Command-Argumente
     */
    private void handlePay(CommandSender sender, String[] args) {
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
                
                // Scoreboards beider Spieler aktualisieren
                if (scoreboardManager != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        scoreboardManager.updatePlayerScoreboard(player);
                        scoreboardManager.updatePlayerScoreboard(targetPlayer);
                    }, 2L); // 0.1 Sekunden Verzögerung
                }
                
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
     * Fügt Geld zum Bargeld-Kontostand eines Spielers hinzu
     * 
     * @param sender Der Command-Sender
     * @param targetPlayer Der Ziel-Spieler
     * @param amount Der hinzuzufügende Betrag
     */
    private void handleBalanceAdd(CommandSender sender, Player targetPlayer, double amount) {
        currency.addBalance(targetPlayer, amount).thenAccept(newBalance -> {
            // Erfolgsnachrichten senden
            String currencySymbol = configManager.getConfig().getString("currency.symbol", "G");
            
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§a§l✓ §7Dem Spieler §e" + targetPlayer.getName() + 
                             " §7wurden §a" + formatAmount(amount) + " " + currencySymbol + 
                             " §7hinzugefügt!");
            
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§7Neuer Bargeld-Kontostand: §f" + formatAmount(newBalance) + " " + currencySymbol);
            
            // Dem Ziel-Spieler eine Benachrichtigung senden
            targetPlayer.sendMessage(configManager.getMessage("prefix") + 
                                   "§a§l✓ §7Dir wurden §a" + formatAmount(amount) + " " + currencySymbol + 
                                   " §7zu deinem Bargeld hinzugefügt!");
            
            // Scoreboard des Ziel-Spielers aktualisieren
            if (scoreboardManager != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    scoreboardManager.updatePlayerScoreboard(targetPlayer);
                }, 2L); // 0.1 Sekunden Verzögerung
            }
            
        }).exceptionally(throwable -> {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§cFehler beim Hinzufügen des Betrags!");
            return null;
        });
    }
    
    /**
     * Entfernt Geld vom Bargeld-Kontostand eines Spielers
     * 
     * @param sender Der Command-Sender
     * @param targetPlayer Der Ziel-Spieler
     * @param amount Der zu entfernende Betrag
     */
    private void handleBalanceRemove(CommandSender sender, Player targetPlayer, double amount) {
        // Erst prüfen, ob genügend Bargeld vorhanden ist
        currency.getBalance(targetPlayer).thenAccept(currentBalance -> {
            if (currentBalance < amount) {
                String currencySymbol = configManager.getConfig().getString("currency.symbol", "G");
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 "§c§l✗ §7Der Spieler §e" + targetPlayer.getName() + 
                                 " §7hat nicht genügend Bargeld!");
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 "§7Verfügbares Bargeld: §f" + formatAmount(currentBalance) + " " + currencySymbol + 
                                 " §8| §7Benötigt: §f" + formatAmount(amount) + " " + currencySymbol);
                return;
            }
            
            // Betrag entfernen
            currency.removeBalance(targetPlayer, amount).thenAccept(newBalance -> {
                // Erfolgsnachrichten senden
                String currencySymbol = configManager.getConfig().getString("currency.symbol", "G");
                
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 "§c§l✓ §7Dem Spieler §e" + targetPlayer.getName() + 
                                 " §7wurden §c" + formatAmount(amount) + " " + currencySymbol + 
                                 " §7vom Bargeld entfernt!");
                
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 "§7Neuer Bargeld-Kontostand: §f" + formatAmount(newBalance) + " " + currencySymbol);
                
                // Dem Ziel-Spieler eine Benachrichtigung senden
                targetPlayer.sendMessage(configManager.getMessage("prefix") + 
                                       "§c§l✗ §7Dir wurden §c" + formatAmount(amount) + " " + currencySymbol + 
                                       " §7von deinem Bargeld entfernt!");
                
                // Scoreboard des Ziel-Spielers aktualisieren
                if (scoreboardManager != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        scoreboardManager.updatePlayerScoreboard(targetPlayer);
                    }, 2L); // 0.1 Sekunden Verzögerung
                }
                
            }).exceptionally(throwable -> {
                sender.sendMessage(configManager.getMessage("prefix") + 
                                 "§cFehler beim Entfernen des Betrags!");
                return null;
            });
            
        }).exceptionally(throwable -> {
            sender.sendMessage(configManager.getMessage("prefix") + 
                             "§cFehler beim Prüfen des Kontostands!");
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
        
        // Admin-Commands nur anzeigen wenn Permission vorhanden
        if (sender.hasPermission("simpleeco.balance.admin")) {
            sender.sendMessage("§c/eco balance <Spieler> add <Betrag> §8- §7Fügt Geld hinzu");
            sender.sendMessage("§c/eco balance <Spieler> remove <Betrag> §8- §7Entfernt Geld");
        }
        
        // Spawn-Commands nur anzeigen wenn Permission vorhanden
        if (sender.hasPermission("simpleeco.spawn")) {
            sender.sendMessage("§e/eco spawn <shop|atm> §8- §7Spawnt Entities");
        }

        // Reload-Command nur anzeigen wenn Permission vorhanden
        if (sender.hasPermission("simpleeco.admin")) {
            sender.sendMessage("§e/eco reload §8- §7Lädt die Konfiguration neu");
        }
        
        sender.sendMessage("§e/eco help §8- §7Zeigt diese Hilfe an");
        sender.sendMessage("§8§m                                        ");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("simpleeco.use")) {
            return new ArrayList<>();
        }
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Erste Ebene: Subcommands
            List<String> subCommands = new ArrayList<>(Arrays.asList("balance", "pay", "help"));
            
            // Spawn hinzufügen wenn Permission vorhanden
            if (sender.hasPermission("simpleeco.spawn")) {
                subCommands.add("spawn");
            }
            
            // Reload hinzufügen wenn Permission vorhanden
            if (sender.hasPermission("simpleeco.admin")) {
                subCommands.add("reload");
            }
            
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
                
            } else if (subCommand.equals("spawn") && sender.hasPermission("simpleeco.spawn")) {
                // Spawn-Subcommands
                String[] spawnArgs = {args[1]};
                completions = spawnCommand.getSpawnTabComplete(sender, spawnArgs);
            }
            
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("balance") && sender.hasPermission("simpleeco.balance.admin")) {
                // Add/Remove subcommands für Balance-Command
                String input = args[2].toLowerCase();
                completions = Arrays.asList("add", "remove").stream()
                    .filter(op -> op.startsWith(input))
                    .collect(Collectors.toList());
                    
            } else if (subCommand.equals("pay")) {
                // Betrag-Vorschläge für Pay-Command
                completions = Arrays.asList("10", "50", "100", "500", "1000");
            }
            
        } else if (args.length == 4 && args[0].equalsIgnoreCase("balance") && sender.hasPermission("simpleeco.balance.admin")) {
            // Betrag-Vorschläge für Balance-Add/Remove
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