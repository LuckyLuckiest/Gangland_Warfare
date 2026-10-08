package org.luckyraven.gangland.mail.command;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailMessages;
import org.luckyraven.gangland.mail.MailRules;
import org.luckyraven.gangland.mail.command.invite.GangInviteCommand;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;

/**
 * Attaches {@code /glw gang invite [cancel] <player>}, its siblings {@code /glw gang accept} and
 * {@code /glw gang decline} under the core's {@code gang} command. Registered as a bean by
 * {@link org.luckyraven.gangland.mail.MailModuleConfig}; without the mail module these sub-arguments simply do not exist.
 */
public final class GangMailContribution implements CommandContribution {

	public static final String PARENT = "gang";

	private final JavaPlugin                 plugin;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final GangManager                gangManager;
	private final RankManager                rankManager;
	private final MailManager                mailManager;
	private final MailRules                  mailRules;
	private final MailMessages               mailMessages;

	public GangMailContribution(JavaPlugin plugin, @Qualifier("online") UserManager<Player> userManager,
	                            @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                            MemberManager memberManager, GangManager gangManager, RankManager rankManager,
	                            MailManager mailManager, MailRules mailRules, MailMessages mailMessages) {
		this.plugin             = plugin;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.memberManager      = memberManager;
		this.gangManager        = gangManager;
		this.rankManager        = rankManager;
		this.mailManager        = mailManager;
		this.mailRules          = mailRules;
		this.mailMessages       = mailMessages;
	}

	@Override
	public String parent() {
		return PARENT;
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		GangInviteCommand invite = new GangInviteCommand(plugin, tree, parent, userManager, offlineUserManager,
		                                                 memberManager, gangManager, rankManager, mailManager,
		                                                 mailRules, mailMessages);
		return List.of(invite, invite.gangAccept(), invite.gangDecline());
	}
}
