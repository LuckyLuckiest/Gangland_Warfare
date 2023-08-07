package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.ConfirmArgument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.rank.RankManager;
import me.luckyraven.timer.CountdownTimer;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Types;
import java.util.*;
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

	protected GangDeleteCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                            RankManager rankManager) {
		super(new String[]{"delete", "remove"}, tree, "delete", parent);

		this.gangland = gangland;
		this.tree = tree;

		this.userManager = userManager;
		this.memberManager = memberManager;
		this.gangManager = gangManager;
		this.rankManager = rankManager;

		this.deleteGangName = new HashMap<>();
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
			if (!member.getRank().match(rankManager.get(SettingAddon.getGangRankTail()).getUsedId())) {
				player.sendMessage(MessageAddon.NOT_OWNER.toString().replace("%tail%", SettingAddon.getGangRankTail()));
				return;
			}

			if (confirmDelete.isConfirmed()) return;

			confirmDelete.setConfirmed(true);
			player.sendMessage(ChatUtil.confirmCommand(new String[]{"gang", "delete"}));

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> sender.sendMessage(
					MessageAddon.GANG_REMOVE_CONFIRM.toString().replace("%timer%", String.valueOf(time.getDuration()))),
			                                          null, time -> {
				confirmDelete.setConfirmed(false);
				deleteGangName.remove(user);
				deleteGangTimer.remove(sender);
			});

			timer.start();

			Gang gang = gangManager.getGang(user.getGangId());

			deleteGangName.put(user, new AtomicReference<>(gang.getName()));
			deleteGangTimer.put(sender, timer);
		};
	}

	private ConfirmArgument gangDeleteConfirm() {
		return new ConfirmArgument(tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			// check if the player is the owner
			if (!member.getRank().match(rankManager.get(SettingAddon.getGangRankTail()).getUsedId())) {
				player.sendMessage(MessageAddon.NOT_OWNER.toString().replace("%tail%", SettingAddon.getGangRankTail()));
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			// need to get all the users, even if they are not online,
			// change the data directly from the database, and collect the online players ONLY!
			List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager);
			List<Member>       members           = new ArrayList<>(gang.getGroup());

			double total = gang.getGroup().stream().mapToDouble(Member::getContribution).sum();
			// get the contribution frequency for each user, and return that frequency according to the current balance

			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
				// change the gang id for all the members
				if (handler instanceof UserDatabase userDatabase) {
					// change the online users gang id
					for (User<Player> gangUser : gangOnlineMembers) {
						Member mem = memberManager.getMember(gangUser.getUser().getUniqueId());
						gang.removeMember(gangUser, mem);
						// distribute the balance according to the contribution
						double freq    = mem.getContribution();
						double balance = gang.getBalance();
						double amount  = Math.round(total) == 0 ? 0 : freq / total * balance;
						gang.setBalance(balance - amount);
						gangUser.setBalance(gangUser.getBalance() + amount);

						// inform the online users
						gangUser.getUser().sendMessage(MessageAddon.KICKED_FROM_GANG.toString(),
						                               MessageAddon.GANG_REMOVED.toString()
						                                                        .replace("%gang%",
						                                                                 deleteGangName.get(user)
						                                                                               .get()),
						                               MessageAddon.DEPOSIT_MONEY_PLAYER.toString()
						                                                                .replace("%amount%",
						                                                                         SettingAddon.formatDouble(
								                                                                         amount)));
						// update the database
						DatabaseHelper helper = new DatabaseHelper(gangland, handler);

						helper.runQueries(database -> userDatabase.updateDataTable(gangUser));
					}
					// change the others' gang id
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						List<Object[]> allUsers = database.table("account").selectAll();
						List<Object[]> gangUsers = allUsers.parallelStream()
						                                   .filter(obj -> Arrays.stream(obj)
						                                                        .anyMatch(o -> o.toString()
						                                                                        .equals(String.valueOf(
								                                                                        gang.getId()))))
						                                   .toList();

						for (Object[] data : gangUsers) {
							UUID   uuid = UUID.fromString(String.valueOf(data[0]));
							Member mem  = memberManager.getMember(uuid);
							gang.removeMember(mem);

							double balance = (double) data[1];
							double freq    = mem.getContribution();
							double gangBal = gang.getBalance();
							double amount  = Math.round(total) == 0 ? 0 : freq / total * gangBal;

							gang.setBalance(gangBal - amount);

							database.table("data").update("uuid = ?", new Object[]{uuid.toString()},
							                              new int[]{Types.CHAR}, new String[]{"balance", "gang_id"},
							                              new Object[]{balance + amount, -1},
							                              new int[]{Types.DOUBLE, Types.INTEGER});
						}
					});

					helper.runQueries(database -> {
						double amount = SettingAddon.getGangCreateFee() / 4;
						user.setBalance(user.getBalance() + amount);
						player.sendMessage(MessageAddon.DEPOSIT_MONEY_PLAYER.toString()
						                                                    .replace("%amount%",
						                                                             SettingAddon.formatDouble(
								                                                             amount)));
						userDatabase.updateDataTable(user);
					});
				}

				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						database.table("data").delete("id", String.valueOf(gang.getId()));

						for (Member mem : members)
							gangDatabase.updateMembersTable(mem);
					});
				}
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
