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
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.Arrays;
import java.util.List;

/** {@code /glw gang notice <text|clear>}: the notice members see once when they log in. */
final class GangNoticeCommand extends SubArgument {

	/** Longest notice, in visible characters. */
	static final int MAX_LENGTH = 200;

	/** Longest stored notice, raw characters with colour codes. Matches the VARCHAR(255) column of GangTable.notice. */
	static final int MAX_STORED_LENGTH = 255;

	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final GangMessages        messages;

	GangNoticeCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                  MemberManager memberManager, GangManager gangManager, GangMessages messages) {
		super(gangland, "notice", tree, parent);

		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.messages      = messages;

		OptionalArgument text = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			run(sender, String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
		}, sender -> List.of("clear", "<text>"));
		text.setDisplayName("text|clear");
		// greedy, so every word after "notice" reaches the action
		text.setGreedy(true);
		this.addSubArgument(text);
	}

	/** Sets the notice ({@code clear} removes it). False when the text is longer than {@link #MAX_LENGTH}. */
	static boolean update(Gang gang, String text) {
		if ("clear".equalsIgnoreCase(text.trim())) {
			gang.setNotice(null);
			return true;
		}

		String stored = text.trim();
		if (GangNameRules.plain(stored).length() > MAX_LENGTH || stored.length() > MAX_STORED_LENGTH) return false;

		gang.setNotice(stored);
		return true;
	}

	private void run(CommandSender sender, String text) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
			return;
		}

		User<Player> user = userManager.getUser(player);
		if (user == null) return;

		if (!user.hasGang()) {
			user.sendMessage(Messages.MUST_CREATE_GANG.toString());
			return;
		}

		if (!GangPermissions.allows(memberManager.getMember(player.getUniqueId()), player, GangPermissions.NOTICE)) {
			user.sendMessage(GangPermissions.refusal(GangPermissions.NOTICE));
			return;
		}

		Gang gang = gangManager.getGang(user.getGangId());
		if (gang == null) return;

		if (!update(gang, text)) {
			user.sendMessage(messages.text("Notice.Too_Long", "&cA notice is at most %max% characters.", "max",
			                               MAX_LENGTH));
			return;
		}

		user.sendMessage(gang.getNotice() == null ?
		                 messages.text("Notice.Cleared", "&7The gang notice was removed.") :
		                 messages.text("Notice.Set", "&aThe gang notice is set. Members see it when they log in."));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<text|clear>"));
	}
}
