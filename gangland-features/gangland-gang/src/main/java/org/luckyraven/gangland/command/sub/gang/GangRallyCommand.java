package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.safehouse.SafeSpot;
import org.luckyraven.gangland.gang.safehouse.Safehouse;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;

/**
 * {@code /glw gang rally [safehouse]} asks the online gang members to join the caller (or a safehouse) and
 * {@code /glw gang rally join} answers within the consent window. The call needs the {@code rally} rank node; the
 * answer needs none, only gang membership. Each join is one ordinary gang teleport, so the turf veto and the combat
 * tag apply to it.
 */
final class GangRallyCommand extends SubArgument {

	private final GangSafehouseService service;

	GangRallyCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangSafehouseService service) {
		super(gangland, "rally", tree, parent);

		this.service = service;

		this.addSubArgument(new Join(gangland, tree, this));

		OptionalArgument safehouse = new OptionalArgument(gangland, tree,
		                                                  (argument, sender, args) -> call(sender, args[2]),
		                                                  this::names);
		safehouse.setDisplayName("safehouse");
		this.addSubArgument(safehouse);
	}

	/** Bare {@code /glw gang rally} calls the gang to the caller position. */
	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> call(sender, null);
	}

	private void call(CommandSender sender, String safehouseName) {
		if (!(sender instanceof Player player)) return;

		int gangId = service.gate(player, GangPermissions.RALLY);
		if (gangId < 0) return;

		Safehouse target = null;
		if (safehouseName != null) {
			target = service.find(gangId, safehouseName).orElse(null);
			if (target == null) {
				player.sendMessage(service.text("Safehouse.Not_Found", "&cYour gang has no safehouse named %name%.",
				                                "name", safehouseName));
				return;
			}
		}

		if (target == null && service.rules().isSafehouseSafeCheck() && !SafeSpot.isSafe(player.getLocation())) {
			player.sendMessage(service.text("Safehouse.Unsafe", "&c%name% is not safe to stand on.", "name",
			                                player.getName()));
			return;
		}

		int asked = service.rally(player, gangId, target);
		if (asked == 0) {
			player.sendMessage(service.text("Safehouse.Rally_None", "&7No other member of your gang is online."));
			return;
		}
		player.sendMessage(service.text("Safehouse.Rally_Sent", "&aRally called: %count% members asked.", "count",
		                                asked));
	}

	private List<String> names(CommandSender sender) {
		if (!(sender instanceof Player player)) return List.of();

		int gangId = service.gangIdOf(player);
		return gangId < 0 ? List.of() : service.list(gangId).stream().map(Safehouse::name).toList();
	}

	/** {@code rally join}: answer the open call. */
	private final class Join extends SubArgument {

		Join(JavaPlugin gangland, Tree<Argument> tree, Argument parent) {
			super(gangland, "join", tree, parent);
		}

		@Override
		protected TriConsumer<Argument, CommandSender, String[]> action() {
			return (argument, sender, args) -> {
				if (sender instanceof Player player) service.joinRally(player);
			};
		}
	}
}
