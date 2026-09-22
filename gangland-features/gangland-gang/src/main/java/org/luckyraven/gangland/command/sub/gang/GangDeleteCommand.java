package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.timer.CountdownTimer;
import org.luckyraven.keystone.util.TimeUtil;
import org.luckyraven.gangland.gang.database.repositories.gang.GangAllianceRepository;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.util.TimeMessages;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

class GangDeleteCommand extends SubArgument {

	private final JavaPlugin                   gangland;
	private final Tree<Argument>             tree;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final MemberManager              memberManager;
	private final GangManager                gangManager;
	private final RankManager                rankManager;
	private final RepositoryRegistry         repositoryRegistry;

	private final HashMap<User<Player>, AtomicReference<String>> deleteGangName;
	private final HashMap<CommandSender, CountdownTimer>         deleteGangTimer;

	private final ConfirmArgument confirmDelete;

	protected GangDeleteCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                            UserManager<Player> userManager, UserManager<OfflinePlayer> offlineUserManager,
	                            MemberManager memberManager, GangManager gangManager,
	                            RankManager rankManager, RepositoryRegistry repositoryRegistry) {
		super(gangland, new String[]{"delete", "remove", "del"}, tree, parent);

		this.gangland           = gangland;
		this.tree               = tree;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.memberManager      = memberManager;
		this.gangManager        = gangManager;
		this.rankManager        = rankManager;
		this.repositoryRegistry = repositoryRegistry;

		this.deleteGangName  = new HashMap<>();
		this.deleteGangTimer = new HashMap<>();

		this.confirmDelete = gangDeleteConfirm();
		this.addSubArgument(confirmDelete);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member member = memberManager.getMember(player.getUniqueId());

			if (member == null || !user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			// check if the player is the owner
			if (member.getRank() == null) return;

			Rank tail = rankManager.get(Settings.getGangRankTail());

			if (tail == null) return;

			if (!member.getRank().match(tail.getUsedId())) {
				user.sendMessage(Messages.NOT_OWNER.toString().replace("%tail%", Settings.getGangRankTail()));
				return;
			}

			if (confirmDelete.isLocked(sender)) return;

			Gang gang = gangManager.getGang(user.getGangId());
			if (gang == null) {
				user.resetGang();
				member.resetGang();
				member.setContribution(0D);
				memberManager.assignRank(member, null);
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			user.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"gang", "delete"}));
			deleteGangName.put(user, new AtomicReference<>(gang.getName()));

			confirmDelete.lock(sender, s -> {
				CountdownTimer timer = new CountdownTimer(gangland, 60, null, time -> {
					if (time.getTimeLeft() % 20 != 0) return;

					String string = Messages.GANG_REMOVE_CONFIRM.toString();
					String replace = string.replace("%timer%", TimeUtil.formatTime(time.getPeriod(), true,
					                                                               TimeMessages.getInstance()));
					s.sendMessage(replace);
				}, time -> {
					confirmDelete.unlock(s);
					deleteGangName.remove(user);
					deleteGangTimer.remove(s);
				});

				timer.start(false);
				deleteGangTimer.put(s, timer);
			});
		};
	}

	private ConfirmArgument gangDeleteConfirm() {
		return new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member member = memberManager.getMember(player.getUniqueId());

			if (member == null || !user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			if (member.getRank() == null) return;

			// check if the player is the owner
			Rank tail = rankManager.get(Settings.getGangRankTail());

			if (tail == null) return;

			if (!member.getRank().match(tail.getUsedId())) {
				user.sendMessage(Messages.NOT_OWNER.toString().replace("%tail%", Settings.getGangRankTail()));
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());
			if (gang == null) {
				user.resetGang();
				member.resetGang();
				member.setContribution(0D);
				memberManager.assignRank(member, null);
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			// need to get all the users, even if they are not online,
			// the periodical updates should take care of all the data save
			// change the data directly from the database, and collect the online players ONLY!
			List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager::getUser);

			// get the contribution frequency for each user, and return that frequency according to the current balance
			double total = gang.getMembers()
					.stream().mapToDouble(Member::getContribution).sum();

			IRepository<Member> memberRepository = repositoryRegistry.getRepository(Member.class);

			// Track online UUIDs so the offline loop below skips them
			Set<UUID> onlineUuids = gangOnlineMembers.stream()
					.map(u -> u.getUser().getUniqueId())
					.collect(Collectors.toSet());

			// change the online users gang id
			String depositMoney = Messages.DEPOSIT_MONEY_PLAYER.toString();
			for (User<Player> gangUser : gangOnlineMembers) {
				Player currentPlayer = gangUser.getUser();
				Member mem           = memberManager.getMember(currentPlayer.getUniqueId());

				// An online gang member with no cached Member cannot be paid out or reset — skip rather than NPE
				// halfway through the disband (GR-01).
				if (mem == null) continue;

				// Capture contribution before removeMember zeros it
				double     freq    = mem.getContribution();
				BigDecimal balance = gang.getEconomy().getAmount();
				BigDecimal amount = Math.round(total) == 0 ? Currency.ZERO
				                                           : Currency.multiply(balance, freq / total);

				gang.removeMember(gangUser, mem);

				gang.getEconomy().withdrawAmount(amount);
				gangUser.getEconomy().depositAmount(amount);

				// Persist the member reset (gang_id, contribution, rank cleared by removeMember)
				memberRepository.save(mem);

				// inform the online users
				String kickedFromGang      = Messages.KICKED_FROM_GANG.toString();
				String gangRemoved         = Messages.GANG_REMOVED.toString();
				String gangRemovedReplace  = gangRemoved.replace("%gang%", deleteGangName.get(user).get());
				String depositMoneyReplace = depositMoney.replace("%amount%", Settings.formatAmount(amount));
				gangUser.sendMessage(kickedFromGang, gangRemovedReplace, depositMoneyReplace);
			}

			// Pay out and reset every offline member through the same repository-cache path the online branch
			// above already uses — not raw SQL against gangland-impl's UserTable/MemberTable, which the gang
			// module cannot import (WS5 G1+G2+G3 gate: the module never depends on gangland-impl). Every existing
			// gang member already has a User row, and PlayerBootstrapService hydrates every such row into
			// offlineUserManager's cache at boot (and on /glw reload) before any command can run, so
			// offlineUserManager.getUser(...) reliably finds it — the create() branch below is a defensive
			// fallback, not the expected path.
			//
			// Deliberately synchronous, still on the calling thread (GR-02): this block mutates gang.getEconomy()
			// and the member's rank/gang_id, and the gang row / in-memory gang are torn down a few lines below.
			// Running it off-thread raced the disband and both duplicated and lost money. Disband is a rare,
			// confirm-gated command, so a short main-thread loop is the right trade for a deterministic payout —
			// same durability trade the online branch above already makes (its economy change also lands on the
			// next periodic autosave, not immediately).
			for (Member mem : gang.getMembers()) {
				if (onlineUuids.contains(mem.getUuid())) continue;

				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(mem.getUuid());

				User<OfflinePlayer> offlineUser = offlineUserManager.getUser(offlinePlayer);
				if (offlineUser == null) {
					offlineUser = offlineUserManager.create(offlinePlayer);
					offlineUserManager.add(offlineUser);
				}

				double     freq   = mem.getContribution();
				BigDecimal gangBal = gang.getEconomy().getAmount();
				BigDecimal amount = Math.round(total) == 0 ? Currency.ZERO
				                                           : Currency.multiply(gangBal, freq / total);

				gang.removeMember(offlineUser, mem);

				gang.getEconomy().withdrawAmount(amount);
				offlineUser.getEconomy().depositAmount(amount);

				memberRepository.save(mem);
			}

			// return quarter of the gang creation fees
			BigDecimal amount = Settings.getGangCreateFee()
			                            .divide(BigDecimal.valueOf(4), Currency.SCALE, Currency.ROUNDING_MODE);

			user.getEconomy().depositAmount(amount);
			user.sendMessage(depositMoney.replace("%amount%", Settings.formatAmount(amount)));

			IRepository<Gang>         gangRepository         = repositoryRegistry.getRepository(Gang.class);
			IRepository<GangAlliance> gangAllianceRepository = repositoryRegistry.getRepository(GangAlliance.class);

			// Delete the gang row and all related alliance rows (both directions)
			gangRepository.delete(gang);
			if (gangAllianceRepository instanceof GangAllianceRepository allianceRepo) {
				allianceRepo.deleteAllForGang(gang);
			}

			// Drop stale in-memory alliance refs on surviving allied gangs — without this,
			// `ally.isAlly(deletedGang)` / `getAllyListString()` keep returning true until restart.
			for (GangAlliance alliance : gang.getAllies()) {
				Gang allyGang = gangManager.getGang(alliance.ally().getId());
				if (allyGang != null) allyGang.removeAlly(gang);
			}

			gangManager.remove(gang);
			deleteGangName.remove(user);

			CountdownTimer timer = deleteGangTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				deleteGangTimer.remove(sender);
			}
		});
	}

}
