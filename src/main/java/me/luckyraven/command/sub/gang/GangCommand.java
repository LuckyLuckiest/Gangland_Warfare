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
import me.luckyraven.command.argument.ConfirmArgument;
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
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

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
				target.sendMessage(
						MessageAddon.GANG_INVITE_TARGET.toString().replace("%gang%", gang.getDisplayNameString()));
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
			sender.sendMessage(
					MessageAddon.GANG_INVITE_ACCEPT.toString().replace("%gang%", gang.getDisplayNameString()));

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
		}, getPermission() + ".accept");

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
			// TODO work on Anvil GUI
		}, getPermission() + ".change_description");

		// gang ally
		// glw gang ally <request/abandon> <id>
		Argument ally = gangAlly(userManager, memberManager, gangManager);

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

			Tree.Node<Rank> playerRank = userMember.getRank().getNode();
			Tree.Node<Rank> targetRank = targetMember.getRank().getNode();

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

	private Argument gangAlly(UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager) {
		Argument ally = new Argument("ally", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(
					CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<request/abandon>"));
		}, getPermission() + ".ally");

		// glw gang ally request <id>
		Argument requestAlly = new Argument("request", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<id>"));
		}, ally.getPermission() + ".request");

		// glw gang ally abandon <id>
		Argument abandonAlly = new Argument("abandon", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<id>"));
		}, ally.getPermission() + ".abandon");

		// key -> the gang requesting alliance with, value -> the gang sending the request
		HashMap<Gang, Gang>           gangsIdMap       = new HashMap<>();
		HashMap<Gang, CountdownTimer> gangRequestTimer = new HashMap<>();
		Argument allyId = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			String value = args[3];
			int    id;
			try {
				id = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				player.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			// should not be null
			Gang sending = gangManager.getGang(user.getGangId());
			// can be null
			Gang receiving = gangManager.getGang(id);

			if (receiving == null) {
				player.sendMessage(MessageAddon.GANG_DOESNT_EXIST.toString());
				return;
			}

			switch (args[2].toLowerCase()) {
				case "request" -> {
					// check if they are allied before proceeding
					if (receiving.getAlly().contains(sending)) {
						player.sendMessage(MessageAddon.ALREADY_ALLIED_GANG.toString());
						return;
					}

					if (gangsIdMap.containsKey(receiving)) {
						player.sendMessage(MessageAddon.GANG_ALLIANCE_ALREADY_SENT.toString());
						return;
					}

					// send a message to every member in the sending gang
					Bukkit.getOnlinePlayers().stream().filter(onlinePlayer -> memberManager.getMember(
							onlinePlayer.getUniqueId()).getGangId() == sending.getId()).toList().forEach(
							pl -> pl.sendMessage(MessageAddon.GANG_ALLY_SEND_REQUEST.toString()
							                                                        .replace("%gang%",
							                                                                 receiving.getDisplayNameString())));

					// send a message to every member in receiving gang
					Bukkit.getOnlinePlayers().stream().filter(onlinePlayer -> memberManager.getMember(
							onlinePlayer.getUniqueId()).getGangId() == receiving.getId()).toList().forEach(
							pl -> pl.sendMessage(MessageAddon.GANG_ALLY_RECEIVE_REQUEST.toString()
							                                                           .replace("%gang%",
							                                                                    sending.getDisplayNameString())));

					gangsIdMap.put(receiving, sending);

					CountdownTimer timer = new CountdownTimer(gangland, 60, null, null, time -> {
						gangsIdMap.remove(receiving);
						gangRequestTimer.remove(receiving);
					});

					timer.start();
					gangRequestTimer.put(receiving, timer);
				}

				case "abandon" -> {
					// send a message to every member in the sending gang
					Bukkit.getOnlinePlayers().stream().filter(onlinePlayer -> memberManager.getMember(
							onlinePlayer.getUniqueId()).getGangId() == sending.getId()).toList().forEach(
							pl -> pl.sendMessage(MessageAddon.GANG_ALLY_ABANDON.toString()
							                                                   .replace("%gang%",
							                                                            receiving.getDisplayNameString())));

					// send a message to every member in receiving gang
					Bukkit.getOnlinePlayers().stream().filter(onlinePlayer -> memberManager.getMember(
							onlinePlayer.getUniqueId()).getGangId() == receiving.getId()).toList().forEach(
							pl -> pl.sendMessage(MessageAddon.GANG_ALLY_ABANDON.toString()
							                                                   .replace("%gang%",
							                                                            sending.getDisplayNameString())));

					sending.getAlly().remove(receiving);
					receiving.getAlly().remove(sending);

					for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
						if (handler instanceof GangDatabase gangDatabase) {
							DatabaseHelper helper = new DatabaseHelper(gangland, handler);

							helper.runQueries(database -> {
								gangDatabase.updateDataTable(sending);
								gangDatabase.updateDataTable(receiving);
							});
							break;
						}
				}
			}

		});

		requestAlly.addSubArgument(allyId);
		abandonAlly.addSubArgument(allyId);

		// glw gang ally accept
		Argument allyAccept = new Argument("accept", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang userGang = gangManager.getGang(user.getGangId());

			// finds the key if it was similar to acceptor gang
			Gang receiving = gangsIdMap.keySet().stream().filter(gang -> gang == userGang).findFirst().orElse(null);

			if (receiving == null) {
				player.sendMessage(MessageAddon.NO_GANG_INVITATION.toString());
				return;
			}

			Gang sending = gangsIdMap.get(receiving);

			// check if they are allied before proceeding
			if (receiving.getAlly().contains(sending)) {
				player.sendMessage(MessageAddon.ALREADY_ALLIED_GANG.toString());
				return;
			}

			// add both ally
			receiving.getAlly().add(sending);
			sending.getAlly().add(receiving);

			// send a message to every member in the sending gang
			Bukkit.getOnlinePlayers().stream().filter(onlinePlayer -> memberManager.getMember(
					onlinePlayer.getUniqueId()).getGangId() == sending.getId()).toList().forEach(pl -> pl.sendMessage(
					MessageAddon.GANG_ALLY_ACCEPT.toString().replace("%gang%", receiving.getDisplayNameString())));

			// send a message to every member in receiving gang
			Bukkit.getOnlinePlayers().stream().filter(onlinePlayer -> memberManager.getMember(
					onlinePlayer.getUniqueId()).getGangId() == receiving.getId()).toList().forEach(pl -> pl.sendMessage(
					MessageAddon.GANG_ALLY_ACCEPT.toString().replace("%gang%", sending.getDisplayNameString())));

			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof GangDatabase gangDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						gangDatabase.updateDataTable(receiving);
						gangDatabase.updateDataTable(sending);
					});
					break;
				}

			gangsIdMap.remove(receiving);

			CountdownTimer timer = gangRequestTimer.get(receiving);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				gangRequestTimer.remove(receiving);
			}
		}, ally.getPermission() + ".accept");

		Argument allyReject = new Argument("reject", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang userGang = gangManager.getGang(user.getGangId());

			// finds the key if it was similar to acceptor gang
			Gang receiving = gangsIdMap.keySet().stream().filter(gang -> gang == userGang).findFirst().orElse(null);

			if (receiving == null) {
				player.sendMessage(MessageAddon.NO_GANG_INVITATION.toString());
				return;
			}

			Gang sending = gangsIdMap.get(receiving);

			// check if they are allied before proceeding
			if (receiving.getAlly().contains(sending)) {
				player.sendMessage(MessageAddon.ALREADY_ALLIED_GANG.toString());
				return;
			}

			// send a message to every member in the sending gang
			Bukkit.getOnlinePlayers().stream().filter(onlinePlayer -> memberManager.getMember(
					onlinePlayer.getUniqueId()).getGangId() == sending.getId()).toList().forEach(pl -> pl.sendMessage(
					MessageAddon.GANG_ALLY_REJECT.toString().replace("%gang%", receiving.getDisplayNameString())));

			// send a message to every member in receiving gang
			Bukkit.getOnlinePlayers().stream().filter(onlinePlayer -> memberManager.getMember(
					onlinePlayer.getUniqueId()).getGangId() == receiving.getId()).toList().forEach(pl -> pl.sendMessage(
					MessageAddon.GANG_ALLY_REJECT.toString().replace("%gang%", sending.getDisplayNameString())));

			gangsIdMap.remove(receiving);

			CountdownTimer timer = gangRequestTimer.get(receiving);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				gangRequestTimer.remove(receiving);
			}
		}, ally.getPermission() + ".reject");

		ally.addSubArgument(requestAlly);
		ally.addSubArgument(abandonAlly);
		ally.addSubArgument(allyAccept);
		ally.addSubArgument(allyReject);

		return ally;
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
		// TODO this item should take you to another gui page
		gui.setItem(19, Material.PLAYER_HEAD, "&bMembers", new ArrayList<>(
				            List.of("&a" + gang.getOnlineMembers(userManager).size() + "&7/&e" + gang.getGroup().size())), false,
		            false, (inventory, item) -> {
					inventory.close(user.getUser());

					int maxRows    = 4;
					int maxColumns = 7;
					int amount     = gang.getGroup().size();

					int perPage = maxColumns * maxRows;
					int pages   = (int) Math.ceil((double) amount / perPage);

					int finalPage   = amount + 9 * 2 + (int) Math.ceil((double) amount / maxColumns) * 2;
					int initialPage = pages == 1 ? finalPage : Inventory.MAX_SLOTS;

					int inventorySize = (int) Math.ceil((double) initialPage / 9) * 9;

					String         name  = "&6&lGang members&r (%d/%d)";
					// the first page
					MultiInventory multi = new MultiInventory(gangland, String.format(name, 1, pages), inventorySize);

					// need to fill the first page

					// the other pages

					// the inventory size of the other pages is determined according to which page is reached
					// it can be that the page reached is the final page which means the finalPage calculation is
					// applied to it

					// need to fill the other pages

					// the other pages
					for (int i = 0; i < pages - 1; i++) {
						Inventory inv = new Inventory(gangland, String.format(name, i + 2, pages), initialPage);

						for (int j = i; j < perPage + i; j++) {
							Member        member        = gang.getGroup().get(j);
							OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
							Rank          userRank      = member.getRank();

							List<String> data = new ArrayList<>();
							data.add("&7rank:&e " + userRank.getName());
							data.add("&7Contribution:&e " + member.getContribution());
							data.add("&7Joined:&e " + member.gangJoinDate());

							ItemBuilder itemBuilder = new ItemBuilder(Material.PLAYER_HEAD).setDisplayName(
									"&b" + offlinePlayer.getName()).setLore(data);

							itemBuilder.modifyNBT(nbt -> nbt.setString("SkullOwner", offlinePlayer.getName()));

//							inv.setItem();
						}

						multi.addPage(user.getUser(), inv);
					}

					Inventory members = new Inventory(gangland, "&6&lGang members",
					                                  inventorySize == 0 ? 9 : inventorySize);

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

					members.open(user.getUser());
				});
		gui.setItem(22, Material.BLAZE_ROD, "&bBounty", new ArrayList<>(
				List.of(String.format("&e%s%s", SettingAddon.getMoneySymbol(),
				                      SettingAddon.formatDouble(gang.getBounty())))), true, false);
		// TODO this item should take you to another gang page
		gui.setItem(25, Material.REDSTONE, "&bAlly", List.of("&e" + gang.getAlly().size()), false, false,
		            (inventory, item) -> {
			            inventory.close(user.getUser());

			            int size          = gang.getAlly().size();
			            int inventorySize = Math.min((int) Math.ceil((double) size / 9) * 9, Inventory.MAX_SLOTS);

			            Inventory ally = new Inventory(gangland, "&6&lGang ally",
			                                           inventorySize == 0 ? 9 : inventorySize);

			            int i = 0;
			            for (Gang allyGang : gang.getAlly()) {
				            if (i >= inventorySize) break;

				            List<String> data = new ArrayList<>();
				            data.add("&7ID:&e " + allyGang.getId());
				            data.add(String.format("&7Members:&a %d&7/&e%d",
				                                   allyGang.getOnlineMembers(userManager).size(),
				                                   allyGang.getGroup().size()));
				            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
				            data.add("&7Created:&e " + sdf.format(allyGang.getDateCreated()));

				            ItemBuilder itemBuilder = new ItemBuilder(Material.REDSTONE).setDisplayName(
						            "&b" + allyGang.getDisplayNameString()).setLore(data);

				            ally.setItem(i++, itemBuilder.build(), false);
			            }

			            ally.open(user.getUser());
		            });
		gui.setItem(31, Material.WRITABLE_BOOK, "&bCreated", new ArrayList<>(List.of("&e" + gang.getDateCreated())),
		            true, false);

		gui.fillInventory();

		gui.open(user.getUser());
	}

}
