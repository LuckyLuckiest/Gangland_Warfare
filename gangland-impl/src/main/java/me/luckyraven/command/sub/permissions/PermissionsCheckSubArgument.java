package me.luckyraven.command.sub.permissions;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

import java.util.List;

final class PermissionsCheckSubArgument extends SubArgument {

	private final Gangland          gangland;
	private final Tree<Argument>    tree;
	private final PermissionManager permissionManager;

	PermissionsCheckSubArgument(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            PermissionManager permissionManager) {
		super(gangland, "check", tree, parent);

		this.gangland          = gangland;
		this.tree              = tree;
		this.permissionManager = permissionManager;

		registerArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<permission>"));
	}

	private void registerArguments() {
		Argument permArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> check(sender, args[2]),
		                                        sender -> List.of("<permission>"));

		this.addSubArgument(permArg);
	}

	private void check(CommandSender sender, String raw) {
		String  resolved         = permissionManager.resolve(raw);
		boolean trackedLocally   = permissionManager.contains(resolved);
		boolean registeredBukkit = permissionManager.getPermissionHandler().permissionExists(resolved);

		if (!trackedLocally && !registeredBukkit) {
			sender.sendMessage(Messages.PERMISSION_NOT_FOUND.toString().replace("%permission%", resolved));
			return;
		}

		if (trackedLocally && registeredBukkit) {
			sender.sendMessage(Messages.PERMISSION_REGISTERED.toString().replace("%permission%", resolved));
			return;
		}

		sender.sendMessage(Messages.PERMISSION_DRIFT.toString()
		                                            .replace("%permission%", resolved)
		                                            .replace("%bukkit%", String.valueOf(registeredBukkit))
		                                            .replace("%tracked%", String.valueOf(trackedLocally)));
	}

}
