package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;

/**
 * {@code /glw gang pvp <on|off>}: lets the gang's members hurt each other, or stops it. With no argument it shows the
 * current state. Setting it needs the {@code gangland.gang.pvp} node (the owner by default, {@code Ranks.Minimum.
 * Friendly_Fire}).
 */
final class GangPvpCommand extends SubArgument {

	private final GangManager        gangManager;
	private final FriendlyFirePolicy friendlyFire;

	GangPvpCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangManager gangManager,
	               FriendlyFirePolicy friendlyFire) {
		super(gangland, "pvp", tree, parent);

		this.gangManager  = gangManager;
		this.friendlyFire = friendlyFire;

		OptionalArgument state = new OptionalArgument(gangland, tree, (argument, sender, args) -> change(sender, args[2]),
		                                              sender -> List.of("on", "off"));
		state.setDisplayName("on|off");
		this.addSubArgument(state);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Gang gang = senderGang(sender);
			if (gang == null) return;

			sender.sendMessage(friendlyFire.messages().text("Combat.Pvp_Now", "&7Gang pvp is %state% for %gang%.",
			                                                "gang", gang.getDisplayNameString(),
			                                                "state", gang.isFriendlyFire() ? "on" : "off"));
		};
	}

	void change(CommandSender sender, String value) {
		boolean on;
		if ("on".equalsIgnoreCase(value)) {
			on = true;
		} else if ("off".equalsIgnoreCase(value)) {
			on = false;
		} else {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<on|off>"));
			return;
		}

		Gang gang = senderGang(sender);
		if (gang == null) return;

		Player player = (Player) sender;
		Member member = friendlyFire.memberOf(player.getUniqueId());
		if (!GangPermissions.allows(member, player, GangPermissions.PVP)) {
			sender.sendMessage(GangPermissions.refusal(GangPermissions.PVP));
			return;
		}

		gang.setFriendlyFire(on);
		sender.sendMessage(on ?
		                   friendlyFire.messages().text("Combat.Pvp_On",
		                                                "&e%gang% members can now hurt each other.",
		                                                "gang", gang.getDisplayNameString()) :
		                   friendlyFire.messages().text("Combat.Pvp_Off",
		                                                "&a%gang% members can no longer hurt each other.",
		                                                "gang", gang.getDisplayNameString()));
	}

	/** The sender's gang, or {@code null} after telling a sender with no gang (or the console) so. */
	private Gang senderGang(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.NOT_PLAYER.toString());
			return null;
		}

		Member member = friendlyFire.memberOf(player.getUniqueId());
		Gang   gang   = member == null || member.getGangId() == -1 ? null : gangManager.getGang(member.getGangId());
		if (gang == null) {
			sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
		}
		return gang;
	}
}
