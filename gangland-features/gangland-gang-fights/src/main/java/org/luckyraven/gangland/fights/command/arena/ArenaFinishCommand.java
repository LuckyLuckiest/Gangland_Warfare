package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;
import org.bukkit.Bukkit;

/** {@code /glw arena finish}: checks the draft and saves it to fight/arenas.yml. */
public final class ArenaFinishCommand extends SubArgument {

	private final ArenaDeps deps;

	public ArenaFinishCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, ArenaDeps deps) {
		super(plugin, "finish", tree, parent);
		this.deps = deps;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player player = deps.player(sender);
			if (player == null) {
				return;
			}
			var found = deps.drafts().of(player.getUniqueId());
			if (found.isEmpty()) {
				sender.sendMessage(deps.text().text("Arenas.No_Draft"));
				return;
			}
			var draft   = found.get();
			var verdict = draft.check(deps.rules().getMinArenaVolume(), deps.registry(),
			                          world -> Bukkit.getWorld(world) != null);
			if (!verdict.ok()) {
				sender.sendMessage(deps.text().text(verdict.key(), verdict.pairs()));
				return;
			}
			if (!deps.registry().add(draft.build())) {
				sender.sendMessage(deps.text().text("Arenas.Save_Failed"));
				return;
			}
			deps.drafts().drop(player.getUniqueId());
			sender.sendMessage(deps.text().text("Arenas.Saved", "name", draft.name(), "a", draft.spawnCount(true),
			                                    "b", draft.spawnCount(false)));
		};
	}
}
