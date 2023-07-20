package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.gui.InventoryGUI;
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
import me.luckyraven.datastructure.Node;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.rank.Rank;
import me.luckyraven.rank.RankManager;
import me.luckyraven.timer.CountdownTimer;
import me.luckyraven.util.ChatUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;

import java.sql.Types;
import java.time.Instant;
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
		UserManager<Player> userManager = gangland.getInitializer().getUserManager();
		GangManager         gangManager = gangland.getInitializer().getGangManager();

		Player       player = (Player) commandSender;
		User<Player> user   = userManager.getUser(player);

		if (user.hasGang()) {
			Gang gang = gangManager.getGang(user.getGangId());

			gangStat(user, userManager, gang);
		} else help(commandSender, 1);
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		UserManager<Player> userManager   = gangland.getInitializer().getUserManager();
		GangManager         gangManager   = gangland.getInitializer().getGangManager();
		MemberManager       memberManager = gangland.getInitializer().getMemberManager();
		RankManager         rankManager   = gangland.getInitializer().getRankManager();

		// create gang
		// glw gang create <name>
		Argument create = gangCreate(userManager, memberManager, gangManager, rankManager);

		// delete gang
		// glw gang delete
		Argument delete = gangDelete(userManager, memberManager, gangManager, rankManager);

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

		HashMap<User<Player>, Gang>           playerInvite = new HashMap<>();
		HashMap<User<Player>, CountdownTimer> inviteTimer  = new HashMap<>();

		Argument inviteName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			String targetStr = args[2];
			Player target    = Bukkit.getPlayer(targetStr);

			if (target == null) {
				sender.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			Gang         gang       = gangManager.getGang(user.getGangId());
			User<Player> targetUser = userManager.getUser(target);

			if (targetUser.hasGang()) {
				sender.sendMessage(MessageAddon.TARGET_IN_GANG.toString().replace("%player%", targetStr));
				return;
			}

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				player.sendMessage(MessageAddon.GANG_INVITE_PLAYER.toString().replace("%player%", targetStr));
				target.sendMessage(MessageAddon.GANG_INVITE_TARGET.toString().replace("%gang%", gang.getName()));
			}, null, time -> {
				playerInvite.remove(targetUser);
				inviteTimer.remove(targetUser);
			});

			timer.start();

			playerInvite.put(targetUser, gang);
			inviteTimer.put(targetUser, timer);
		});

		addUser.addSubArgument(inviteName);

		// glw gang accept
		Argument acceptInvite = new Argument("accept", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!playerInvite.containsKey(user)) {
				player.sendMessage(MessageAddon.NO_GANG_INVITATION.toString());
				return;
			}

			if (user.hasGang()) {
				player.sendMessage(MessageAddon.PLAYER_IN_GANG.toString());
				return;
			}

			Gang   gang   = playerInvite.get(user);
			Member member = memberManager.getMember(player.getUniqueId());
			Rank   rank   = rankManager.get(SettingAddon.getGangRankHead());

			// broadcast in gang join of the player
			// don't broadcast to the joined member
			List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager);
			for (User<Player> onUser : gangOnlineMembers)
				onUser.getUser().sendMessage(
						MessageAddon.GANG_PLAYER_JOINED.toString().replace("%player%", user.getUser().getName()));

			member.setGangJoinDate(Instant.now().toEpochMilli());
			gang.addMember(user, member, rank);
			sender.sendMessage(MessageAddon.GANG_INVITE_ACCEPT.toString().replace("%gang%", gang.getName()));

			// update to database
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> gangDatabase.updateMembersTable(member));
				}

				if (handler instanceof UserDatabase userDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> userDatabase.updateAccountTable(user));
				}
			}

			playerInvite.remove(user);

			CountdownTimer timer = inviteTimer.get(user);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				inviteTimer.remove(user);
			}
		});

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

		Argument kickName = gangKick(userManager, memberManager, gangManager, rankManager);

		removeUser.addSubArgument(kickName);

		// leave the gang
		// glw gang leave
		Argument leave = gangLeave(userManager, memberManager, gangManager, rankManager);

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

		Argument promDemoUser = gangRankStatus(userManager, memberManager, gangManager, rankManager);

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

		Argument amount = gangEconomyAmount(userManager, memberManager, gangManager);

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

		// change gang name
		// glw gang name <name>
		Argument name = gangRename(userManager, gangManager);

		// change gang description
		// opens an anvil with a name-tag that can change the title
		// glw gang desc
		String[] descStr = {"desc", "description"};
		Argument description = new Argument(descStr, getArgumentTree(), (argument, sender, args) -> {

		}, getPermission() + ".change_description");

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);

		arguments.add(addUser);
		arguments.add(acceptInvite);

		arguments.add(removeUser);
		arguments.add(leave);

		arguments.add(promoteUser);
		arguments.add(demoteUser);

		arguments.add(deposit);
		arguments.add(withdraw);
		arguments.add(balance);

		arguments.add(name);
		arguments.add(description);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Gang");
	}

	private void gangStat(User<Player> user, UserManager<Player> userManager, Gang gang) {
		InventoryGUI gui = new InventoryGUI("&6&l" + gang.getName() + "&r gang", 45);

		gui.setItem(11, Material.GOLD_BLOCK, "&bBalance", new ArrayList<>(
				List.of(String.format("&e%s%s", SettingAddon.getMoneySymbol(),
				                      SettingAddon.formatDouble(gang.getBalance())))), true, false);
		gui.setItem(13, Material.CRAFTING_TABLE, "&bID", new ArrayList<>(List.of("&e" + gang.getId())), false, false);
		gui.setItem(15, Material.PAPER, "&bDescription", new ArrayList<>(List.of("&e" + gang.getDescription())), false,
		            false);
		// this item should take you to another gui page
		gui.setItem(19, Material.PLAYER_HEAD, "&bMembers", new ArrayList<>(
				            List.of("&a" + gang.getOnlineMembers(userManager).size() + "&7/&e" + gang.getGroup().size())), false,
		            false, (inventory, item) -> {
					inventory.close(user);

					int size          = gang.getGroup().size();
					int inventorySize = Math.min((int) Math.ceil((double) size / 9) * 9, InventoryGUI.MAX_SLOTS);

					InventoryGUI members = new InventoryGUI("&6&lGang members", inventorySize == 0 ? 9 : inventorySize);

					int i = 0;
					for (Member member : gang.getGroup()) {
						// temporary measure for limited members
						if (i >= inventorySize) break;
						// this will work if there were at most 45 members
						// need to add compatibility if there were more than 45
						OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
						Rank          userRank      = member.getRank();

						List<String> data = new ArrayList<>();
						data.add("&7Rank:&e " + userRank.getName());
						data.add("&7Contribution:&e " + member.getContribution());
						data.add("&7Joined:&e " + member.gangJoinDate());

						ItemBuilder itemBuilder = new ItemBuilder(Material.PLAYER_HEAD).setDisplayName(
								"&b" + offlinePlayer.getName()).setLore(data);

						// change the skull nbt data (texture)
						itemBuilder.modifyNBT(nbt -> nbt.setString("SkullOwner", offlinePlayer.getName()));

						members.setItem(i++, itemBuilder.build(), false);
					}

					members.open(user);
				});
		gui.setItem(22, Material.BLAZE_ROD, "&bBounty", new ArrayList<>(
				List.of(String.format("&e%s%s", SettingAddon.getMoneySymbol(),
				                      SettingAddon.formatDouble(gang.getBounty())))), true, false);
		// this item should take you to another gangs page
		gui.setItem(25, Material.REDSTONE, "&bAlias", null, false, false, (inventory, item) -> {
			inventory.close(user);

			int          size          = gang.getAlias().size();
			int          inventorySize = Math.min((int) Math.ceil((double) size / 9) * 9, InventoryGUI.MAX_SLOTS);
			InventoryGUI alias         = new InventoryGUI("&6&lGang alias", inventorySize == 0 ? 9 : inventorySize);

			// TODO add alias gang

			alias.open(user);
		});
		gui.setItem(31, Material.WRITABLE_BOOK, "&bCreated", new ArrayList<>(List.of("&e" + gang.getDateCreated())),
		            true, false);

		gui.fillInventory();

		gui.open(user);
	}

	private Argument gangCreate(UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                            RankManager rankManager) {
		Argument create = new Argument("create", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasGang()) {
				player.sendMessage(MessageAddon.PLAYER_IN_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".create");

		HashMap<User<Player>, AtomicReference<String>> createGangName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>         createGangTimer = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

			if (user.hasGang()) {
				player.sendMessage(MessageAddon.PLAYER_IN_GANG.toString());
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

			member.setGangJoinDate(Instant.now().toEpochMilli());
			gang.addMember(user, member, rankManager.get(SettingAddon.getGangRankTail()));
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
				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						database.table("data").insert(new String[]{
								"id", "name", "display_name", "color", "description", "balance", "bounty", "alias",
								"created"
						}, new Object[]{
								gang.getId(), gang.getName(), gang.getDisplayName(), gang.getColor(),
								gang.getDescription(), gang.getBalance(), gang.getBounty(), "", gang.getCreated()
						}, new int[]{
								Types.INTEGER, Types.CHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.DOUBLE,
								Types.DOUBLE, Types.LONGVARCHAR, Types.BIGINT
						});
						gangDatabase.updateMembersTable(member);
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
				player.sendMessage(MessageAddon.PLAYER_IN_GANG.toString());
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

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> sender.sendMessage(
					MessageAddon.GANG_CREATE_CONFIRM.toString().replace("%timer%", String.valueOf(time.getDuration()))),
			                                          null, time -> {
				confirmCreate.setConfirmed(false);
				createGangName.remove(user);
				createGangTimer.remove(sender);
			});

			timer.start();
			createGangTimer.put(sender, timer);
		});

		create.addSubArgument(createName);

		return create;
	}

	private Argument gangDelete(UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                            RankManager rankManager) {
		String[]                                       delArr          = {"delete", "remove"};
		HashMap<User<Player>, AtomicReference<String>> deleteGangName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>         deleteGangTimer = new HashMap<>();

		ConfirmArgument confirmDelete = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
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

			// need to get all the users, even if they are not online
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

						helper.runQueries(database -> userDatabase.updateAccountTable(gangUser));
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
							UUID   uuid = UUID.fromString(String.valueOf(data[0]));
							Member mem  = memberManager.getMember(uuid);

							double balance = (double) data[1];
							double freq    = mem.getContribution();
							double gangBal = gang.getBalance();
							double amount  = Math.round(total) == 0 ? 0 : freq / total * gangBal;

							gang.setBalance(gangBal - amount);

							database.table("account").update("uuid = ?", new Object[]{uuid.toString()},
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
						userDatabase.updateAccountTable(user);
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

		Argument delete = new Argument(delArr, getArgumentTree(), (argument, sender, args) -> {
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
		}, getPermission() + ".delete");

		delete.addSubArgument(confirmDelete);

		return delete;
	}

	private Argument gangEconomyAmount(UserManager<Player> userManager, MemberManager memberManager,
	                                   GangManager gangManager) {
		return new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

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

				List<User<Player>> gangOnlineMembers = gang.getOnlineMembers(userManager);

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
						member.increaseContribution(contribution);
						for (User<Player> gangUser : gangOnlineMembers) {
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
						member.decreaseContribution(contribution);
						for (User<Player> gangUser : gangOnlineMembers) {
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

						helper.runQueries(database -> gangDatabase.updateMembersTable(member));
					}
				}
			} catch (NumberFormatException exception) {
				player.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", args[2]));
			}
		});
	}

	private Argument gangRankStatus(UserManager<Player> userManager, MemberManager memberManager,
	                                GangManager gangManager, RankManager rankManager) {
		return new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player     = (Player) sender;
			User<Player> user       = userManager.getUser(player);
			Member       userMember = memberManager.getMember(player.getUniqueId());

			String forceRank = "gangland.command.gang.force_rank";
			if (Bukkit.getPluginManager().getPermission(forceRank) == null) {
				Permission permission = new Permission(forceRank);
				Bukkit.getPluginManager().addPermission(permission);
			}

			boolean force = player.hasPermission(forceRank);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			String targetStr    = args[2];
			Member targetMember = null;
			for (Member member : gang.getGroup()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());

				if (!Objects.requireNonNull(offlinePlayer.getName()).equalsIgnoreCase(targetStr)) continue;

				targetMember = member;
				break;
			}

			if (targetMember == null) {
				player.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			// change the user rank by proceeding to the next node
			Rank currentRank = targetMember.getRank();
			switch (args[1].toLowerCase()) {
				// in the case there are more than one child then give options to the promoter
				case "promote" -> {
					if (!force)
						// cannot promote more than your rank
						if (userMember.getRank().equals(targetMember.getRank())) {
							player.sendMessage(MessageAddon.GANG_SAME_RANK_ACTION.toString());
							return;
						}

					// navigate the ranks first
					List<Rank> nextRanks = rankManager.getRankTree()
					                                  .find(currentRank)
					                                  .getNode()
					                                  .getChildren()
					                                  .stream()
					                                  .map(Node::getData)
					                                  .toList();

					if (nextRanks.isEmpty()) {
						player.sendMessage(MessageAddon.GANG_PROMOTE_END.toString());
						return;
					}

					if (nextRanks.size() > 1) {
						ComponentBuilder ranks = new ComponentBuilder();

						for (int i = 0; i < nextRanks.size(); i++) {
							String rank = nextRanks.get(i).getName();

							ComponentBuilder sep = new ComponentBuilder(rank).event(
									new ClickEvent(ClickEvent.Action.RUN_COMMAND,
									               "/glw option gang rank " + targetStr + " " + rank));

							ranks.append(sep.create());

							if (i < nextRanks.size() - 1) ranks.append("  ");
						}
					} else {
						OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
						if (offlinePlayer.isOnline()) Objects.requireNonNull(offlinePlayer.getPlayer()).sendMessage(
								MessageAddon.GANG_PROMOTE_TARGET_SUCCESS.toString()
								                                        .replace("%rank%", nextRanks.get(0).getName()));
						player.sendMessage(MessageAddon.GANG_PROMOTE_PLAYER_SUCCESS.toString()
						                                                           .replace("%player%", targetStr)
						                                                           .replace("%rank%", nextRanks.get(0)
						                                                                                       .getName()));

						targetMember.setRank(nextRanks.get(0));
					}
				}

				case "demote" -> {
					// cannot demote higher rank
					Node<Rank> playerRank = userMember.getRank().getNode();
					Node<Rank> targetRank = targetMember.getRank().getNode();

					if (!force)
						// [player : Owner (descendant), target : Member (ancestor)] (Inverse)
						if (!rankManager.getRankTree().isDescendant(targetRank, playerRank)) {
							player.sendMessage(MessageAddon.GANG_HIGHER_RANK_ACTION.toString());
							return;
						}

					Node<Rank> previousRankNode = currentRank.getNode().getParent();

					if (previousRankNode == null) {
						player.sendMessage(MessageAddon.GANG_DEMOTE_END.toString());
						return;
					}

					Rank previousRank = previousRankNode.getData();

					OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
					if (offlinePlayer.isOnline()) Objects.requireNonNull(offlinePlayer.getPlayer()).sendMessage(
							MessageAddon.GANG_DEMOTE_TARGET_SUCCESS.toString()
							                                       .replace("%rank%", previousRank.getName()));
					player.sendMessage(MessageAddon.GANG_DEMOTE_PLAYER_SUCCESS.toString()
					                                                          .replace("%player%", targetStr)
					                                                          .replace("%rank%",
					                                                                   previousRank.getName()));
					targetMember.setRank(previousRank);
				}
			}

			// update database
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					Member finalTargetMember = targetMember;
					helper.runQueries(database -> gangDatabase.updateMembersTable(finalTargetMember));
					break;
				}
		});
	}

	private Argument gangKick(UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                          RankManager rankManager) {
		return new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player     = (Player) sender;
			User<Player> user       = userManager.getUser(player);
			Member       userMember = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			String targetStr    = args[2];
			Member targetMember = null;
			for (Member member : gang.getGroup()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());

				if (!Objects.requireNonNull(offlinePlayer.getName()).equalsIgnoreCase(targetStr)) continue;

				targetMember = member;
				break;
			}

			if (targetMember == null) {
				player.sendMessage(MessageAddon.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			Node<Rank> playerRank = userMember.getRank().getNode();
			Node<Rank> targetRank = targetMember.getRank().getNode();

			if (!rankManager.getRankTree().isDescendant(targetRank, playerRank)) {
				player.sendMessage(MessageAddon.GANG_HIGHER_RANK_ACTION.toString());
				return;
			}

			User<Player>  onlineTarget;
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
			if (offlinePlayer.isOnline()) onlineTarget = userManager.getUser(offlinePlayer.getPlayer());
			else onlineTarget = null;

			if (onlineTarget != null) {
				gang.removeMember(onlineTarget, targetMember);
				onlineTarget.getUser().sendMessage(MessageAddon.KICKED_FROM_GANG.toString());
			} else
				// remove user gang data
				gang.removeMember(targetMember);

			// update the user account
			// update the gang data
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
				if (handler instanceof UserDatabase userDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					if (onlineTarget != null) helper.runQueries(
							database -> userDatabase.updateAccountTable(onlineTarget));
					else {
						UUID finalTargetUuid = targetMember.getUuid();
						helper.runQueries(database -> {
							database.table("account").update("uuid = ?", new Object[]{finalTargetUuid},
							                                 new int[]{Types.VARCHAR}, new String[]{"gang_id"},
							                                 new Object[]{-1}, new int[]{Types.INTEGER});
						});
					}
				}

				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					Member finalTargetMember = targetMember;
					helper.runQueries(database -> gangDatabase.updateMembersTable(finalTargetMember));
				}
			}

			player.sendMessage(MessageAddon.GANG_KICKED_TARGET.toString()
			                                                  .replace("%player%", Objects.requireNonNull(
					                                                  offlinePlayer.getName())));
		});
	}

	private Argument gangLeave(UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                           RankManager rankManager) {
		HashMap<User<Player>, CountdownTimer> leaveTimer = new HashMap<>();

		ConfirmArgument leaveConfirm = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			// need to check if they were the owner or not
			// if it was the owner, then they need to transfer the rank
			if (member.getRank().match(rankManager.get(SettingAddon.getGangRankTail()).getUsedId())) {
				player.sendMessage(MessageAddon.GANG_TRANSFER_OWNERSHIP.toString());
				return;
			}

			// if they were not the owner they can leave
			// anyone leaving will not get a piece of the pie, thus the contribution would not be counted
			gang.removeMember(user, member);
			player.sendMessage(MessageAddon.GANG_LEAVE.toString());

			// update to database
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases()) {
				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> gangDatabase.updateMembersTable(member));
				}

				if (handler instanceof UserDatabase userDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> userDatabase.updateAccountTable(user));
				}
			}

			CountdownTimer timer = leaveTimer.get(user);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				leaveTimer.remove(user);
			}
		});

		Argument leave = new Argument("leave", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			// need to check if they were the owner or not
			// if it was the owner, then they need to transfer the rank
			if (member.getRank().match(rankManager.get(SettingAddon.getGangRankTail()).getUsedId())) {
				player.sendMessage(MessageAddon.GANG_TRANSFER_OWNERSHIP.toString());
				return;
			}

			if (leaveConfirm.isConfirmed()) return;

			leaveConfirm.setConfirmed(true);

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> player.sendMessage(
					ChatUtil.confirmCommand(new String[]{"gang", "leave"})), null, time -> {
				leaveConfirm.setConfirmed(false);
				leaveTimer.remove(user);
			});

			timer.start();
			leaveTimer.put(user, timer);
		});

		leave.addSubArgument(leaveConfirm);

		return leave;
	}

	private Argument gangRename(UserManager<Player> userManager, GangManager gangManager) {
		Argument name = new Argument("name", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".rename");

		// glw gang name <name>
		Argument changeName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang   gang    = gangManager.getGang(user.getGangId());
			String oldName = gang.getName();
			String newName = args[2];

			if (!SettingAddon.isGangNameDuplicates()) for (Gang checkGangName : gangManager.getGangs().values())
				if (checkGangName.getName().equalsIgnoreCase(newName)) {
					player.sendMessage(MessageAddon.DUPLICATE_GANG_NAME.toString().replace("%gang%", newName));
					return;
				}

			gang.setName(newName);
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> gangDatabase.updateDataTable(gang));
					break;
				}

			for (User<Player> onlineMembers : gang.getOnlineMembers(userManager))
				onlineMembers.getUser().sendMessage(MessageAddon.GANG_RENAME.toString()
				                                                            .replace("%old_gang%", oldName)
				                                                            .replace("%gang%", gang.getName()));
		});

		name.addSubArgument(changeName);

		return name;
	}

}
