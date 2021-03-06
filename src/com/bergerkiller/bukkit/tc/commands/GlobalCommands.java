package com.bergerkiller.bukkit.tc.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartGroupStore;
import com.bergerkiller.bukkit.tc.pathfinding.PathNode;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import com.bergerkiller.bukkit.common.MessageBuilder;
import com.bergerkiller.bukkit.common.permissions.NoPermissionException;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.WorldUtil;

public class GlobalCommands {

	public static boolean execute(CommandSender sender, String[] args) throws NoPermissionException {
		if (args[0].equals("removeall") || args[0].equals("destroyall")) {
			Permission.COMMAND_DESTROYALL.handle(sender);
			if (args.length == 2) {
				String cname = args[1].toLowerCase();
				World w = null;
				for (World world : Bukkit.getServer().getWorlds()) {
					String wname = world.getName().toLowerCase();
					if (wname.equals(cname)) {
						w = world;
						break;
					}
				}
				if (w == null) {
					for (World world : Bukkit.getServer().getWorlds()) {
						String wname = world.getName().toLowerCase();
						if (wname.contains(cname)) {
							w = world;
							break;
						}
					}
				}
				if (w != null) {
					int count = OfflineGroupManager.destroyAll(w);
					sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");	
				} else {
					sender.sendMessage(ChatColor.RED + "World not found!");
				}
			} else {
				int count = OfflineGroupManager.destroyAll();
				sender.sendMessage(ChatColor.RED.toString() + count + " (visible) trains have been destroyed!");	
			}
			return true;
		} else if (args[0].equals("reroute")) {
			Permission.COMMAND_REROUTE.handle(sender);
			PathNode.clearAll();
			sender.sendMessage(ChatColor.YELLOW + "All train routings will be recalculated.");
			return true;
		} else if (args[0].equals("reload")) {
			Permission.COMMAND_RELOAD.handle(sender);
			TrainProperties.loadDefaults();
			TrainCarts.plugin.loadConfig();
			sender.sendMessage(ChatColor.YELLOW + "Configuration has been reloaded.");
			return true;
		} else if (args[0].equals("saveall")) {
			Permission.COMMAND_SAVEALL.handle(sender);
			TrainCarts.plugin.save();
			sender.sendMessage(ChatColor.YELLOW + "TrainCarts' information has been saved to file.");
			return true;
		} else if (args[0].equals("fixbugged")) {
			Permission.COMMAND_FIXBUGGED.handle(sender);
			OfflineGroupManager.removeBuggedMinecarts();
			sender.sendMessage(ChatColor.YELLOW + "Bugged minecarts have been forcibly removed.");
			return true;
		} else if (args[0].equals("list")) {
			int count = 0, moving = 0;
			for (MinecartGroup group : MinecartGroupStore.getGroups()) {
				count++;
				if (group.isMoving()) {
					moving++;
				}
			}
			count += OfflineGroupManager.getStoredCount();
			int minecartCount = 0;
			for (World world : WorldUtil.getWorlds()) {
				for (org.bukkit.entity.Entity e : WorldUtil.getEntities(world)) {
					if (e instanceof Minecart) {
						minecartCount++;
					}
				}
			}
			MessageBuilder builder = new MessageBuilder();
			builder.green("There are ").yellow(count).green(" trains on this server (of which ");
			builder.yellow(moving).green(" are moving)");
			builder.newLine().green("There are ").yellow(minecartCount).green(" minecart entities");
			builder.send(sender);
			if (sender instanceof Player) {
				if (args.length == 2 && LogicUtil.contains(args[1], "renamed", "rename", "ren", "name", "named")) {
					list((Player) sender, true);
				} else {
					list((Player) sender, false);
				}
			} else {
				sender.sendMessage("Consoles don't own trains!");
			}
			return true;
		} else if (args[0].equals("edit")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage("Can not edit a train through the console!");
				return true;
			}
			if (args.length == 2) {
				String name = args[1];
				TrainProperties prop = TrainProperties.exists(name) ? TrainProperties.get(name) : null;
				if (prop != null && !prop.isEmpty()) {
					if (prop.isOwner((Player) sender)) {
						CartPropertiesStore.setEditing((Player) sender, prop.get(0));
						sender.sendMessage(ChatColor.GREEN + "You are now editing train '" + prop.getTrainName() + "'!");	
					} else {
						sender.sendMessage(ChatColor.RED + "You do not own this train and can not edit it!");
					}
					return true;
				} else {
					sender.sendMessage(ChatColor.RED + "Could not find a valid train named '" + name + "'!");	
				}
			} else {
				sender.sendMessage(ChatColor.RED + "Please enter the exact name of the train to edit");	
			}
			list((Player) sender, false);
			return true;
		}
		return false;
	}

	public static void list(Player player, boolean named) {
		MessageBuilder builder = new MessageBuilder();
		builder.yellow("You are the proud owner of the following trains:");
		builder.newLine().setSeparator(ChatColor.WHITE, " / ");
		boolean found = false;
		for (TrainProperties prop : TrainProperties.getAll()) {
			if (!prop.isOwner(player)) continue;
			if (named && !prop.isTrainRenamed()) continue;
			found = true;
			if (prop.isLoaded()) {
				builder.green(prop.getTrainName());
			} else {
				builder.red(prop.getTrainName());
			}
		}
		if (found) {
			builder.send(player);
		} else if (named) {
			player.sendMessage(ChatColor.RED + "You do not own any renamed trains you can edit.");
		} else {
			player.sendMessage(ChatColor.RED + "You do not own any trains you can edit.");
		}
	}

}
