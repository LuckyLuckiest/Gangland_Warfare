package org.luckyraven.gangland.gang.command.option;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.command.extension.CommandContribution;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;
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
	private final GangStaffVerbs      staff;

	public GangOptionContribution(JavaPlugin plugin, UserManager<Player> userManager, MemberManager memberManager,
	                              GangManager gangManager, RankManager rankManager, GangStaffVerbs staff) {
		this.staff         = staff;
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
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<rank>"));
		}, sender -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return null;
			}
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

		// staff verbs: every one runs from the console (GangStaffVerbs holds the bodies)
		gang.addSubArgument(staffVerb(tree, "disband", "gang", "confirm"));
		gang.addSubArgument(staffVerb(tree, "leader", "gang", "player"));
		gang.addSubArgument(staffVerb(tree, "bank", "gang", "balance|give|take|reset", "amount"));
		gang.addSubArgument(staffVerb(tree, "safehouse", "gang", "list|tp|remove", "name"));
		gang.addSubArgument(staffVerb(tree, "stats", "reset", "player"));
		gang.addSubArgument(staffVerb(tree, "spy"));
		gang.addSubArgument(staffVerb(tree, "purge", "preview|run"));

		return gang;
	}

	/**
	 * A verb with a chain of free-text positions. The verb and every position run the same body, which reports a
	 * missing argument itself, so the usage line is the same wherever the line stops.
	 */
	private Argument staffVerb(Tree<Argument> tree, String verb, String... positions) {
		Argument root = new Argument(plugin, verb, tree, (argument, sender, args) -> staff.handle(verb, sender, args));

		Argument current = root;
		for (String position : positions) {
			OptionalArgument next = new OptionalArgument(plugin, tree,
			                                             (argument, sender, args) -> staff.handle(verb, sender, args),
			                                             sender -> staff.complete(position));
			next.setDisplayName(position);
			current.addSubArgument(next);
			current = next;
		}

		return root;
	}

	private @NotNull Argument getRankType(Tree<Argument> tree) {
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}
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
			boolean force     = player.hasPermission(GangPermissions.FORCE_RANK);
			boolean self      = targetMember.getUuid().equals(player.getUniqueId());

			RankAssignmentPolicy.Decision decision =
					RankAssignmentPolicy.evaluate(rankManager.getRankTree(), userMember.getRank(),
					                              targetMember.getRank(), nextRank, force, self,
					                              rankManager.get(GangSettings.getGangRankTail()));

			if (decision != RankAssignmentPolicy.Decision.ALLOWED) {
				user.sendMessage(RankAssignmentPolicy.message(decision));
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
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return null;
			}
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return null;
			}

			Collection<Rank> values = rankManager.getRanks().values();

			// GR-08: the completer offered every rank in the tree, owner included.
			boolean force     = player.hasPermission(GangPermissions.FORCE_RANK);

			if (force) return values.stream().map(Rank::getName).toList();

			Member  actor     = memberManager.getMember(player.getUniqueId());
			Rank    actorRank = actor == null ? null : actor.getRank();

			return values.stream()
					.filter(rank -> RankAssignmentPolicy.assignable(rankManager.getRankTree(), actorRank, rank))
					.map(Rank::getName)
					.toList();
		});
	}

}
