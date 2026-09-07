package org.luckyraven.gangland.weapon.command;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.weapon.command.wearable.ItemWearableCommand;
import org.luckyraven.gangland.weapon.wearable.WearableAddon;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;

/**
 * Attaches {@code /glw item wearable} under the core's {@code /glw item} argument. Rebuilds exactly the
 * {@code ItemWearableCommand} construction deleted from {@code ItemCommand} when the wearable stack moved into
 * this module.
 */
public final class ItemWearableContribution implements CommandContribution {

	public static final String PARENT = "item";

	private final Gangland            gangland;
	private final UserManager<Player> userManager;
	private final WearableAddon       wearableAddon;

	public ItemWearableContribution(Gangland gangland, UserManager<Player> userManager,
	                                WearableAddon wearableAddon) {
		this.gangland      = gangland;
		this.userManager   = userManager;
		this.wearableAddon = wearableAddon;
	}

	@Override
	public String parent() {
		return PARENT;
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		return List.of(new ItemWearableCommand(gangland, tree, parent, userManager, wearableAddon));
	}
}
