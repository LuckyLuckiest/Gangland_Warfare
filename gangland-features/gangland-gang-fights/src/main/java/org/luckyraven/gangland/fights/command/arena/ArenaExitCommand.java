package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw arena exit}: the point fighters return to, where you stand. */
public final class ArenaExitCommand extends SubArgument {

	private final ArenaDeps deps;

	public ArenaExitCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, ArenaDeps deps) {
		super(plugin, "exit", tree, parent);
		this.deps = deps;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player player = deps.player(sender);
			if (player == null) {
				return;
			}
			var draft = deps.drafts().of(player.getUniqueId());
			if (draft.isEmpty()) {
				sender.sendMessage(deps.text().text("Arenas.No_Draft"));
				return;
			}
			draft.get().setExit(ArenaDeps.point(player.getLocation()));
			sender.sendMessage(deps.text().text("Arenas.Exit_Set"));
		};
	}
}
