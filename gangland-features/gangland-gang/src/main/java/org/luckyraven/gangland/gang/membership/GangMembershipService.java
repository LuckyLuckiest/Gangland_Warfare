package org.luckyraven.gangland.gang.membership;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.events.gang.GangJoinEvent;
import org.luckyraven.gangland.events.gangs.GangCreatedEvent;
import org.luckyraven.gangland.events.gangs.GangMemberJoinedEvent;
import org.luckyraven.gangland.events.gangs.GangMemberLeftEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * The one path into and out of a gang: join (create, invite accept, {@code /glw gang join}, staff), leave, kick,
 * disband and leader change. It applies the member cap, refuses a leave while combat-tagged, runs the
 * {@code Commands.On_*} console lines and fires the api events exactly once per change.
 *
 * <p>Nothing else may call {@link Gang#addMember} or {@link Gang#removeMember} (the member repository load excepted).
 */
@CustomLog
public class GangMembershipService {

	public enum JoinSource {
		CREATE,
		INVITE,
		JOIN,
		STAFF
	}

	public record Result(boolean ok, String messageKey, Map<String, String> placeholders) {

		/** The success result (named {@code success} because a record accessor already is {@code ok()}). */
		public static Result success() {
			return new Result(true, "", Map.of());
		}

		public static Result fail(String messageKey) {
			return new Result(false, messageKey, Map.of());
		}
	}

	private static final Pattern ASCII_NAME = Pattern.compile("[A-Za-z0-9_]+");

	/** Texts that already live in the core {@link Messages}; their wording is kept. */
	private static final Map<String, Supplier<String>> LEGACY = Map.of(
			"Membership.Self", Messages.GANG_CANNOT_ACT_SELF::toString,
			"Membership.Higher_Rank", Messages.GANG_HIGHER_RANK_ACTION::toString,
			"Membership.Invalid_Rank", Messages.INVALID_RANK::toString,
			"Membership.No_Parent", Messages.GANG_TRANSFER_NO_PARENT::toString,
			"Membership.Owner_Leave", Messages.GANG_TRANSFER_OWNERSHIP::toString,
			"Membership.Not_Owner", Messages.NOT_OWNER::toString,
			"Membership.Unknown_Player", Messages.PLAYER_NOT_FOUND::toString);

	private static final Map<String, String> DEFAULTS = Map.ofEntries(
			Map.entry("Membership.Full", "&c%gang% is full (%count%/%max%)."),
			Map.entry("Membership.Tagged", "&cYou are in a fight: wait %seconds% s."),
			Map.entry("Membership.Kick_Denied", "&cYour rank may not kick members."),
			Map.entry("Membership.Already_In_Gang", "&cLeave your gang first."),
			Map.entry("Membership.Unknown_Gang", "&cThat gang does not exist."),
			Map.entry("Membership.Not_In_Gang", "&cYou are not in a gang."),
			Map.entry("Membership.No_Rank", "&cThis gang has no starting rank; ask an admin."),
			Map.entry("Membership.No_Owner_Rank", "&cThe owner rank is missing. Tell an admin."));

	private final GangManager                gangManager;
	private final MemberManager              memberManager;
	private final RankManager                rankManager;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final GangLimits                 limits;
	private final CombatTag                  combatTag;
	private final GangRules                  rules;
	private final GangMessages               messages;
	private final GangDisbandService         disbandService;
	private final GangLeadership             leadership;
	private final Consumer<String>           console;

	public GangMembershipService(GangManager gangManager, MemberManager memberManager, RankManager rankManager,
	                             UserManager<Player> userManager, UserManager<OfflinePlayer> offlineUserManager,
	                             GangLimits limits, CombatTag combatTag, GangRules rules, GangMessages messages,
	                             GangDisbandService disbandService, GangLeadership leadership,
	                             Consumer<String> console) {
		this.gangManager        = gangManager;
		this.memberManager      = memberManager;
		this.rankManager        = rankManager;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.limits             = limits;
		this.combatTag          = combatTag;
		this.rules              = rules;
		this.messages           = messages;
		this.disbandService     = disbandService;
		this.leadership         = leadership;
		this.console            = console;
	}

	/** The player-facing line for a failed {@link Result}. */
	public String text(Result result) {
		String key = result.messageKey();

		Supplier<String> legacy = LEGACY.get(key);
		if (legacy != null) {
			String line = legacy.get();
			for (Map.Entry<String, String> entry : result.placeholders().entrySet()) {
				line = line.replace("%" + entry.getKey() + "%", entry.getValue());
			}
			return line;
		}

		Object[] pairs = new Object[result.placeholders().size() * 2];
		int      index = 0;
		for (Map.Entry<String, String> entry : result.placeholders().entrySet()) {
			pairs[index++] = entry.getKey();
			pairs[index++] = entry.getValue();
		}
		return messages.text(key, DEFAULTS.getOrDefault(key, "&c" + key), pairs);
	}

	public Result join(int gangId, UUID player, JoinSource source) {
		Gang gang = gangManager.getGang(gangId);
		if (gang == null) return Result.fail("Membership.Unknown_Gang");

		Member member = memberManager.getMember(player);
		if (member == null) return new Result(false, "Membership.Unknown_Player", Map.of("player", "?"));

		User<? extends OfflinePlayer> user = userOf(player);
		if (user == null || user.hasGang()) return Result.fail("Membership.Already_In_Gang");

		if (source != JoinSource.CREATE) {
			int max = limits.maxMembers(gangId);
			int now = gang.getMembers().size();
			if (max > 0 && now >= max) {
				return new Result(false, "Membership.Full", Map.of("gang", gang.getDisplayNameString(),
				                                                     "count", String.valueOf(now),
				                                                     "max", String.valueOf(max)));
			}
		}

		Rank rank = rankManager.get(source == JoinSource.CREATE ? GangSettings.getGangRankTail() :
		                            GangSettings.getGangRankHead());
		if (rank == null) {
			log.warn("Gang " + gang.getName() + " cannot be joined: no rank named " +
			         (source == JoinSource.CREATE ? GangSettings.getGangRankTail() : GangSettings.getGangRankHead()));
			return Result.fail("Membership.No_Rank");
		}

		String name = nameOf(player);
		if (source != JoinSource.CREATE) {
			String joined = Messages.GANG_PLAYER_JOINED.toString().replace("%player%", name);
			for (User<Player> onlineMember : gang.getOnlineMembers(userManager::getUser)) {
				onlineMember.sendMessage(joined);
			}
		}

		member.setGangJoinDateLong(Instant.now().toEpochMilli());
		gang.addMember(user, member, rank);

		if (source == JoinSource.CREATE) {
			Bukkit.getPluginManager().callEvent(new GangCreatedEvent(gangId, gang.getName(), player));
		}
		Bukkit.getPluginManager().callEvent(new GangMemberJoinedEvent(gangId, player));
		if (source == JoinSource.JOIN) {
			// T-265: mail drops the player's other invites
			Player online = Bukkit.getPlayer(player);
			if (online != null) Bukkit.getPluginManager().callEvent(new GangJoinEvent(gang, online));
		}
		run(rules.getJoinCommands(), gang, name);

		return Result.success();
	}

	/** {@code LEFT} is the player's own choice; {@code PURGED} removes an inactive member. */
	public Result leave(UUID player, GangMemberLeftEvent.Reason reason) {
		Member member = memberManager.getMember(player);
		if (member == null || member.getGangId() < 0) return Result.fail("Membership.Not_In_Gang");

		Gang gang = gangManager.getGang(member.getGangId());
		if (gang == null) return Result.fail("Membership.Not_In_Gang");

		if (reason == GangMemberLeftEvent.Reason.LEFT) {
			if (isOwner(member)) return Result.fail("Membership.Owner_Leave");

			if (combatTag.isTagged(player)) {
				long seconds = Math.max(1, (combatTag.remaining(player).toMillis() + 999) / 1000);
				return new Result(false, "Membership.Tagged", Map.of("seconds", String.valueOf(seconds)));
			}
		}

		User<? extends OfflinePlayer> user = userOf(player);
		if (user == null) return Result.fail("Membership.Not_In_Gang");

		String name = nameOf(player);
		gang.removeMember(user, member);
		Bukkit.getPluginManager().callEvent(new GangMemberLeftEvent(gang.getId(), player, reason));

		if (reason == GangMemberLeftEvent.Reason.LEFT) {
			// removeMember already cleared the leaver's gang link, so the broadcast naturally excludes them.
			String broadcast = Messages.GANG_PLAYER_LEFT.toString().replace("%player%", name);
			for (User<Player> onUser : gang.getOnlineMembers(userManager::getUser)) {
				onUser.sendMessage(broadcast);
			}
		}
		run(rules.getLeaveCommands(), gang, name);

		return Result.success();
	}

	public Result kick(int gangId, UUID actor, UUID target) {
		Gang gang = gangManager.getGang(gangId);
		if (gang == null) return Result.fail("Membership.Unknown_Gang");

		Member actorMember  = memberManager.getMember(actor);
		Member targetMember = memberManager.getMember(target);
		if (actorMember == null || actorMember.getGangId() != gangId) return Result.fail("Membership.Not_In_Gang");
		if (targetMember == null || targetMember.getGangId() != gangId) {
			return new Result(false, "Membership.Unknown_Player", Map.of("player", nameOf(target)));
		}

		// Self-action is a domain rule, never a permission decision - applied before the rank gates.
		if (actor.equals(target)) return Result.fail("Membership.Self");

		if (!GangPermissions.allows(actorMember, Bukkit.getPlayer(actor), GangPermissions.KICK)) {
			return Result.fail("Membership.Kick_Denied");
		}

		if (actorMember.getRank() == null || targetMember.getRank() == null) {
			return Result.fail("Membership.Invalid_Rank");
		}

		if (!rankManager.getRankTree().isDescendant(targetMember.getRank().getNode(), actorMember.getRank().getNode())) {
			return Result.fail("Membership.Higher_Rank");
		}

		User<? extends OfflinePlayer> targetUser = userOf(target);
		if (targetUser == null) return Result.fail("Membership.Not_In_Gang");

		if (targetUser.getUser() instanceof Player targetPlayer) {
			targetPlayer.sendMessage(Messages.KICKED_FROM_GANG.toString());
		}

		String name = nameOf(target);
		gang.removeMember(targetUser, targetMember);
		Bukkit.getPluginManager().callEvent(new GangMemberLeftEvent(gangId, target, GangMemberLeftEvent.Reason.KICKED));
		run(rules.getKickCommands(), gang, name);

		return Result.success();
	}

	/**
	 * @param staff skips the caller-is-owner check and gives no fee refund; the owner who disbands gets a quarter of
	 *              the creation fee back
	 */
	public Result disband(int gangId, @Nullable UUID actor, boolean staff) {
		Gang gang = gangManager.getGang(gangId);
		if (gang == null) return Result.fail("Membership.Unknown_Gang");

		if (!staff) {
			Member member = actor == null ? null : memberManager.getMember(actor);
			if (member == null || member.getGangId() != gangId || !isOwner(member)) {
				return new Result(false, "Membership.Not_Owner", Map.of("tail", GangSettings.getGangRankTail()));
			}
		}

		return disbandService.disband(gang, actor, staff, !staff);
	}

	public Result setLeader(int gangId, @Nullable UUID actor, UUID target, boolean staff) {
		Gang gang = gangManager.getGang(gangId);
		if (gang == null) return Result.fail("Membership.Unknown_Gang");

		return leadership.transfer(gang, actor, target, staff);
	}

	private boolean isOwner(Member member) {
		Rank tail = rankManager.get(GangSettings.getGangRankTail());
		return tail != null && member.getRank() != null && member.getRank().match(tail.getUsedId());
	}

	/** An online player's account, or the cached offline one (created when this session never loaded it). */
	@Nullable
	private User<? extends OfflinePlayer> userOf(UUID uuid) {
		Player online = Bukkit.getPlayer(uuid);
		if (online != null) return userManager.getUser(online);

		OfflinePlayer       offline = Bukkit.getOfflinePlayer(uuid);
		User<OfflinePlayer> user    = offlineUserManager.getUser(offline);
		if (user == null) {
			// Fallback only: the row failed to load, so this User holds defaults. Load-pending keeps autosave
			// and shutdown from writing them over the stored row.
			user = offlineUserManager.create(offline);
			if (!offlineUserManager.loadStored(user)) user.setLoadPending(true);
			offlineUserManager.add(user);
		}
		return user;
	}

	private static String nameOf(UUID uuid) {
		OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
		String        name   = player == null ? null : player.getName();
		return name == null ? uuid.toString() : name;
	}

	/**
	 * Runs each console line once. {@code %gang%} is the gang name only when it is plain ASCII word characters, else
	 * the id; the display name is never spliced into a console line.
	 */
	private void run(List<String> lines, Gang gang, String playerName) {
		String gangToken = gang.getName() != null && ASCII_NAME.matcher(gang.getName()).matches() ?
		                   gang.getName() : String.valueOf(gang.getId());

		for (String line : lines) {
			console.accept(line.replace("%gang_id%", String.valueOf(gang.getId()))
			                   .replace("%gang%", gangToken)
			                   .replace("%player%", playerName));
		}
	}

}
