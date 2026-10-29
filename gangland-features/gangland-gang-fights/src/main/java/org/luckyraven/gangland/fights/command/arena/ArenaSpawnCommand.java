package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;

/** {@code /glw arena spawn <a|b>}: adds a spawn for that side where you stand; repeat for more. */
public final class ArenaSpawnCommand extends SubArgument {

	private final ArenaDeps deps;

	public ArenaSpawnCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, ArenaDeps deps) {
		super(plugin, "spawn", tree, parent);
		this.deps = deps;
		this.addSubArgument(deps.operand(plugin, tree, "side", List.of("a", "b"), this::add));
	}

	private void add(CommandSender sender, String side) {
		Player player = deps.player(sender);
		if (player == null) {
			return;
		}
		boolean sideA = side.equalsIgnoreCase("a");
		if (!sideA && !side.equalsIgnoreCase("b")) {
			sender.sendMessage(deps.text().text("Arenas.Spawn_Side_Invalid"));
			return;
		}
		var draft = deps.drafts().of(player.getUniqueId());
		if (draft.isEmpty()) {
			sender.sendMessage(deps.text().text("Arenas.No_Draft"));
			return;
		}
		draft.get().addSpawn(sideA, ArenaDeps.point(player.getLocation()));
		sender.sendMessage(deps.text().text("Arenas.Spawn_Added", "side", sideA ? "A" : "B", "count",
		                                    draft.get().spawnCount(sideA)));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> ArenaDeps.usage(sender, "/glw arena spawn <a|b>");
	}
}
