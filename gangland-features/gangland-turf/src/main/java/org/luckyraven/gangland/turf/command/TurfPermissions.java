package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.turf.config.TurfMessages;

/**
 * The one turf admin node (TF-30). {@code gangland.turf.admin} is the parent of every turf admin command in plugin.yml,
 * so granting it grants them all; the sub-command nodes never need to be granted one by one.
 */
public final class TurfPermissions {

	public static final String ADMIN = "gangland.turf.admin";

	private TurfPermissions() {
	}

	public static boolean isAdmin(CommandSender sender) {
		return sender.hasPermission(ADMIN);
	}

	/** true when allowed; otherwise sends Turf.Admin_Only and returns false. */
	public static boolean requireAdmin(CommandSender sender, TurfMessages text) {
		if (isAdmin(sender)) {
			return true;
		}
		sender.sendMessage(text.text("Turf.Admin_Only", "&cOnly turf admins can do that."));
		return false;
	}
}
