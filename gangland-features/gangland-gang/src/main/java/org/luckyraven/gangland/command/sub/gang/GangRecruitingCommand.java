package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;

/**
 * {@code /glw gang recruiting [open|invite]}: sets whether anyone may {@code /glw gang join} the gang. Only the leader
 * or a rank with the invite node may change it; with no argument it shows the current state.
 */
final class GangRecruitingCommand extends SubArgument {

	private final JavaPlugin          gangland;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final GangMessages        messages;

	GangRecruitingCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                      @Qualifier("online") UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                      GangMessages messages) {
		super(gangland, "recruiting", tree, parent);
		this.gangland      = gangland;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.messages      = messages;

		this.addSubArgument(stateArgument(gangland, tree));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Gang gang = senderGang(sender);
			if (gang == null) {
				return;
			}

			sender.sendMessage(messages.text("Social.Recruiting_Now",
			                                 "&7%gang% is %state%. &7Use /glw gang recruiting open|invite to change it.",
			                                 "gang", gang.getDisplayNameString(), "state", stateWord(gang)));
		};
	}

	private Argument stateArgument(JavaPlugin gangland, Tree<Argument> tree) {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> change(sender, args[2]),
		                            sender -> List.of("open", "invite"));
	}

	private void change(CommandSender sender, String value) {
		Gang.State state;
		if ("open".equalsIgnoreCase(value)) {
			state = Gang.State.OPEN;
		} else if ("invite".equalsIgnoreCase(value)) {
			state = Gang.State.INVITE;
		} else {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<open|invite>"));
			return;
		}

		Gang gang = senderGang(sender);
		if (gang == null) {
			return;
		}

		Player player = (Player) sender;
		if (!GangPermissions.allows(memberManager.getMember(player.getUniqueId()), player,
		                            GangPermissions.RECRUITING)) {
			sender.sendMessage(GangPermissions.refusal(GangPermissions.RECRUITING));
			return;
		}

		gang.setState(state);
		String line = state == Gang.State.OPEN ?
		              messages.text("Social.Recruiting_Open", "&a%gang% is open: anyone can /glw gang join it.",
		                            "gang", gang.getDisplayNameString()) :
		              messages.text("Social.Recruiting_Invite", "&7%gang% is invite only.",
		                            "gang", gang.getDisplayNameString());
		for (User<Player> member : gang.getOnlineMembers(userManager::getUser)) {
			member.sendMessage(line);
		}
	}

	private static String stateWord(Gang gang) {
		return gang.getState() == Gang.State.OPEN ? "open" : "invite only";
	}

	/** The sender's gang, or {@code null} after telling a sender with no gang (or the console) so. */
	private Gang senderGang(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
			return null;
		}

		User<Player> user = userManager.getUser(player);
		Gang gang = user == null || !user.hasGang() ? null : gangManager.getGang(user.getGangId());
		if (gang == null) {
			sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
		}
		return gang;
	}

}
