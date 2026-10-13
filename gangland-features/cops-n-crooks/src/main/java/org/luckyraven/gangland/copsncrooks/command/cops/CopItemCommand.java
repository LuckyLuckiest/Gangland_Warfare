package org.luckyraven.gangland.copsncrooks.command.cops;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.item.CopsItems;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.Map;

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

	private final JavaPlugin       plugin;
	private final Tree<Argument>   tree;
	private final CopsItems        items;
	private final CommandMessages  messages;

	CopItemCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, CopsItems items,
	               CommandMessages messages) {
		super(plugin, "item", tree, parent);
		setPermission(PERMISSION);

		this.plugin   = plugin;
		this.tree     = tree;
		this.items    = items;
		this.messages = messages;

		itemArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> give(sender,
		                                        args.length > 2 ? args[2] : null,
		                                        args.length > 3 ? args[3] : null);
	}

	/**
	 * The command's work: checks the permission, resolves the target ({@code playerName}, else the sender), gives the
	 * item (leftovers that do not fit drop at the target's feet) and answers the sender.
	 */
	void give(CommandSender sender, @Nullable String itemId, @Nullable String playerName) {
		if (!sender.hasPermission(PERMISSION)) {
			sender.sendMessage(messages.format(CommandMessages.Key.ITEM_NO_PERMISSION, Map.of()));
			return;
		}

		Player target;
		if (playerName == null) {
			if (!(sender instanceof Player self)) {
				sender.sendMessage(messages.format(CommandMessages.Key.ITEM_NEEDS_PLAYER, Map.of()));
				return;
			}
			target = self;
		} else {
			target = Bukkit.getPlayerExact(playerName);
			if (target == null || !target.isOnline()) {
				sender.sendMessage(messages.format(CommandMessages.Key.PLAYER_NOT_FOUND,
				                                   Map.of("player", playerName)));
				return;
			}
		}

		ItemStack stack = itemId == null ? null : items.create(itemId);
		if (stack == null) {
			sender.sendMessage(messages.format(CommandMessages.Key.ITEM_UNKNOWN,
			                                   Map.of("item", itemId == null ? "" : itemId,
			                                          "items", String.join(", ", CopsItems.ids()))));
			return;
		}

		for (ItemStack leftover : target.getInventory().addItem(stack).values()) {
			Location at    = target.getLocation();
			World    world = at.getWorld();
			if (world != null) {
				world.dropItemNaturally(at, leftover);
			}
		}

		sender.sendMessage(messages.format(CommandMessages.Key.ITEM_GIVEN,
		                                   Map.of("item", itemId, "target", target.getName())));
	}

	private void itemArgument() {
		OptionalArgument itemId = new OptionalArgument(plugin, tree, action(), sender -> CopsItems.ids());

		OptionalArgument player = new OptionalArgument(plugin, tree, action(), sender -> Bukkit.getOnlinePlayers()
				.stream()
				.map(Player::getName)
				.toList());

		itemId.addSubArgument(player);
		this.addSubArgument(itemId);
	}
}
