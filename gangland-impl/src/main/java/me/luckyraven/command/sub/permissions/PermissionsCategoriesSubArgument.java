package me.luckyraven.command.sub.permissions;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.permission.PermissionManager;
import org.bukkit.command.CommandSender;

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
