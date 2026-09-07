package org.luckyraven.gangland.mail.command;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.command.invite.GangInviteCommand;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;

/**
 * Attaches {@code /glw gang invite [cancel] <player>} and its sibling {@code /glw gang accept} under the core's
 * {@code gang} command. Registered as a bean by {@link org.luckyraven.gangland.mail.MailModuleConfig}; without the
 * mail module these sub-arguments simply do not exist.
 */
public final class GangMailContribution implements CommandContribution {

	public static final String PARENT = "gang";

	private final Gangland                   gangland;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final GangManager                gangManager;
	private final RankManager                rankManager;
	private final MailManager                mailManager;

	public GangMailContribution(Gangland gangland, UserManager<Player> userManager,
	                            UserManager<OfflinePlayer> offlineUserManager, MemberManager memberManager,
	                            GangManager gangManager, RankManager rankManager, MailManager mailManager) {
		this.gangland           = gangland;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.memberManager      = memberManager;
		this.gangManager        = gangManager;
		this.rankManager        = rankManager;
		this.mailManager        = mailManager;
	}

	@Override
	public String parent() {
		return PARENT;
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		GangInviteCommand invite = new GangInviteCommand(gangland, tree, parent, userManager, offlineUserManager,
		                                                 memberManager, gangManager, rankManager, mailManager);
		return List.of(invite, invite.gangAccept());
	}
}
