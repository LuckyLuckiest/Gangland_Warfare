package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.Inventory;
import me.luckyraven.bukkit.inventory.MultiInventory;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.database.sub.UserDatabase;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.rank.Rank;
import me.luckyraven.rank.RankManager;
import me.luckyraven.util.ChatUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GangCommand extends CommandHandler {

	private final Gangland gangland;

	public GangCommand(Gangland gangland) {
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
		Argument create = new GangCreateCommand(gangland, getArgumentTree(), userManager, memberManager, gangManager,
		                                        rankManager);

		// delete gang
		// glw gang delete
		Argument delete = new GangDeleteCommand(gangland, getArgumentTree(), userManager, memberManager, gangManager,
		                                        rankManager);

		// add user to gang
		// glw gang invite <name>
		GangInviteCommand addUser = new GangInviteCommand(gangland, getArgumentTree(), userManager, memberManager,
		                                                  gangManager, rankManager);

		// glw gang accept
		Argument acceptInvite = addUser.gangAccept();

		// remove user from gang
		// glw gang kick <name>
		Argument removeUser = new GangKickCommand(gangland, getArgumentTree(), userManager, memberManager, gangManager,
		                                          rankManager);

		// leave the gang
		// glw gang leave
		Argument leave = new GangLeaveCommand(gangland, getArgumentTree(), userManager, memberManager, gangManager,
		                                      rankManager);

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
		Argument name = new GangRenameCommand(gangland, getArgumentTree(), userManager, gangManager);

		// change gang description
		// opens an anvil with a paper that can change the title
		// glw gang desc
		Argument description = new GangDescriptionCommand(gangland, getArgumentTree(), userManager, gangManager);

		// gang ally
		// glw gang ally <request/abandon> <id>
		Argument ally = new GangAllyCommand(gangland, getArgumentTree(), userManager, memberManager, gangManager);

		// change gang display name
		// glw gang display <name>
		Argument display = gangDisplayName(userManager, gangManager);

		// change gang color using gui
		// glw gang color
		Argument color = new GangColorCommand(gangland, getArgumentTree(), userManager, gangManager);

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

		arguments.add(ally);

		arguments.add(display);
		arguments.add(color);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Gang");
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
					                                  .map(Tree.Node::getData)
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
					Tree.Node<Rank> playerRank = userMember.getRank().getNode();
					Tree.Node<Rank> targetRank = targetMember.getRank().getNode();

					if (!force)
						// [player : Owner (descendant), target : Member (ancestor)] (Inverse)
						if (!rankManager.getRankTree().isDescendant(targetRank, playerRank)) {
							player.sendMessage(MessageAddon.GANG_HIGHER_RANK_ACTION.toString());
							return;
						}

					Tree.Node<Rank> previousRankNode = currentRank.getNode().getParent();

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

	private Argument gangDisplayName(UserManager<Player> userManager, GangManager gangManager) {
		Argument display = new Argument("display", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".display_name");

		Argument displayName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			String displayNameStr = args[2];
			Gang   gang           = gangManager.getGang(user.getGangId());

			gang.setDisplayName(displayNameStr);
			player.sendMessage(MessageAddon.GANG_DISPLAY_SET.toString().replace("%display%", displayNameStr));

			// update database
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> gangDatabase.updateDataTable(gang));
					break;
				}
		});

		// glw gang display remove
		Argument removeDisplay = new Argument("remove", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				player.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			gang.setDisplayName("");
			player.sendMessage(MessageAddon.GANG_DISPLAY_REMOVED.toString());

			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> gangDatabase.updateDataTable(gang));
					break;
				}
		});

		display.addSubArgument(removeDisplay);
		display.addSubArgument(displayName);

		return display;
	}

	private void gangStat(User<Player> user, UserManager<Player> userManager, Gang gang) {
		Inventory gui = new Inventory(gangland, "&6&l" + gang.getDisplayNameString() + "&r gang", 5 * 9);

		gui.setItem(11, Material.GOLD_BLOCK, "&bBalance", new ArrayList<>(
				List.of(String.format("&e%s%s", SettingAddon.getMoneySymbol(),
				                      SettingAddon.formatDouble(gang.getBalance())))), true, false);

		gui.setItem(13, Material.CRAFTING_TABLE, "&bID", new ArrayList<>(List.of("&e" + gang.getId())), false, false);

		gui.setItem(15, Material.PAPER, "&bDescription", new ArrayList<>(List.of("&e" + gang.getDescription())), false,
		            false);

		gui.setItem(19, Material.PLAYER_HEAD, "&bMembers", new ArrayList<>(
				            List.of("&a" + gang.getOnlineMembers(userManager).size() + "&7/&e" + gang.getGroup().size())), false,
		            false, (inventory, item) -> {
					inventory.close(user.getUser());

					List<ItemStack> items = new ArrayList<>();
					for (Member member : gang.getGroup()) {
						OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
						Rank          userRank      = member.getRank();

						List<String> data = new ArrayList<>();
						data.add("&7rank:&e " + userRank.getName());
						data.add("&7Contribution:&e " + member.getContribution());
						data.add("&7Joined:&e " + member.gangJoinDate());

						ItemBuilder itemBuilder = new ItemBuilder(Material.PLAYER_HEAD).setDisplayName(
								"&b" + offlinePlayer.getName()).setLore(data);

						itemBuilder.modifyNBT(nbt -> nbt.setString("SkullOwner", offlinePlayer.getName()));

						items.add(itemBuilder.build());
					}

					MultiInventory multi = MultiInventory.dynamicMultiInventory(gangland, items, "&6&lGang Members",
					                                                            user.getUser());

					multi.open(user.getUser());
				});

		gui.setItem(22, Material.BLAZE_ROD, "&bBounty", new ArrayList<>(
				List.of(String.format("&e%s%s", SettingAddon.getMoneySymbol(),
				                      SettingAddon.formatDouble(gang.getBounty())))), true, false);

		gui.setItem(25, Material.REDSTONE, "&bAlly", List.of("&e" + gang.getAlly().size()), false, false,
		            (inventory, item) -> {
			            inventory.close(user.getUser());

			            List<ItemStack> items = new ArrayList<>();
			            for (Gang ally : gang.getAlly()) {
				            List<String> data = new ArrayList<>();
				            data.add("&7ID:&e " + ally.getId());
				            data.add(String.format("&7Members:&a %d&7/&e%d", ally.getOnlineMembers(userManager).size(),
				                                   ally.getGroup().size()));
				            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
				            data.add("&7Created:&e " + sdf.format(ally.getDateCreated()));

				            ItemBuilder itemBuilder = new ItemBuilder(Material.REDSTONE).setDisplayName(
						            "&b" + ally.getDisplayNameString()).setLore(data);

				            items.add(itemBuilder.build());
			            }

			            MultiInventory multi = MultiInventory.dynamicMultiInventory(gangland, items, "&6&lGang Allies",
			                                                                        user.getUser());

			            multi.open(user.getUser());
		            });
		gui.setItem(31, Material.WRITABLE_BOOK, "&bCreated", new ArrayList<>(List.of("&e" + gang.getDateCreated())),
		            true, false);

		gui.fillInventory();

		gui.open(user.getUser());
	}

}
