package org.luckyraven.gangland.gang.membership;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangMembershipService.Result;
import org.luckyraven.gangland.gang.permission.RankPermissionApplier;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.Map;
import java.util.UUID;

/**
 * Hands a gang to another member: the target takes the tail rank, the old owner drops to the rank directly below it.
 * Body moved unchanged from {@code GangTransferCommand}'s confirm step. {@code staff} skips the caller-is-owner check
 * (the confirm lock lives in the command, so staff never meet it); the current holder of the tail rank is demoted.
 */
public class GangLeadership {

	private final MemberManager       memberManager;
	private final RankManager         rankManager;
	private final UserManager<Player> userManager;

	public GangLeadership(MemberManager memberManager, RankManager rankManager, UserManager<Player> userManager) {
		this.memberManager = memberManager;
		this.rankManager   = rankManager;
		this.userManager   = userManager;
	}

	public Result transfer(Gang gang, @Nullable UUID actor, UUID target, boolean staff) {
		Rank tail = rankManager.get(GangSettings.getGangRankTail());
		if (tail == null) return Result.fail("Membership.No_Owner_Rank");

		Tree.Node<Rank> tailParentNode = tail.getNode().getParent();
		if (tailParentNode == null) return Result.fail("Membership.No_Parent");

		Rank tailParent = tailParentNode.getData();

		Member targetMember = memberManager.getMember(target);
		if (targetMember == null || targetMember.getGangId() != gang.getId()) {
			return new Result(false, "Membership.Unknown_Player", Map.of("player", "?"));
		}

		Member owner = staff ? holderOf(gang, tail) : actor == null ? null : memberManager.getMember(actor);
		if (!staff && (owner == null || owner.getGangId() != gang.getId() || owner.getRank() == null ||
		               !owner.getRank().match(tail.getUsedId()))) {
			return new Result(false, "Membership.Not_Owner", Map.of("tail", GangSettings.getGangRankTail()));
		}

		if (owner != null && owner.getUuid().equals(target)) return Result.fail("Membership.Self");

		if (owner != null) {
			memberManager.assignRank(owner, tailParent);
			flush(owner.getUuid(), tailParent);
		}
		memberManager.assignRank(targetMember, tail);

		// Refresh runtime permission attachments for online users so the rank swap takes effect immediately.
		Player onlineTarget = Bukkit.getPlayer(target);
		if (onlineTarget != null) {
			flush(target, tail);

			String by = actor != null && Bukkit.getPlayer(actor) != null ? Bukkit.getPlayer(actor).getName() : "staff";
			onlineTarget.sendMessage(Messages.GANG_TRANSFER_TARGET_SUCCESS.toString().replace("%player%", by));
		}

		return Result.success();
	}

	private void flush(UUID uuid, Rank rank) {
		Player online = Bukkit.getPlayer(uuid);
		User<Player> user = online == null ? null : userManager.getUser(online);

		if (user != null && user.getPermissionAttachment() != null) RankPermissionApplier.flush(user, rank);
	}

	private static Member holderOf(Gang gang, Rank tail) {
		for (Member member : gang.getMembers()) {
			if (member.getRank() != null && member.getRank().match(tail.getUsedId())) return member;
		}
		return null;
	}

}
