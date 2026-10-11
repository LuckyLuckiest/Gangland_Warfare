package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.gangland.gang.membership.GangMembershipService;
import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.events.gang.GangJoinEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.time.Instant;
import java.util.Objects;

/**
 * {@code /glw gang join <gang>}: a gangless player joins an OPEN gang at its head (lowest) rank, the same way an
 * accepted invite does. Joining fires GangJoinEvent; the mail module cancels the player's other pending invites.
 */
@CustomLog
final class GangJoinCommand extends SubArgument {

	private final JavaPlugin          gangland;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final GangMessages        messages;
	private final RankManager         rankManager;

	private final GangMembershipService membershipService;

	GangJoinCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                @Qualifier("online") UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                RankManager rankManager, GangMessages messages,
								GangMembershipService membershipService) {
		super(gangland, "join", tree, parent);

		this.membershipService = membershipService;
		this.gangland      = gangland;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.messages      = messages;
		this.rankManager   = rankManager;

		this.addSubArgument(nameArgument(gangland, tree));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang>"));
	}

	private Argument nameArgument(JavaPlugin gangland, Tree<Argument> tree) {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> join(sender, args[2]),
		                            sender -> gangManager.getAll()
				                            .stream()
				                            .filter(gang -> gang.getState() == Gang.State.OPEN)
				                            .map(Gang::getName)
				                            .filter(Objects::nonNull)
				                            .toList());
	}

	private void join(CommandSender sender, String key) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.NOT_PLAYER.toString());
			return;
		}

		User<Player> user = userManager.getUser(player);
		if (user == null) {
			return;
		}
		if (user.hasGang()) {
			sender.sendMessage(messages.text("Social.Already_In_Gang", "&cLeave your gang first."));
			return;
		}

		Gang gang = find(key);
		if (gang == null) {
			sender.sendMessage(messages.text("Social.Unknown", "&cNo gang named %gang%.", "gang",
			                                 GangNameRules.plain(key)));
			return;
		}
		if (gang.getState() != Gang.State.OPEN) {
			sender.sendMessage(messages.text("Social.Invite_Only", "&c%gang% is invite only.",
			                                 "gang", gang.getDisplayNameString()));
			return;
		}

		Rank rank = rankManager.get(GangSettings.getGangRankHead());
		if (rank == null) {
			log.warn("Gang " + gang.getName() + " cannot be joined: no head rank named " +
			         GangSettings.getGangRankHead());
			sender.sendMessage(messages.text("Social.No_Rank", "&cThis gang has no starting rank; ask an admin."));
			return;
		}

		Member member = memberManager.getMember(player.getUniqueId());
		if (member == null) {
			return;
		}

		String joined = Messages.GANG_PLAYER_JOINED.toString().replace("%player%", player.getName());
		for (User<Player> onlineMember : gang.getOnlineMembers(userManager::getUser)) {
			onlineMember.sendMessage(joined);
		}

		member.setGangJoinDateLong(Instant.now().toEpochMilli());
		gang.addMember(user, member, rank);
		sender.sendMessage(messages.text("Social.Joined", "&aYou joined %gang%.",
		                                 "gang", gang.getDisplayNameString()));
		// T-265: mail drops his other invites
		Bukkit.getPluginManager().callEvent(new GangJoinEvent(gang, player));
	}

	/** A gang whose plain name matches {@code key} ignoring case; names only, never ids. */
	private Gang find(String key) {
		String plain = GangNameRules.plain(key);
		for (Gang gang : gangManager.getAll()) {
			if (gang.getName() != null && GangNameRules.plain(gang.getName()).equalsIgnoreCase(plain)) {
				return gang;
			}
		}
		return null;
	}

}
