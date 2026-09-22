package org.luckyraven.gangland.gang.command.option;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankAssignmentPolicy;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Attaches {@code /glw option gang rank <target> <rank>} under the core's {@code option} command
 * ({@code ComponentExecutorCommand}) — moved out of gangland-impl wholesale (WS5 G2 step 14): every branch this
 * class ever had was gang/rank logic, so impl keeps only the unrelated {@code /glw option click resource} branch
 * and this contribution supplies the rest, exactly like {@code BankMenuContribution} does for {@code bank}.
 */
public final class GangOptionContribution implements CommandContribution {

	private final JavaPlugin          plugin;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	public GangOptionContribution(JavaPlugin plugin, UserManager<Player> userManager, MemberManager memberManager,
	                              GangManager gangManager, RankManager rankManager) {
		this.plugin        = plugin;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.rankManager   = rankManager;
	}

	@Override
	public String parent() {
		return "option";
	}

	@Override
	public List<Argument> create(Tree<Argument> tree, Argument parent) {
		return List.of(gangArgument(tree));
	}

	private Argument gangArgument(Tree<Argument> tree) {
		Argument gang = new Argument(plugin, "gang", tree);
		Argument rank = new Argument(plugin, "rank", tree);

		Argument target = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<rank>"));
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return null;
			}

			// get all the members in the gang
			Gang         userGang = gangManager.getGang(user.getGangId());
			List<Member> members  = userGang.getMembers();
			Stream<String> allMembers = members.stream()
					.map(Member::getUuid)
					.map(Bukkit::getOfflinePlayer)
					.map(OfflinePlayer::getName);

			return allMembers.toList();
		});

		rank.addSubArgument(target);

		// glw option gang rank <target> <rank>
		Argument rankType = getRankType(tree);

		target.addSubArgument(rankType);

		gang.addSubArgument(rank);

		return gang;
	}

	private @NotNull Argument getRankType(Tree<Argument> tree) {
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member userMember = memberManager.getMember(player.getUniqueId());

			// GR-01: a player with no cached Member would NPE further down this command.
			if (userMember == null || !user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Gang userGang = gangManager.getGang(user.getGangId());

			String targetStr    = args[3];
			String rankStr      = args[4];
			Member targetMember = null;
			for (Member member : userGang.getMembers()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
				String        offlineName   = offlinePlayer.getName();

				if (offlineName == null || offlineName.isEmpty() || !offlineName.equalsIgnoreCase(targetStr)) continue;

				targetMember = member;
				break;
			}

			if (targetMember == null) {
				user.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			Rank nextRank = rankManager.get(rankStr);

			if (nextRank == null) return;

			// GR-08: this command used to check only "not my own rank", so anyone who could reach it could hand
			// out the owner rank. Same force_rank override GangPromoteCommand honours.
			String  forceRank = String.format("%s.command.gang.force_rank", GanglandApi.FULL_PREFIX);
			boolean force     = player.hasPermission(forceRank);
			boolean self      = targetMember.getUuid().equals(player.getUniqueId());

			RankAssignmentPolicy.Decision decision =
					RankAssignmentPolicy.evaluate(rankManager.getRankTree(), userMember.getRank(),
					                              targetMember.getRank(), nextRank, force, self);

			if (decision != RankAssignmentPolicy.Decision.ALLOWED) {
				user.sendMessage(messageFor(decision));
				return;
			}

			memberManager.assignRank(targetMember, nextRank);

			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
			Player        onlinePlayer  = offlinePlayer.getPlayer();
			User<Player>  onlineUser    = userManager.getUser(onlinePlayer);

			if (onlinePlayer != null && onlineUser != null && offlinePlayer.isOnline()) {
				String string  = Messages.GANG_PROMOTE_TARGET_SUCCESS.toString();
				String replace = string.replace("%rank%", nextRank.getName());
				onlineUser.sendMessage(replace);
			}

			user.sendMessage(Messages.GANG_PROMOTE_PLAYER_SUCCESS.toString()
			                                                     .replace("%player%", targetStr)
			                                                     .replace("%rank%", nextRank.getName()));
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return null;
			}

			Collection<Rank> values = rankManager.getRanks().values();

			// GR-08: the completer offered every rank in the tree, owner included.
			String  forceRank = String.format("%s.command.gang.force_rank", GanglandApi.FULL_PREFIX);
			boolean force     = player.hasPermission(forceRank);

			if (force) return values.stream().map(Rank::getName).toList();

			Member  actor     = memberManager.getMember(player.getUniqueId());
			Rank    actorRank = actor == null ? null : actor.getRank();

			return values.stream()
					.filter(rank -> RankAssignmentPolicy.assignable(rankManager.getRankTree(), actorRank, rank))
					.map(Rank::getName)
					.toList();
		});
	}

	/**
	 * Maps a refused {@link RankAssignmentPolicy.Decision} onto the message the player sees.
	 */
	private static String messageFor(RankAssignmentPolicy.Decision decision) {
		return switch (decision) {
			case SELF -> Messages.GANG_CANNOT_ACT_SELF.toString();
			case SAME_RANK -> Messages.GANG_SAME_RANK_ACTION.toString();
			case TARGET_OUTRANKS_ACTOR, RANK_NOT_BELOW_ACTOR -> Messages.GANG_HIGHER_RANK_ACTION.toString();
			default -> Messages.COMMAND_NO_PERM.toString();
		};
	}

}
