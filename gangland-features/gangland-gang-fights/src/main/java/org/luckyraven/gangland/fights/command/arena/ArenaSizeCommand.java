package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.gangland.fights.arena.ArenaBox;

/** {@code /glw arena size}: prints the draft box. */
public final class ArenaSizeCommand extends SubArgument {

	private final ArenaDeps deps;

	public ArenaSizeCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, ArenaDeps deps) {
		super(plugin, "size", tree, parent);
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
			draft.get().box().ifPresentOrElse(box -> sender.sendMessage(describe(deps, box)),
			                                  () -> sender.sendMessage(deps.text().text("Arenas.Size_Incomplete")));
		};
	}

	static String describe(ArenaDeps deps, ArenaBox box) {
		var c = box.cuboid();
		return deps.text().text("Arenas.Size", "x", c.maxX() - c.minX() + 1, "y", c.maxY() - c.minY() + 1, "z",
		                        c.maxZ() - c.minZ() + 1, "volume", box.volume());
	}
}
