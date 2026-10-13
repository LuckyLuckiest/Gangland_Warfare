package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.events.gang.GangSafehouseSetEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.safehouse.SafeSpot;
import org.luckyraven.gangland.gang.safehouse.Safehouse;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code /glw gang safehouse [name]} teleports to a safehouse, with {@code set <name>}, {@code remove <name>} and
 * {@code list}. Setting and removing need the {@code safehouse.manage} rank node, the rest {@code safehouse}; the count
 * is capped by the gang level. The rank, world and switch gates live in {@link GangSafehouseService#gate}.
 */
final class GangSafehouseCommand extends SubArgument {

	private static final Pattern     NAME     = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Set<String> RESERVED = Set.of("set", "remove", "list", "join");

	private final GangSafehouseService service;
	private final GangLimits           limits;

	// worldPolicy stays in the constructor GangCommand calls; the world gate itself runs inside the service
	GangSafehouseCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangSafehouseService service,
	                     GangLimits limits, GangWorldPolicy worldPolicy) {
		super(gangland, "safehouse", tree, parent);

		this.service = service;
		this.limits  = limits;

		OptionalArgument name = new OptionalArgument(gangland, tree,
		                                             (argument, sender, args) -> teleport(sender, args[2]),
		                                             this::names);
		name.setDisplayName("name");
		this.addSubArgument(name);

		Argument set = new SetVerb(gangland, tree);
		OptionalArgument setName = new OptionalArgument(gangland, tree,
		                                                (argument, sender, args) -> set(sender, args[3]));
		setName.setDisplayName("name");
		set.addSubArgument(setName);
		this.addSubArgument(set);

		Argument remove = new RemoveVerb(gangland, tree);
		OptionalArgument removeName = new OptionalArgument(gangland, tree,
		                                                   (argument, sender, args) -> remove(sender, args[3]),
		                                                   this::names);
		removeName.setDisplayName("name");
		remove.addSubArgument(removeName);
		this.addSubArgument(remove);

		this.addSubArgument(new ListVerb(gangland, tree));
	}

	/** Bare {@code /glw gang safehouse} lists the gang safehouses. */
	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> list(sender);
	}

	private void teleport(CommandSender sender, String name) {
		if (!(sender instanceof Player player)) return;

		int gangId = service.gate(player, GangPermissions.SAFEHOUSE);
		if (gangId < 0) return;

		Safehouse safehouse = service.find(gangId, name).orElse(null);
		if (safehouse == null) {
			notFound(player, name);
			return;
		}
		service.teleport(player, safehouse);
	}

	private void set(CommandSender sender, String name) {
		if (!(sender instanceof Player player)) return;

		int gangId = service.gate(player, GangPermissions.SAFEHOUSE_MANAGE);
		if (gangId < 0) return;

		if (!NAME.matcher(name).matches() || RESERVED.contains(name.toLowerCase(Locale.ROOT))) {
			player.sendMessage(service.text("Safehouse.Name_Invalid",
			                                "&cA safehouse name is 1 to 16 letters, digits or underscores."));
			return;
		}

		int max = limits.maxSafehouses(gangId);
		if (service.find(gangId, name).isEmpty() && max > 0 && service.count(gangId) >= max) {
			player.sendMessage(service.text("Safehouse.Cap",
			                                "&cYour gang level allows %max% safehouses; remove one first.", "count",
			                                service.count(gangId), "max", max));
			return;
		}

		Location at    = player.getLocation();
		World    world = at.getWorld();
		if (world == null) return;

		if (service.rules().isSafehouseSafeCheck() && !SafeSpot.isSafe(at)) {
			player.sendMessage(service.text("Safehouse.Unsafe", "&c%name% is not safe to stand on.", "name", name));
			return;
		}

		GangSafehouseSetEvent event = new GangSafehouseSetEvent(gangId, at, name);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			player.sendMessage(service.text("Safehouse.Set_Refused", "&cYou cannot set a safehouse here."));
			return;
		}

		service.add(new Safehouse(gangId, name, world.getName(), at.getX(), at.getY(), at.getZ(), at.getYaw(),
		                          at.getPitch()));
		player.sendMessage(service.text("Safehouse.Set", "&aSafehouse %name% set (%count%/%max%).", "name", name,
		                                "count", service.count(gangId), "max", max == 0 ? "unlimited" : max));
	}

	private void remove(CommandSender sender, String name) {
		if (!(sender instanceof Player player)) return;

		int gangId = service.gate(player, GangPermissions.SAFEHOUSE_MANAGE);
		if (gangId < 0) return;

		if (service.find(gangId, name).isEmpty()) {
			notFound(player, name);
			return;
		}
		service.remove(gangId, name);
		player.sendMessage(service.text("Safehouse.Removed", "&7Safehouse %name% removed.", "name", name));
	}

	private void list(CommandSender sender) {
		if (!(sender instanceof Player player)) return;

		int gangId = service.gate(player, GangPermissions.SAFEHOUSE);
		if (gangId < 0) return;

		int max = limits.maxSafehouses(gangId);
		player.sendMessage(service.text("Safehouse.List_Header", "&6Safehouses &7(%count%/%max%)", "count",
		                                service.count(gangId), "max", max == 0 ? "unlimited" : max));
		for (Safehouse safehouse : service.list(gangId)) {
			player.sendMessage(service.text("Safehouse.List_Row", "&7- &f%name% &7(%world% %x% %y% %z%)", "name",
			                                safehouse.name(), "world", safehouse.world(), "x", (int) safehouse.x(),
			                                "y", (int) safehouse.y(), "z", (int) safehouse.z()));
		}
	}

	private void notFound(Player player, String name) {
		player.sendMessage(service.text("Safehouse.Not_Found", "&cYour gang has no safehouse named %name%.", "name",
		                                name));
	}

	private void missingName(CommandSender sender) {
		sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	/** Tab completion: the names of the sender gang safehouses. */
	private List<String> names(CommandSender sender) {
		if (!(sender instanceof Player player)) return List.of();

		int gangId = service.gangIdOf(player);
		return gangId < 0 ? List.of() : service.list(gangId).stream().map(Safehouse::name).toList();
	}

	/*
	 * The verbs are inner classes whose action reads only the outer instance: SubArgument's constructor calls action()
	 * before any field of the subclass is assigned.
	 */

	private final class SetVerb extends SubArgument {

		SetVerb(JavaPlugin gangland, Tree<Argument> tree) {
			super(gangland, "set", tree, GangSafehouseCommand.this);
		}

		@Override
		protected TriConsumer<Argument, CommandSender, String[]> action() {
			return (argument, sender, args) -> missingName(sender);
		}
	}

	private final class RemoveVerb extends SubArgument {

		RemoveVerb(JavaPlugin gangland, Tree<Argument> tree) {
			super(gangland, "remove", tree, GangSafehouseCommand.this);
		}

		@Override
		protected TriConsumer<Argument, CommandSender, String[]> action() {
			return (argument, sender, args) -> missingName(sender);
		}
	}

	private final class ListVerb extends SubArgument {

		ListVerb(JavaPlugin gangland, Tree<Argument> tree) {
			super(gangland, "list", tree, GangSafehouseCommand.this);
		}

		@Override
		protected TriConsumer<Argument, CommandSender, String[]> action() {
			return (argument, sender, args) -> list(sender);
		}
	}
}
