package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.fights.arena.Arena;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** {@code /glw arena tp <name>}: to the arena exit. */
public final class ArenaTpCommand extends SubArgument {

	private final ArenaDeps deps;

	public ArenaTpCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, ArenaDeps deps) {
		super(plugin, "tp", tree, parent);
		this.deps = deps;
		this.addSubArgument(deps.operand(plugin, tree, "name", null, this::run));
	}

	private void run(CommandSender sender, String name) {
		Arena arena = deps.registry().byName(name).orElse(null);
		if (arena == null) {
			sender.sendMessage(deps.text().text("Arenas.Not_Found", "name", name));
			return;
		}
		Player player = deps.player(sender);
		if (player == null) {
			return;
		}
		var exit = arena.exit();
		World world = Bukkit.getWorld(exit.world());
		if (world == null) {
			sender.sendMessage(deps.text().text("Arenas.Tp_World_Missing", "world", exit.world()));
			return;
		}
		player.teleport(new Location(world, exit.x(), exit.y(), exit.z(), exit.yaw(), exit.pitch()));
		sender.sendMessage(deps.text().text("Arenas.Tp", "name", arena.name()));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> ArenaDeps.usage(sender, "/glw arena tp <name>");
	}
}
