package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@code /glw gang transfer <player>} → confirm via {@code /glw gang transfer confirm} → swaps the gang's owner rank
 * onto the target player and demotes the original owner to the rank directly below owner. Mirrors the
 * {@link GangDeleteCommand} confirm flow so a misclick can't accidentally hand the gang away.
 */
class GangTransferCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	private final HashMap<User<Player>, AtomicReference<UUID>> pendingTargets = new HashMap<>();
	private final ConfirmArgument                              confirmTransfer;

	protected GangTransferCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                              RankManager rankManager) {
		super(gangland, "transfer", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.rankManager   = rankManager;

		// Register `confirm` BEFORE the `<player>` OptionalArgument so the parser binds `/glw gang transfer confirm`
		// to the confirm branch instead of treating "confirm" as a target player name.
		this.confirmTransfer = transferConfirm();
		this.addSubArgument(confirmTransfer);
		this.addSubArgument(transferTargetArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private OptionalArgument transferTargetArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member userMember = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang() || userMember == null) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Rank tail = rankManager.get(Settings.getGangRankTail());
			if (tail == null) return;

			if (userMember.getRank() == null || !userMember.getRank().match(tail.getUsedId())) {
				user.sendMessage(Messages.NOT_OWNER.toString().replace("%tail%", Settings.getGangRankTail()));
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());
			if (gang == null) return;

			String targetStr    = args[2];
			Member targetMember = findGangMemberByName(gang, targetStr);

			if (targetMember == null) {
				user.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			if (targetMember.getUuid().equals(player.getUniqueId())) {
				user.sendMessage(Messages.GANG_CANNOT_ACT_SELF.toString());
				return;
			}

			// Demote target slot exists — owner has a parent rank — verified up-front so confirm can't strand the
			// gang without an owner if the rank tree was edited mid-flow.
			if (tail.getNode().getParent() == null) {
				user.sendMessage(Messages.GANG_TRANSFER_NO_PARENT.toString());
				return;
			}

			pendingTargets.put(user, new AtomicReference<>(targetMember.getUuid()));

			user.sendMessage(Messages.GANG_TRANSFER_REQUEST.toString().replace("%player%", targetStr));
			user.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"gang", "transfer"}));

			confirmTransfer.lock(sender);
		}, this::tabCompleteGangMembers);
	}

	private ConfirmArgument transferConfirm() {
		return new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			AtomicReference<UUID> pending = pendingTargets.remove(user);
			if (pending == null) return;

			UUID targetUuid = pending.get();
			if (targetUuid == null) return;

			Member userMember = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang() || userMember == null) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Rank tail = rankManager.get(Settings.getGangRankTail());
			if (tail == null) return;

			// Re-verify ownership — the player may have been demoted (or had their rank edited) between the request
			// and the confirm.
			if (userMember.getRank() == null || !userMember.getRank().match(tail.getUsedId())) {
				user.sendMessage(Messages.NOT_OWNER.toString().replace("%tail%", Settings.getGangRankTail()));
				return;
			}

			Tree.Node<Rank> tailParentNode = tail.getNode().getParent();
			if (tailParentNode == null) {
				user.sendMessage(Messages.GANG_TRANSFER_NO_PARENT.toString());
				return;
			}

			Rank tailParent = tailParentNode.getData();

			Gang gang = gangManager.getGang(user.getGangId());
			if (gang == null) return;

			Member targetMember = memberManager.getMember(targetUuid);
			if (targetMember == null || targetMember.getGangId() != gang.getId()) {
				user.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", "?"));
				return;
			}

			memberManager.assignRank(userMember, tailParent);
			memberManager.assignRank(targetMember, tail);

			// Refresh runtime permission attachments for online users so the rank swap takes effect immediately.
			if (user.getPermissionAttachment() != null) user.flushPermissions(tailParent);

			OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetUuid);
			String        targetName    = offlineTarget.getName() != null ? offlineTarget.getName() : "?";

			if (offlineTarget.isOnline()) {
				Player       onlineTarget = offlineTarget.getPlayer();
				User<Player> targetUser   = onlineTarget == null ? null : userManager.getUser(onlineTarget);

				if (targetUser != null) targetUser.flushPermissions(tail);

				if (onlineTarget != null) {
					onlineTarget.sendMessage(Messages.GANG_TRANSFER_TARGET_SUCCESS.toString()
					                                                              .replace("%player%",
					                                                                       player.getName()));
				}
			}

			user.sendMessage(Messages.GANG_TRANSFER_PLAYER_SUCCESS.toString().replace("%player%", targetName));
		});
	}

	private Member findGangMemberByName(Gang gang, String name) {
		for (Member member : gang.getMembers()) {
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
			String        offlineName   = offlinePlayer.getName();
			if (offlineName != null && offlineName.equalsIgnoreCase(name)) return member;
		}
		return null;
	}

	private List<String> tabCompleteGangMembers(CommandSender sender) {
		if (!(sender instanceof Player player)) return null;

		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasGang()) return null;

		Gang gang = gangManager.getGang(user.getGangId());
		if (gang == null) return null;

		List<String> names = new ArrayList<>();
		UUID         self  = player.getUniqueId();

		for (Member member : gang.getMembers()) {
			if (member.getUuid().equals(self)) continue;

			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
			String        offlineName   = offlinePlayer.getName();
			if (offlineName == null) continue;

			names.add(offlineName);
		}

		return names.isEmpty() ? null : names;
	}

}
