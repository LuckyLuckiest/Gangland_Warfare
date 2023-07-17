package me.luckyraven.command.sub;

import com.google.common.util.concurrent.AtomicDouble;
import me.luckyraven.Gangland;
import me.luckyraven.account.Account;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.bukkit.InventoryGUI;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.ConfirmArgument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.rank.Rank;
import me.luckyraven.rank.RankManager;
import me.luckyraven.timer.CountdownTimer;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Types;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class SCGang extends CommandHandler {

	private final Gangland gangland;

	public SCGang(Gangland gangland) {
		super(gangland, "gang", true);
		this.gangland = gangland;

		List<CommandInformation> list = getCommands().entrySet()
		                                             .parallelStream()
		                                             .filter(entry -> entry.getKey().startsWith("gang"))
		                                             .sorted(Map.Entry.comparingByKey())
		                                             .map(Map.Entry::getValue)
		                                             .toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player       player = (Player) commandSender;
		User<Player> user   = gangland.getInitializer().getUserManager().getUser(player);

		if (user.hasGang()) {
			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Gang gang) {
					StringBuilder   members  = new StringBuilder();
					Map<UUID, Rank> groupMem = gang.getGroup();

					for (Map.Entry<UUID, Rank> mem : groupMem.entrySet()) {
						OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(mem.getKey());
						String        name          = offlinePlayer.getName();

						if (name != null) members.append(name);
						else members.append("(unregistered)");

						members.append(", ");
					}
					// remove trailing comma and space
					if (members.length() > 0) members.setLength(members.length() - 2);

					StringBuilder alias      = new StringBuilder();
					Set<Gang>     aliasGangs = gang.getAlias();

					for (Gang aliasGang : aliasGangs)
						alias.append(aliasGang.getName()).append(", ");

					// remove trailing comma and space
					if (alias.length() > 0) alias.setLength(alias.length() - 2);

					player.sendMessage(ChatUtil.color(String.format("&6%s&7 gang information", gang.getName()),
					                                  String.format("&7%s&8: &b%d", "ID", gang.getId()),
					                                  String.format("&7%s&8: &a%s%s", "Balance",
					                                                SettingAddon.getMoneySymbol(),
					                                                SettingAddon.formatDouble(gang.getBalance())),
					                                  String.format("&7%s&8: &7%s", "Description",
					                                                gang.getDescription()),
					                                  String.format("&7%s&8: &a%s", "Members", members),
					                                  String.format("&7%s&8: &a%s%s", "Bounty",
					                                                SettingAddon.getMoneySymbol(),
					                                                SettingAddon.formatDouble(gang.getBounty())),
					                                  String.format("&7%s&8: &a%s", "Alias", alias),
					                                  String.format("&7%s&8: &b%s", "Created", gang.getDateCreated())));
					break;
				}
		} else help(commandSender, 1);
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		UserManager<Player> userManager = gangland.getInitializer().getUserManager();
		GangManager         gangManager = gangland.getInitializer().getGangManager();
		RankManager         rankManager = gangland.getInitializer().getRankManager();

		// create gang
		// glw gang create <name>
		Argument create = gangCreate(userManager, gangManager, rankManager);

		// delete gang
		// glw gang delete
		Argument delete = gangDelete(userManager, gangManager, rankManager);


		// add user to gang
		// glw gang invite <name>
		Argument addUser = new Argument(new String[]{"invite", "add"}, getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".add_user");

		// remove user from gang
		// glw gang kick <name>
		Argument removeUser = new Argument("kick", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".remove_user");

		Argument invKickUser = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			// TODO add/kick user process

		});

		addUser.addSubArgument(invKickUser);
		removeUser.addSubArgument(invKickUser);

		// promote user in gang
		// glw gang promote <name>
		Argument promoteUser = new Argument("promote", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".promote_user");

		// demote user in gang
		// glw gang demote <name>
		Argument demoteUser = new Argument("demote", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".demote_user");

		Argument promDemoUser = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			// TODO promote/demote user process
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}
		});

		promoteUser.addSubArgument(promDemoUser);
		demoteUser.addSubArgument(promDemoUser);

		// deposit money to gang
		// glw gang deposit <amount>
		Argument deposit = new Argument("deposit", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
		}, getPermission() + ".deposit");

		// withdraw money from gang
		// glw gang withdraw <amount>
		Argument withdraw = new Argument("withdraw", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
		}, getPermission() + ".withdraw");

		Argument amount = gangEconomyAmount(userManager, gangManager);

		deposit.addSubArgument(amount);
		withdraw.addSubArgument(amount);

		// balance of gang
		// glw gang balance
		Argument balance = new Argument(new String[]{"balance", "bal"}, getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());
			sender.sendMessage(MessageAddon.GANG_BALANCE.toString()
			                                            .replace("%balance%",
			                                                     SettingAddon.formatDouble(gang.getBalance())));
		}, getPermission() + ".balance");

		String[] statsStr = {"stats", "statistics"};
		Argument stats = new Argument(statsStr, getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang         gang = gangManager.getGang(user.getGangId());
			InventoryGUI gui  = new InventoryGUI("&6&l" + gang.getName() + "&r gang", 36);

			gui.setItem(0, Material.GOLD_BLOCK, "&bBalance", null, true, false);

			gui.fillInventory();

			gui.open(user);
		});

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);
		arguments.add(addUser);
		arguments.add(removeUser);
		arguments.add(promoteUser);
		arguments.add(demoteUser);
		arguments.add(deposit);
		arguments.add(withdraw);
		arguments.add(balance);
		arguments.add(stats);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Gang");
	}

	private Argument gangCreate(UserManager<Player> userManager, GangManager gangManager, RankManager rankManager) {
		Argument create = new Argument("create", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasGang()) {
				player.sendMessage(MessageAddon.GANG_EXIST.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".create");

		HashMap<User<Player>, AtomicReference<String>> createGangName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>         createGangTimer = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasGang()) {
				player.sendMessage(MessageAddon.GANG_EXIST.toString());
				return;
			}

			if (user.getBalance() < SettingAddon.getGangCreateFee()) {
				player.sendMessage(MessageAddon.CANNOT_CREATE_GANG.toString());
				return;
			}

			Gang   gang   = new Gang();
			Random random = new Random();
			do gang.setId(random.nextInt(999_999));
			while (gangManager.contains(gang));

			gang.addUser(user, rankManager.get(SettingAddon.getGangRankTail()));
			gang.setName(createGangName.get(user).get());
			gang.setBalance(SettingAddon.getGangInitialBalance());

			gangManager.add(gang);

			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
				if (handler instanceof UserDatabase userDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						user.setBalance(user.getBalance() - SettingAddon.getGangCreateFee());
						userDatabase.updateAccountTable(user);
					});
				}
				if (handler instanceof GangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						List<String> mem = new ArrayList<>();

						for (Map.Entry<UUID, Rank> entry : gang.getGroup().entrySet())
							mem.add(entry.getKey() + ":" + entry.getValue().getName());

						String members = database.createList(mem);

						List<String> cont = new ArrayList<>();
						for (Map.Entry<UUID, Double> entry : gang.getContribution().entrySet())
							cont.add(entry.getKey() + ":" + entry.getValue());

						String contribution = database.createList(cont);

						database.table("data").insert(new String[]{
								"id", "name", "description", "members", "contribution", "bounty", "alias", "created"
						}, new Object[]{
								gang.getId(), gang.getName(), gang.getDescription(), members, contribution,
								gang.getBounty(), "", gang.getCreated()
						}, new int[]{
								Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.LONGVARCHAR,
								Types.DOUBLE, Types.LONGVARCHAR, Types.BIGINT
						});
						database.table("account").insert(new String[]{"id", "balance"},
						                                 new Object[]{gang.getId(), gang.getBalance()},
						                                 new int[]{Types.INTEGER, Types.DOUBLE});
					});
				}
			}

			player.sendMessage(MessageAddon.GANG_CREATED.toString().replace("%gang%", gang.getName()));

			createGangName.remove(user);

			CountdownTimer timer = createGangTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				createGangTimer.remove(sender);
			}
		});

		create.addSubArgument(confirmCreate);

		Argument createName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasGang()) {
				player.sendMessage(MessageAddon.GANG_EXIST.toString());
				return;
			}

			if (confirmCreate.isConfirmed()) return;

			AtomicReference<String> name = new AtomicReference<>(args[2]);

			if (!SettingAddon.isGangNameDuplicates()) for (Gang gang : gangManager.getGangs().values())
				if (gang.getName().equalsIgnoreCase(name.get())) {
					player.sendMessage(MessageAddon.DUPLICATE_GANG_NAME.toString().replace("%gang%", name.get()));
					return;
				}

			createGangName.put(user, name);

			// Need to notify the player and give access to confirm
			player.sendMessage(MessageAddon.GANG_CREATE_FEE.toString()
			                                               .replace("%amount%", SettingAddon.formatDouble(
					                                               SettingAddon.getGangCreateFee())));
			player.sendMessage(ChatUtil.confirmCommand(new String[]{"gang", "create"}));
			confirmCreate.setConfirmed(true);

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				sender.sendMessage(MessageAddon.GANG_CREATE_CONFIRM.toString()
				                                                   .replace("%timer%",
				                                                            String.valueOf(time.getDuration())));
			}, null, time -> {
				confirmCreate.setConfirmed(false);
				createGangName.remove(user);
			});

			timer.start();
			createGangTimer.put(sender, timer);
		});

		create.addSubArgument(createName);

		return create;
	}

	private Argument gangDelete(UserManager<Player> userManager, GangManager gangManager, RankManager rankManager) {
		String[]                                       delArr          = {"delete", "remove"};
		HashMap<User<Player>, AtomicReference<String>> deleteGangName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>         deleteGangTimer = new HashMap<>();

		ConfirmArgument confirmDelete = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			// check if the player is the owner
			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Gang gang) {
					if (!gang.getUserRank(player.getUniqueId()).match(
							rankManager.get(SettingAddon.getGangRankTail()).getUsedId())) {
						player.sendMessage(
								MessageAddon.NOT_OWNER.toString().replace("%tail%", SettingAddon.getGangRankTail()));
						return;
					}
					break;
				}

			Gang               gang  = gangManager.getGang(user.getGangId());
			List<User<Player>> users = new ArrayList<>();

			// need to get all the users, even if they are not online
			// change the data directly from the database, and collect the online players ONLY!
			for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
				User<Player> onUser = userManager.getUser(onlinePlayer);
				if (onUser.hasGang() && onUser.getGangId() == gang.getId()) users.add(onUser);
			}

			AtomicDouble          total         = new AtomicDouble(0D);
			HashMap<UUID, Double> contributions = new HashMap<>();
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				// remove the gang content
				// get the contribution frequency for each user, and return that frequency according to the current balance
				if (handler instanceof GangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						Object[] contributionObject = database.select("id = ?", new Object[]{gang.getId()},
						                                              new int[]{Types.INTEGER},
						                                              new String[]{"contribution"});
						String       data = (String) contributionObject[0];
						List<String> cont = database.getList(data);
						for (String info : cont) {
							String[] temp = info.split(":");
							double   freq = Double.parseDouble(temp[1]);
							total.getAndAdd(freq);
							contributions.put(UUID.fromString(temp[0]), freq);
						}
					});

					break;
				}

			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
				// change the gang id for all the members
				if (handler instanceof UserDatabase userDatabase) {
					// change the online users gang id
					for (User<Player> gangUser : users) {
						gang.removeUser(gangUser);
						// distribute the balance according to the contribution
						double freq    = contributions.get(gangUser.getUser().getUniqueId());
						double balance = gang.getBalance();
						double amount  = Math.round(total.get()) == 0 ? 0 : freq / total.get() * balance;
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

						helper.runQueries(database -> userDatabase.updateAccountTable(gangUser));

						contributions.remove(gangUser.getUser().getUniqueId());
					}
					// change the others gang id
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
							UUID   uuid    = UUID.fromString(String.valueOf(data[0]));
							double balance = (double) data[1];

							double freq    = contributions.get(uuid);
							double gangBal = gang.getBalance();
							double amount  = Math.round(total.get()) == 0 ? 0 : freq / total.get() * gangBal;

							gang.setBalance(gangBal - amount);

							database.table("account").update("uuid = ?", new Object[]{uuid.toString()},
							                                 new int[]{Types.CHAR}, new String[]{"balance", "gang_id"},
							                                 new Object[]{balance + amount, -1},
							                                 new int[]{Types.DOUBLE, Types.INTEGER});

							contributions.remove(uuid);
						}
					});

					helper.runQueries(database -> {
						double amount = SettingAddon.getGangCreateFee() / 4;
						user.setBalance(user.getBalance() + amount);
						player.sendMessage(MessageAddon.DEPOSIT_MONEY_PLAYER.toString()
						                                                    .replace("%amount%",
						                                                             SettingAddon.formatDouble(
								                                                             amount)));
						userDatabase.updateAccountTable(user);
					});
				}

				if (handler instanceof GangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						database.table("account").delete("id", String.valueOf(gang.getId()));
						database.table("data").delete("id", String.valueOf(gang.getId()));
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

		Argument delete = new Argument(delArr, getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			if (confirmDelete.isConfirmed()) return;

			confirmDelete.setConfirmed(true);
			player.sendMessage(ChatUtil.confirmCommand(new String[]{"gang", "delete"}));

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				sender.sendMessage(MessageAddon.GANG_REMOVE_CONFIRM.toString()
				                                                   .replace("%timer%",
				                                                            String.valueOf(time.getDuration())));
			}, null, time -> {
				confirmDelete.setConfirmed(false);
				deleteGangName.remove(user);
			});

			timer.start();

			Gang gang = gangManager.getGang(user.getGangId());

			deleteGangName.put(user, new AtomicReference<>(gang.getName()));
			deleteGangTimer.put(sender, timer);
		}, getPermission() + ".delete");

		delete.addSubArgument(confirmDelete);

		return delete;
	}

	private Argument gangEconomyAmount(UserManager<Player> userManager, GangManager gangManager) {
		Argument amount = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			try {
				double argAmount = Double.parseDouble(args[2]);
				Gang   gang      = gangManager.getGang(user.getGangId());

				double rate   = SettingAddon.getGangContributionRate();
				int    length = String.valueOf((int) rate).length() - 1;
				double round  = Math.pow(10, length);

				double contribution = Math.round(argAmount / rate * round) / round;

				double prevValue = gang.getContribution().get(user.getUser().getUniqueId());

				List<User<Player>> users = new ArrayList<>();

				for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
					User<Player> onUser = userManager.getUser(onlinePlayer);
					if (onUser.hasGang() && onUser.getGangId() == gang.getId()) users.add(onUser);
				}

				switch (args[1].toLowerCase()) {
					case "deposit" -> {
						if (user.getBalance() < argAmount) {
							player.sendMessage(MessageAddon.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
							return;
						} else if (gang.getBalance() + argAmount >= SettingAddon.getGangMaxBalance()) {
							player.sendMessage(MessageAddon.CANNOT_EXCEED_MAXIMUM.toString());
							return;
						}

						user.setBalance(user.getBalance() - argAmount);
						gang.setBalance(gang.getBalance() + argAmount);
						gang.getContribution().put(user.getUser().getUniqueId(), contribution + prevValue);
						for (User<Player> gangUser : users) {
							gangUser.getUser().sendMessage(MessageAddon.GANG_MONEY_DEPOSIT.toString()
							                                                              .replace("%player%",
							                                                                       player.getName())
							                                                              .replace("%amount%",
							                                                                       SettingAddon.formatDouble(
									                                                                       argAmount)));
						}
						player.sendMessage(ChatUtil.color("&a+" + contribution));
					}

					case "withdraw" -> {
						if (gang.getBalance() < argAmount) {
							player.sendMessage(MessageAddon.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
							return;
						}

						user.setBalance(user.getBalance() + argAmount);
						gang.setBalance(gang.getBalance() - argAmount);
						// the user can get to negative value
						gang.getContribution().put(user.getUser().getUniqueId(), contribution - prevValue);
						for (User<Player> gangUser : users) {
							gangUser.getUser().sendMessage(MessageAddon.GANG_MONEY_WITHDRAW.toString()
							                                                               .replace("%player%",
							                                                                        player.getName())
							                                                               .replace("%amount%",
							                                                                        SettingAddon.formatDouble(
									                                                                        argAmount)));
						}
						player.sendMessage(ChatUtil.color("&c-" + contribution));
					}
				}

				// update database
				for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
					if (handler instanceof UserDatabase userDatabase) {
						DatabaseHelper helper = new DatabaseHelper(gangland, handler);

						helper.runQueries(database -> userDatabase.updateAccountTable(user));
					}

					if (handler instanceof GangDatabase gangDatabase) {
						DatabaseHelper helper = new DatabaseHelper(gangland, handler);

						helper.runQueries(database -> {
							gangDatabase.updateAccountTable(gang);
							gangDatabase.updateDataTable(gang);
						});
					}
				}
			} catch (NumberFormatException exception) {
				player.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", args[2]));
			}
		});

		return amount;
	}

}
