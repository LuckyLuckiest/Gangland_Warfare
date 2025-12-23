package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.GangAlliesTable;
import me.luckyraven.database.tables.GangTable;
import me.luckyraven.database.tables.MemberTable;
import me.luckyraven.database.tables.UserTable;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.Pair;
import me.luckyraven.util.TimeUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Types;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
			Member       member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			// check if the player is the owner
			if (member.getRank() == null) return;

			Rank tail = rankManager.get(SettingAddon.getGangRankTail());

			if (tail == null) return;

			if (!member.getRank().match(tail.getUsedId())) {
				user.sendMessage(MessageAddon.NOT_OWNER.toString().replace("%tail%", SettingAddon.getGangRankTail()));
				return;
			}

			if (confirmDelete.isConfirmed()) return;

			confirmDelete.setConfirmed(true);
			user.sendMessage(ChatUtil.confirmCommand(new String[]{"gang", "delete"}));

			CountdownTimer timer = new CountdownTimer(gangland, 60, null, time -> {
				if (time.getTimeLeft() % 20 != 0) return;

				sender.sendMessage(MessageAddon.GANG_REMOVE_CONFIRM.toString()
																   .replace("%timer%",
																			TimeUtil.formatTime(time.getPeriod(),
																								true)));
			}, time -> {
				confirmDelete.setConfirmed(false);
				deleteGangName.remove(user);
				deleteGangTimer.remove(sender);
			});

			timer.start(false);

			Gang gang = gangManager.getGang(user.getGangId());

			deleteGangName.put(user, new AtomicReference<>(gang.getName()));
			deleteGangTimer.put(sender, timer);
		};
	}

	private ConfirmArgument gangDeleteConfirm() {
		return new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				user.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			if (member.getRank() == null) return;

			// check if the player is the owner
			Rank tail = rankManager.get(SettingAddon.getGangRankTail());

			if (tail == null) return;

			if (!member.getRank().match(tail.getUsedId())) {
				user.sendMessage(MessageAddon.NOT_OWNER.toString().replace("%tail%", SettingAddon.getGangRankTail()));
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			// need to get all the users, even if they are not online,
			// the periodical updates should take care of all the data save
			// change the data directly from the database, and collect the online players ONLY!
			List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager);

			// get the contribution frequency for each user, and return that frequency according to the current balance
			double total = gang.getGroup()
							   .stream().mapToDouble(Member::getContribution).sum();

			Initializer      initializer      = gangland.getInitializer();
			GanglandDatabase ganglandDatabase = initializer.getGanglandDatabase();
			DatabaseHelper   helper           = new DatabaseHelper(gangland, ganglandDatabase);
			List<Table<?>>   tables           = ganglandDatabase.getTables();

			UserTable       userTable       = initializer.getInstanceFromTables(UserTable.class, tables);
			MemberTable     memberTable     = initializer.getInstanceFromTables(MemberTable.class, tables);
			GangTable       gangTable       = initializer.getInstanceFromTables(GangTable.class, tables);
			GangAlliesTable gangAlliesTable = initializer.getInstanceFromTables(GangAlliesTable.class, tables);

			// change the online users gang id
			String depositMoney = MessageAddon.DEPOSIT_MONEY_PLAYER.toString();
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

				// inform the online users
				String kickedFromGang      = MessageAddon.KICKED_FROM_GANG.toString();
				String gangRemoved         = MessageAddon.GANG_REMOVED.toString();
				String gangRemovedReplace  = gangRemoved.replace("%gang%", deleteGangName.get(user).get());
				String depositMoneyReplace = depositMoney.replace("%amount%", SettingAddon.formatDouble(amount));
				gangUser.sendMessage(kickedFromGang, gangRemovedReplace, depositMoneyReplace);
			}

			helper.runQueriesAsync(database -> {
				Database       userConfig = database.table(userTable.getName());
				List<Object[]> allUsers   = userConfig.selectAll();
				List<Object[]> gangUsers = allUsers.parallelStream()
												   .filter(obj -> Arrays.stream(obj)
																		.anyMatch(o -> o.toString()
																						.equals(String.valueOf(
																								gang.getId()))))
												   .toList();

				// update offline users
				for (Object[] data : gangUsers) {
					UUID   uuid = UUID.fromString(String.valueOf(data[0]));
					Member mem  = memberManager.getMember(uuid);

					gang.removeMember(mem);

					double balance = (double) data[1];
					double freq    = mem.getContribution();
					double gangBal = gang.getEconomy().getBalance();
					double amount  = Math.round(total) == 0 ? 0 : freq / total * gangBal;

					gang.getEconomy().withdraw(amount);

					// update the balance
					database.table(userTable.getName())
							.update("uuid = ?", new Object[]{uuid.toString()}, new int[]{Types.CHAR},
									new String[]{"balance"}, new Object[]{balance + amount}, new int[]{Types.DOUBLE});
					// update the gang id
					database.table(memberTable.getName())
							.update("uuid = ?", new Object[]{uuid.toString()}, new int[]{Types.CHAR},
									new String[]{"gang_id"}, new Object[]{-1}, new int[]{Types.INTEGER});
				}
			});

			// return quarter of the gang creation fees
			double amount = SettingAddon.getGangCreateFee() / 4;

			user.getEconomy().deposit(amount);
			user.sendMessage(depositMoney.replace("%amount%", SettingAddon.formatDouble(amount)));

			// remove the gang information
			helper.runQueriesAsync(database -> {
				int removedGang = gang.getId();

				// remove the gang itself
				database.table(gangTable.getName()).delete("id", String.valueOf(removedGang));

				// remove allied gangs to itself
				for (Pair<Gang, Long> alliedGangPair : gang.getAllies()) {
					int      alliedGangId = alliedGangPair.first().getId();
					Database config       = database.table(gangAlliesTable.getName());

					config.delete("gang_id", String.valueOf(alliedGangId));
					// remove other gangs who're allied to the removed gang
					// the number of gangs allied with the removed gang is equal to the opposite
					config.delete("allie_id", String.valueOf(removedGang));
				}
			});

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
