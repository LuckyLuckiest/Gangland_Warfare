package org.luckyraven.gangland.copsncrooks.command.cops;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.item.CopsItems;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw cop item <police_scanner|scanner_map> [player]} (permission {@code gangland.cop.item}): gives a cops item
 * to the named online player, or to the sender; the console must name a player. Chained {@code OptionalArgument}s with
 * tab completion for the item id and the player.
 *
 * @since 0.20.0
 */
class CopItemCommand extends SubArgument {

	/** The permission the command, and {@link #give}, require. */
	static final String PERMISSION = "gangland.cop.item";

	CopItemCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, CopsItems items,
	               CommandMessages messages) {
		super(plugin, "item", tree, parent);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			throw new UnsupportedOperationException("TDD stub");
		};
	}

	/**
	 * The command's work: checks the permission, resolves the target ({@code playerName}, else the sender), gives the
	 * item (leftovers that do not fit drop at the target's feet) and answers the sender.
	 */
	void give(CommandSender sender, @Nullable String itemId, @Nullable String playerName) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
