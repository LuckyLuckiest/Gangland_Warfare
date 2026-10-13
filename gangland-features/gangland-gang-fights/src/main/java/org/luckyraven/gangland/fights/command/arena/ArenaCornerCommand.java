package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw arena corner}: the first call sets one corner where you stand, the second the other, a third starts over. */
public final class ArenaCornerCommand extends SubArgument {

	private final ArenaDeps deps;

	public ArenaCornerCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, ArenaDeps deps) {
		super(plugin, "corner", tree, parent);
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
			var at = player.getLocation();
			int n  = draft.get().nextCorner(at.getWorld() == null ? "" : at.getWorld().getName(), at.getBlockX(),
			                                at.getBlockY(), at.getBlockZ());
			sender.sendMessage(deps.text().text("Arenas.Corner_Set", "n", n, "x", at.getBlockX(), "y", at.getBlockY(),
			                                    "z", at.getBlockZ()));
			draft.get().box().ifPresent(box -> sender.sendMessage(ArenaSizeCommand.describe(deps, box)));
		};
	}
}
