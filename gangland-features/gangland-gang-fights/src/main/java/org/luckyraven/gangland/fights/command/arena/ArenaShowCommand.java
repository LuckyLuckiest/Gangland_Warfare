package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.fights.arena.Arena;
import org.luckyraven.gangland.fights.arena.ArenaBox;
import org.luckyraven.gangland.fights.arena.ArenaOutline;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.Optional;

/** {@code /glw arena show [name]}: particle outline of an arena, or of your draft when no name is given. */
public final class ArenaShowCommand extends SubArgument {

	private final JavaPlugin plugin;
	private final ArenaDeps  deps;

	public ArenaShowCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, ArenaDeps deps) {
		super(plugin, "show", tree, parent);
		this.plugin = plugin;
		this.deps   = deps;
		this.addSubArgument(deps.operand(plugin, tree, "name", null,
		                                 (sender, name) -> outline(sender, deps.registry()
				                                 .byName(name)
				                                 .map(Arena::box))));
	}

	private void outline(CommandSender sender, Optional<ArenaBox> box) {
		Player player = deps.player(sender);
		if (player == null) {
			return;
		}
		if (box.isEmpty()) {
			sender.sendMessage(deps.text().text("Arenas.Show_Nothing"));
			return;
		}
		int seconds = deps.rules().getOutlineSeconds();
		ArenaOutline.show(plugin, player, box.get(), seconds);
		sender.sendMessage(deps.text().text("Arenas.Show_Started", "seconds", seconds));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Optional<ArenaBox> draft = sender instanceof Player player
			                           ? deps.drafts().of(player.getUniqueId()).flatMap(d -> d.box())
			                           : Optional.empty();
			outline(sender, draft);
		};
	}
}
