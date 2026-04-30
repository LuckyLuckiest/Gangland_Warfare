package org.luckyraven.gangland.command.sub.permissions;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.data.permission.PermissionManager;

final class PermissionsCategoriesSubArgument extends SubArgument {

	private final PermissionManager permissionManager;

	PermissionsCategoriesSubArgument(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                 PermissionManager permissionManager) {
		super(gangland, "categories", tree, parent);

		this.permissionManager = permissionManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> PermissionsCommand.sendOverview(sender, permissionManager);
	}

}
