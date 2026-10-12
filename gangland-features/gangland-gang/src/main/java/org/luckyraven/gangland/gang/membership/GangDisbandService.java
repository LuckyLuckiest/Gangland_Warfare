package org.luckyraven.gangland.gang.membership;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.events.gang.GangDeleteEvent;
import org.luckyraven.gangland.events.gangs.GangMemberLeftEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.database.repositories.gang.GangAllianceRepository;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangMembershipService.Result;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Disbands a gang: pays the vault out to the members, optionally refunds a quarter of the creation fee, deletes the
 * rows and announces {@link GangDeleteEvent}. Body moved unchanged from {@code GangDeleteCommand}; also used by the
 * staff disband and the purge.
 */
@CustomLog
public class GangDisbandService {

	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final GangManager                gangManager;
	private final RepositoryRegistry         repositoryRegistry;
	private final GangMessages               messages;

	public GangDisbandService(UserManager<Player> userManager, UserManager<OfflinePlayer> offlineUserManager,
	                          MemberManager memberManager, GangManager gangManager,
	                          RepositoryRegistry repositoryRegistry, GangMessages messages) {
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.memberManager      = memberManager;
		this.gangManager        = gangManager;
		this.repositoryRegistry = repositoryRegistry;
		this.messages           = messages;
	}

	/**
	 * @param actor     who disbands (receives the rounding remainder and the refund); {@code null} for the purge,
	 *                  where the owner takes both
	 * @param refundFee give back a quarter of the creation fee
	 */
	public Result disband(Gang gang, @Nullable UUID actor, boolean staff, boolean refundFee) {
		List<Member> members = gang.getMembers();

		UUID payee = actor != null ? actor : ownerOf(members);
		if (payee == null && !members.isEmpty()) payee = members.get(0).getUuid();

		// need to get all the users, even if they are not online,
		// the periodical updates should take care of all the data save
		List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager::getUser);

		Map<UUID, BigDecimal> payouts = members.isEmpty() ? Map.of() :
		                                payouts(gang.getEconomy().getAmount(), members, payee);

		IRepository<Member>                        memberRepository = repositoryRegistry.getRepository(Member.class);
		IRepository<User<? extends OfflinePlayer>> userRepository   = repositoryRegistry.getGenericRepository(
				User.class);

		// The refund goes to the payee's account; found now because removeMember clears the gang link below.
		User<Player> refundUser = null;
		for (User<Player> online : gangOnlineMembers) {
			if (online.getUser().getUniqueId().equals(payee)) refundUser = online;
		}

		Set<UUID> onlineUuids = gangOnlineMembers.stream()
				.map(u -> u.getUser().getUniqueId())
				.collect(Collectors.toSet());

		String depositMoney = Messages.DEPOSIT_MONEY_PLAYER.toString();
		for (User<Player> gangUser : gangOnlineMembers) {
			Player currentPlayer = gangUser.getUser();
			Member mem           = memberManager.getMember(currentPlayer.getUniqueId());

			// An online gang member with no cached Member cannot be paid out or reset - skip rather than NPE
			// halfway through the disband (GR-01).
			if (mem == null) continue;

			BigDecimal amount = payouts.getOrDefault(currentPlayer.getUniqueId(), Currency.ZERO);

			gang.getEconomy().withdrawAmount(amount);
			gangUser.getEconomy().depositAmount(amount);

			gang.removeMember(gangUser, mem);

			// Persist the member reset (gang_id, contribution, rank cleared by removeMember)
			memberRepository.save(mem);
			left(gang, mem);

			String kickedFromGang      = Messages.KICKED_FROM_GANG.toString();
			String gangRemovedReplace  = Messages.GANG_REMOVED.toString().replace("%gang%", gang.getName());
			String depositMoneyReplace = depositMoney.replace("%amount%", Settings.formatAmount(amount));
			gangUser.sendMessage(kickedFromGang, gangRemovedReplace, depositMoneyReplace);
		}

		// Pay out and reset every offline member through the same repository-cache path the online branch uses.
		// Deliberately synchronous on the calling thread (GR-02): this block mutates gang.getEconomy() and the
		// member's rank/gang_id, and the gang row / in-memory gang are torn down below. Each offline User row is
		// saved on the spot: a rejoin evicts the cached offline User before the next autosave, which would
		// otherwise discard the payout.
		for (Member mem : members) {
			if (onlineUuids.contains(mem.getUuid())) continue;

			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(mem.getUuid());

			User<OfflinePlayer> offlineUser = offlineUserManager.getUser(offlinePlayer);
			// Not cached, or cached as a load-pending default (T-300, B3): load just this member's row, the path boot
			// uses. Never loadAll, which rebuilds every player's User (Vault balance writes, wanted events).
			if (offlineUser == null || offlineUser.isLoadPending()) {
				boolean cached = offlineUser != null;
				if (!cached) offlineUser = offlineUserManager.create(offlinePlayer);
				// A failed read leaves defaults: load-pending keeps autosave and the save below off it.
				if (!offlineUserManager.loadStored(offlineUser)) offlineUser.setLoadPending(true);
				if (!cached) offlineUserManager.add(offlineUser);
			}

			BigDecimal amount = payouts.getOrDefault(mem.getUuid(), Currency.ZERO);

			gang.getEconomy().withdrawAmount(amount);
			offlineUser.getEconomy().depositAmount(amount);
			if (offlineUser.isLoadPending()) {
				log.warn("Disband payout of {} for {} was not saved: no stored row could be read",
				         amount, mem.getUuid());
			} else {
				userRepository.save(offlineUser);
			}

			gang.removeMember(offlineUser, mem);

			memberRepository.save(mem);
			left(gang, mem);
		}

		if (refundFee && payee != null) refund(payee, refundUser, userRepository);

		IRepository<Gang>         gangRepository         = repositoryRegistry.getRepository(Gang.class);
		IRepository<GangAlliance> gangAllianceRepository = repositoryRegistry.getRepository(GangAlliance.class);

		// Alliance rows (both directions) go first: gang_ally has foreign keys to gang(id) with no ON DELETE, so
		// MySQL refuses the gang DELETE while they exist (T-703). The gang's bounty is a column of the gang row, so it
		// is destroyed with that row (W2-P1).
		if (gangAllianceRepository instanceof GangAllianceRepository allianceRepo) {
			allianceRepo.deleteAllForGang(gang);
		}
		gangRepository.delete(gang);

		// Drop stale in-memory alliance refs on surviving allied gangs - without this,
		// `ally.isAlly(deletedGang)` / `getAllyListString()` keep returning true until restart.
		for (GangAlliance alliance : gang.getAllies()) {
			Gang allyGang = gangManager.getGang(alliance.ally().getId());
			if (allyGang != null) allyGang.removeAlly(gang);
		}

		gangManager.remove(gang);
		Bukkit.getPluginManager().callEvent(new GangDeleteEvent(gang));

		return Result.success();
	}

	private static void left(Gang gang, Member member) {
		Bukkit.getPluginManager()
		      .callEvent(new GangMemberLeftEvent(gang.getId(), member.getUuid(), GangMemberLeftEvent.Reason.DISBANDED));
	}

	/** A quarter of the creation fee back, to an online payee or, failing that, the cached offline account. */
	private void refund(UUID payee, @Nullable User<Player> online,
	                    IRepository<User<? extends OfflinePlayer>> userRepository) {
		BigDecimal amount = GangSettings.getGangCreateFee()
		                                .divide(BigDecimal.valueOf(4), Currency.SCALE, Currency.ROUNDING_MODE);

		User<? extends OfflinePlayer> target = online;
		if (target == null) {
			User<OfflinePlayer> offlineUser = offlineUserManager.getUser(Bukkit.getOfflinePlayer(payee));
			// A load-pending default is not the stored account: depositing into it and saving would overwrite the row.
			if (offlineUser == null || offlineUser.isLoadPending()) return;

			target = offlineUser;
		}

		target.getEconomy().depositAmount(amount);
		if (online == null) userRepository.save(target);

		target.sendMessage(messages.text("Progression.Fee_Refund",
		                                 "&7Refunded a quarter of the creation fee: &a%money_symbol%%amount%",
		                                 "money_symbol", Settings.getMoneySymbol(),
		                                 "amount", Settings.formatAmount(amount)));
	}

	private static UUID ownerOf(List<Member> members) {
		String tail = GangSettings.getGangRankTail();
		for (Member member : members) {
			if (member.getRank() != null && member.getRank().getName().equalsIgnoreCase(tail)) {
				return member.getUuid();
			}
		}
		return null;
	}

	/**
	 * Splits the whole vault pro rata on each member's contribution, a negative contribution counting as zero. When
	 * the total contribution is zero (D15) every member gets an equal share of the pool, rounded down to the currency
	 * scale. The disbanding owner also takes whatever rounding leaves, so the shares sum to exactly {@code pool}.
	 */
	public static Map<UUID, BigDecimal> payouts(BigDecimal pool, List<Member> members, UUID owner) {
		double                total  = members.stream().mapToDouble(m -> Math.max(0, m.getContribution())).sum();
		BigDecimal            equal  = members.isEmpty() ? Currency.ZERO :
		                               pool.divide(BigDecimal.valueOf(members.size()), Currency.SCALE, RoundingMode.DOWN);
		Map<UUID, BigDecimal> shares = new HashMap<>();
		BigDecimal            left   = pool;

		for (Member member : members) {
			BigDecimal share = total > 0 ?
			                   Currency.multiply(pool, Math.max(0, member.getContribution()) / total).min(left) :
			                   equal;
			shares.put(member.getUuid(), share);
			left = left.subtract(share);
		}

		shares.merge(owner, left, BigDecimal::add);
		return shares;
	}

}
