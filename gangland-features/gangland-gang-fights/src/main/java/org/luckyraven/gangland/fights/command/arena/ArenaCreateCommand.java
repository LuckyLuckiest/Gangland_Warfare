package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;

/** {@code /glw arena create <name>}: starts this staff member's draft. */
public final class ArenaCreateCommand extends SubArgument {

	private final ArenaDeps deps;

	public ArenaCreateCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, ArenaDeps deps) {
		super(plugin, "create", tree, parent);
		this.deps = deps;
		this.addSubArgument(deps.operand(plugin, tree, "name", List.of(), this::create));
	}

	private void create(CommandSender sender, String name) {
		Player player = deps.player(sender);
		if (player == null) {
			return;
		}
		if (!name.matches(ArenaDeps.NAME_PATTERN)) {
			sender.sendMessage(deps.text().text("Arenas.Name_Invalid"));
		} else if (deps.registry().byName(name).isPresent()) {
			sender.sendMessage(deps.text().text("Arenas.Create_Exists", "name", name));
		} else {
			deps.drafts().start(player.getUniqueId(), name);
			sender.sendMessage(deps.text().text("Arenas.Created", "name", name));
		}
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> ArenaDeps.usage(sender, "/glw arena create <name>");
	}
}
