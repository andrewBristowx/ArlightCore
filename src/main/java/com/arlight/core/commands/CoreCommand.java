package com.arlight.core.commands;

import com.arlight.core.ArlightCorePlugin;
import com.arlight.core.items.CoreItems;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CoreCommand implements CommandExecutor, TabCompleter {

    private final ArlightCorePlugin plugin;

    public CoreCommand(ArlightCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("items", "reward", "xp", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reward")) {
            return filter(Arrays.asList("set", "remove", "list"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("xp")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reward")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove"))) {
            return filter(plugin.getRewardManager().getAll().keySet().stream().map(String::valueOf).collect(Collectors.toList()), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /core <items|reward|xp|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "items": {
                if (!(sender instanceof Player)) return true;
                Player player = (Player) sender;
                player.getInventory().addItem(CoreItems.createSelectorItem(plugin));
                player.getInventory().addItem(CoreItems.createRewardsItem(plugin));
                sender.sendMessage(ChatColor.GREEN + "Te dimos los items de minijuegos y recompensas.");
                return true;
            }

            case "reward": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /core reward <set|remove|list> [nivel]");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "set": {
                        if (!(sender instanceof Player)) {
                            sender.sendMessage(ChatColor.RED + "Solo un jugador sosteniendo el item puede hacer esto.");
                            return true;
                        }
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core reward set <nivel>");
                            return true;
                        }
                        int level;
                        try {
                            level = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "El nivel tiene que ser un numero.");
                            return true;
                        }
                        Player player = (Player) sender;
                        ItemStack held = player.getInventory().getItemInMainHand();
                        if (held.getType().isAir()) {
                            sender.sendMessage(ChatColor.RED + "Tenes que sostener el item que queres dar de recompensa.");
                            return true;
                        }
                        plugin.getRewardManager().setReward(level, held);
                        sender.sendMessage(ChatColor.GREEN + "Recompensa del nivel " + level + " actualizada (" + held.getAmount() + "x " + held.getType() + ").");
                        return true;
                    }
                    case "remove": {
                        if (args.length < 3) {
                            sender.sendMessage(ChatColor.RED + "Uso: /core reward remove <nivel>");
                            return true;
                        }
                        try {
                            int level = Integer.parseInt(args[2]);
                            plugin.getRewardManager().removeReward(level);
                            sender.sendMessage(ChatColor.GREEN + "Recompensa del nivel " + level + " eliminada.");
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "El nivel tiene que ser un numero.");
                        }
                        return true;
                    }
                    case "list": {
                        sender.sendMessage(ChatColor.GOLD + "Niveles con recompensa: " + ChatColor.WHITE
                                + plugin.getRewardManager().getAll().keySet());
                        return true;
                    }
                    default:
                        sender.sendMessage(ChatColor.RED + "Subcomando de reward desconocido.");
                        return true;
                }
            }

            case "xp": {
                if (!checkAdmin(sender)) return true;
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Uso: /core xp <jugador> <cantidad>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Jugador no encontrado (debe estar conectado).");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    plugin.getLevelManager().addXp(target.getUniqueId(), amount);
                    sender.sendMessage(ChatColor.GREEN + "Se le dio " + amount + " XP a " + target.getName() + ".");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "La cantidad tiene que ser un numero.");
                }
                return true;
            }

            case "reload": {
                if (!checkAdmin(sender)) return true;
                plugin.reloadCoreConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuracion recargada.");
                return true;
            }

            default:
                sender.sendMessage(ChatColor.RED + "Subcomando desconocido.");
                return true;
        }
    }

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission("arlightcore.admin")) {
            sender.sendMessage(ChatColor.RED + "No tenes permiso para hacer eso.");
            return false;
        }
        return true;
    }
}
