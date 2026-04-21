package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangAlliance;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.member.Member;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.TableLookup;
import me.luckyraven.database.repositories.gang.GangAllianceRepository;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.database.DatabaseHelper;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.database.query.QueryBuilder;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.utilities.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

class GangDeleteCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;
	private final GanglandDatabase    ganglandDatabase;

	private final HashMap<User<Player>, AtomicReference<String>> deleteGangName;
	private final HashMap<CommandSender, CountdownTimer>         deleteGangTimer;

	private final ConfirmArgument confirmDelete;

	protected GangDeleteCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                            RankManager rankManager, GanglandDatabase ganglandDatabase) {
		super(gangland, new String[]{"delete", "remove", "del"}, tree, parent);

		this.gangland         = gangland;
		this.tree             = tree;
		this.userManager      = userManager;
		this.memberManager    = memberManager;
		this.gangManager      = gangManager;
		this.rankManager      = rankManager;
		this.ganglandDatabase = ganglandDatabase;

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

			if (!user.hasGang()) {
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

			if (!user.hasGang()) {
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
			List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager);

			// get the contribution frequency for each user, and return that frequency according to the current balance
			double total = gang.getMembers()
					.stream().mapToDouble(Member::getContribution).sum();

			DatabaseHelper helper = new DatabaseHelper(gangland, ganglandDatabase);
			List<Table<?>> tables = ganglandDatabase.getTables();

			UserTable   userTable   = TableLookup.find(UserTable.class, tables);
			MemberTable memberTable = TableLookup.find(MemberTable.class, tables);

			var memberRepository = ganglandDatabase.getRepositoryRegistry().getRepository(Member.class);

			// Track online UUIDs so the async block can skip them
			Set<UUID> onlineUuids = gangOnlineMembers.stream()
					.map(u -> u.getUser().getUniqueId())
					.collect(Collectors.toSet());

			// change the online users gang id
			String depositMoney = Messages.DEPOSIT_MONEY_PLAYER.toString();
			for (User<Player> gangUser : gangOnlineMembers) {
				Player currentPlayer = gangUser.getUser();
				Member mem           = memberManager.getMember(currentPlayer.getUniqueId());

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

			// Update offline members: query MemberTable for all gang members, distribute balance,
			// then reset their gang_id in both the DB and (if cached) in-memory member objects.
			// The SQL reset must run even when the Member isn't cached in MemberManager.
			helper.runQueriesAsync(database -> {
				String memberTableName = memberTable.getName();
				String userTableName   = userTable.getName();

				List<Object[]> gangMemberRows = QueryBuilder.on(database, memberTableName)
				                                            .select("uuid", "contribution")
				                                            .where("gang_id", gang.getId())
				                                            .executeAll();

				for (Object[] row : gangMemberRows) {
					UUID uuid = UUID.fromString(String.valueOf(row[0]));

					// Skip online members – already handled synchronously above
					if (onlineUuids.contains(uuid)) continue;

					double freq = (double) row[1];

					// Fetch the offline member's current balance from the user table
					Object[] userRow = QueryBuilder.on(database, userTableName)
					                               .select("balance")
					                               .where("uuid", uuid.toString())
					                               .executeOne();
					if (userRow.length == 0) continue;

					double     dbBalance = (double) userRow[0];
					BigDecimal gangBal   = gang.getEconomy().getAmount();
					BigDecimal amount = Math.round(total) == 0 ? Currency.ZERO
					                                           : Currency.multiply(gangBal, freq / total);

					gang.getEconomy().withdrawAmount(amount);

					// Update offline user balance
					QueryBuilder.on(database, userTableName)
					            .update()
					            .set("balance", dbBalance + amount.doubleValue())
					            .where("uuid", uuid.toString())
					            .execute();

					// Always reset member row in DB — parity with Gang#removeMember(Member)
					QueryBuilder.on(database, memberTableName)
					            .update()
					            .set("gang_id", -1)
					            .set("contribution", 0D)
					            .set("rank_id", -1)
					            .where("uuid", uuid.toString())
					            .execute();

					// If the Member happens to be cached, keep memory in sync with the DB
					Member mem = memberManager.getMember(uuid);
					if (mem != null) {
						mem.resetGang();
						mem.setContribution(0D);
						memberManager.assignRank(mem, null);
					}
				}
			});

			// return quarter of the gang creation fees
			BigDecimal amount = Settings.getGangCreateFee()
			                            .divide(BigDecimal.valueOf(4), Currency.SCALE, Currency.ROUNDING_MODE);

			user.getEconomy().depositAmount(amount);
			user.sendMessage(depositMoney.replace("%amount%", Settings.formatAmount(amount)));

			var gangRepository         = ganglandDatabase.getRepositoryRegistry().getRepository(Gang.class);
			var gangAllianceRepository = ganglandDatabase.getRepositoryRegistry().getRepository(GangAlliance.class);

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
