package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
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
import me.luckyraven.database.repositories.gang.GangAllianceRepository;
import me.luckyraven.database.tables.player.MemberTable;
import me.luckyraven.database.tables.player.UserTable;
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

	private final HashMap<User<Player>, AtomicReference<String>> deleteGangName;
	private final HashMap<CommandSender, CountdownTimer>         deleteGangTimer;

	private final ConfirmArgument confirmDelete;

	protected GangDeleteCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, new String[]{"delete", "remove", "del"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager   = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager   = gangland.getInitializer().getGangManager();
		this.rankManager   = gangland.getInitializer().getRankManager();

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

			user.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"gang", "delete"}));

			Gang gang = gangManager.getGang(user.getGangId());
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

			// need to get all the users, even if they are not online,
			// the periodical updates should take care of all the data save
			// change the data directly from the database, and collect the online players ONLY!
			List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager);

			// get the contribution frequency for each user, and return that frequency according to the current balance
			double total = gang.getMembers()
					.stream().mapToDouble(Member::getContribution).sum();

			Initializer      initializer      = gangland.getInitializer();
			GanglandDatabase ganglandDatabase = initializer.getGanglandDatabase();
			DatabaseHelper   helper           = new DatabaseHelper(gangland, ganglandDatabase);
			List<Table<?>>   tables           = ganglandDatabase.getTables();

			UserTable   userTable   = initializer.getInstanceFromTables(UserTable.class, tables);
			MemberTable memberTable = initializer.getInstanceFromTables(MemberTable.class, tables);

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

				gang.removeMember(gangUser, mem);

				// distribute the balance according to the contribution
				double freq    = mem.getContribution();
				double balance = gang.getEconomy().getBalance();
				double amount  = Math.round(total) == 0 ? 0 : freq / total * balance;

				gang.getEconomy().withdraw(amount);
				gangUser.getEconomy().deposit(amount);

				// Persist the member reset (gang_id, contribution, rank cleared by removeMember)
				memberRepository.save(mem);

				// inform the online users
				String kickedFromGang      = Messages.KICKED_FROM_GANG.toString();
				String gangRemoved         = Messages.GANG_REMOVED.toString();
				String gangRemovedReplace  = gangRemoved.replace("%gang%", deleteGangName.get(user).get());
				String depositMoneyReplace = depositMoney.replace("%amount%", Settings.formatDouble(amount));
				gangUser.sendMessage(kickedFromGang, gangRemovedReplace, depositMoneyReplace);
			}

			// Update offline members: query MemberTable for all gang members, distribute balance,
			// then reset their gang_id in both the DB and in-memory member objects
			helper.runQueriesAsync(database -> {
				String memberTableName = memberTable.getName();
				String userTableName   = userTable.getName();

				List<Object[]> gangMemberRows = QueryBuilder.on(database, memberTableName)
				                                            .select("*")
				                                            .where("gang_id", gang.getId())
				                                            .executeAll();

				for (Object[] row : gangMemberRows) {
					UUID uuid = UUID.fromString(String.valueOf(row[0]));

					// Skip online members – already handled above; auto-save will persist their changes
					if (onlineUuids.contains(uuid)) continue;

					Member mem = memberManager.getMember(uuid);
					if (mem == null) continue;

					// Fetch the offline member's current balance from the user table
					Object[] userRow = QueryBuilder.on(database, userTableName)
					                               .select("balance")
					                               .where("uuid", uuid.toString())
					                               .executeOne();
					if (userRow.length == 0) continue;

					double dbBalance = (double) userRow[0];
					double freq      = mem.getContribution();
					double gangBal   = gang.getEconomy().getBalance();
					double amount    = Math.round(total) == 0 ? 0 : freq / total * gangBal;

					gang.getEconomy().withdraw(amount);

					// Update offline user balance
					QueryBuilder.on(database, userTableName)
					            .update()
					            .set("balance", dbBalance + amount)
					            .where("uuid", uuid.toString())
					            .execute();

					// Reset member's gang_id in DB
					QueryBuilder.on(database, memberTableName)
					            .update()
					            .set("gang_id", -1)
					            .where("uuid", uuid.toString())
					            .execute();

					// Reset in-memory member state
					mem.resetGang();
					mem.setContribution(0D);
					mem.setRank(null);
				}
			});

			// return quarter of the gang creation fees
			double amount = Settings.getGangCreateFee() / 4;

			user.getEconomy().deposit(amount);
			user.sendMessage(depositMoney.replace("%amount%", Settings.formatDouble(amount)));

			var gangRepository         = ganglandDatabase.getRepositoryRegistry().getRepository(Gang.class);
			var gangAllianceRepository = ganglandDatabase.getRepositoryRegistry().getRepository(GangAlliance.class);

			// Delete the gang row and all related alliance rows (both directions)
			gangRepository.delete(gang);
			if (gangAllianceRepository instanceof GangAllianceRepository allianceRepo) {
				allianceRepo.deleteAllForGang(gang);
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
