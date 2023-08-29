package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.ConfirmArgument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.RankDatabase;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TimeUtil;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.command.CommandSender;

import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class RankCommand extends CommandHandler {

	public RankCommand(Gangland gangland) {
		super(gangland, "rank", false);

		List<CommandInformation> list = getCommands().entrySet()
		                                             .stream()
		                                             .filter(entry -> entry.getKey().startsWith("rank"))
		                                             .sorted(Map.Entry.comparingByKey())
		                                             .map(Map.Entry::getValue)
		                                             .toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		help(commandSender, 1);
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		RankManager rankManager = gangland.getInitializer().getRankManager();

		// glw rank create <name>
		Argument create = new Argument("create", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".create");

		HashMap<CommandSender, AtomicReference<String>> createRankName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>          createRankTimer = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Rank rank = new Rank(createRankName.get(sender).get());

			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof RankDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						String permissions = database.createList(rank.getPermissions());

						Database config = database.table("data");
						config.insert(config.getColumns().toArray(String[]::new),
						              new Object[]{rank.getUsedId(), rank.getName(), permissions, ""},
						              new int[]{Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR});
					});

					break;
				}

			sender.sendMessage(MessageAddon.RANK_CREATED.toString().replace("%rank%", rank.getName()));
			rankManager.add(rank);
			createRankName.remove(sender);

			CountdownTimer timer = createRankTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				createRankTimer.remove(sender);
			}
		});

		create.addSubArgument(confirmCreate);

		Argument createName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank != null) {
				sender.sendMessage(MessageAddon.RANK_EXIST.toString());
				return;
			}

			if (confirmCreate.isConfirmed()) return;

			sender.sendMessage(ChatUtil.confirmCommand(new String[]{"rank", "create"}));
			confirmCreate.setConfirmed(true);
			createRankName.put(sender, new AtomicReference<>(args[2]));

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				sender.sendMessage(MessageAddon.RANK_CREATE_CONFIRM.toString()
				                                                   .replace("%timer%",
				                                                            TimeUtil.formatTime(time.getPeriod(),
				                                                                                true)));
			}, null, time -> {
				confirmCreate.setConfirmed(false);
				createRankName.remove(sender);
			});

			timer.start();
			createRankTimer.put(sender, timer);
		});

		create.addSubArgument(createName);

		// glw rank delete <name>
		String[] delArr = new String[]{"delete", "remove"};
		Argument delete = new Argument(delArr, getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".delete");

		HashMap<CommandSender, AtomicReference<String>> deleteRankName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>          deleteRankTimer = new HashMap<>();

		ConfirmArgument confirmDelete = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Rank rank = rankManager.get(deleteRankName.get(sender).get());

			if (rank != null) {
				for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
					if (handler instanceof RankDatabase rankDatabase) {
						DatabaseHelper helper = new DatabaseHelper(gangland, handler);

						helper.runQueries(database -> {
							rankManager.remove(rank);
							database.table("data").delete("id", String.valueOf(rank.getUsedId()));
						});

						// important to refactor the ids, so they are in the correct id order
						sender.sendMessage(ChatUtil.informationMessage("Refactoring IDs..."));
						rankManager.refactorIds(rankDatabase);
						sender.sendMessage(ChatUtil.informationMessage("Refactoring done"));
						break;
					}

				sender.sendMessage(MessageAddon.RANK_REMOVED.toString().replace("%rank%", rank.getName()));
				deleteRankName.remove(sender);

				CountdownTimer timer = deleteRankTimer.get(sender);
				if (timer != null) {
					if (!timer.isCancelled()) timer.cancel();
					deleteRankTimer.remove(sender);
				}
			}
		});

		delete.addSubArgument(confirmDelete);

		Argument deleteName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK.toString());
				return;
			}

			if (confirmDelete.isConfirmed()) return;

			sender.sendMessage(ChatUtil.confirmCommand(new String[]{"rank", "delete"}));
			confirmDelete.setConfirmed(true);
			deleteRankName.put(sender, new AtomicReference<>(args[2]));

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				sender.sendMessage(MessageAddon.RANK_REMOVE_CONFIRM.toString()
				                                                   .replace("%timer%",
				                                                            TimeUtil.formatTime(time.getPeriod(),
				                                                                                true)));
			}, null, time -> {
				confirmDelete.setConfirmed(false);
				deleteRankName.remove(sender);
			});

			timer.start();
			deleteRankTimer.put(sender, timer);
		});

		delete.addSubArgument(deleteName);

		// glw rank list
		Argument list = new Argument("list", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(MessageAddon.RANK_LIST_PRIMARY.toString());

			StringBuilder builder = new StringBuilder();
			List<Rank>    ranks   = rankManager.getRanks().values().stream().toList();

			for (int i = 0; i < ranks.size(); i++) {
				builder.append(ranks.get(i).getName());
				if (i < ranks.size() - 1) builder.append(", ");
			}

			sender.sendMessage(MessageAddon.RANK_LIST_SECONDARY.toString().replace("%ranks%", builder.toString()));
		}, getPermission() + ".list");

		// glw rank permission <add/remove> <name> <permission>
		String[] permArr = {"permission", "perm"};
		Argument permission = new Argument(permArr, getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<add/remove>"));
		}, getPermission() + ".permission");

		Argument perm = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			// check if rank exists
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK.toString());
				return;
			}

			// get the list
			String permString = args[4];
			String message    = "";
			switch (args[2].toLowerCase()) {
				case "add" -> {
					if (rank.contains("")) rank.removePermission("");
					rank.addPermission(permString);
					message = MessageAddon.RANK_PERMISSION_ADD.toString().replace("%rank%", rank.getName()).replace(
							"%permission%", permString);
				}
				case "remove" -> {
					if (!rank.contains(permString)) {
						sender.sendMessage(MessageAddon.INVALID_RANK_PERMISSION.toString());
						return;
					}
					rank.removePermission(permString);
					message = MessageAddon.RANK_PERMISSION_REMOVE.toString().replace("%rank%", rank.getName()).replace(
							"%permission%", permString);
				}
			}

			sender.sendMessage(message);
		});

		Argument permName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<permission>"));
		});

		permName.addSubArgument(perm);

		Argument addPerm = new Argument("add", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, permission.getPermission() + ".add");

		Argument removePerm = new Argument("remove", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, permission.getPermission() + ".remove");

		addPerm.addSubArgument(permName);
		removePerm.addSubArgument(permName);

		permission.addSubArgument(addPerm);
		permission.addSubArgument(removePerm);

		// glw rank info <name>
		Argument info = new Argument("info", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, getPermission() + ".info");

		Argument infoName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK.toString());
				return;
			}

			StringBuilder permBuilder = new StringBuilder();
			for (int i = 0; i < rank.getPermissions().size(); i++) {
				permBuilder.append(rank.getPermissions().get(i));
				if (i < rank.getPermissions().size() - 1) permBuilder.append(", ");
			}

			StringBuilder parentBuilder = new StringBuilder();
			for (int i = 0; i < rank.getNode().getChildren().size(); i++) {
				parentBuilder.append(rank.getNode().getChildren().get(i).getData().getName());
				if (i < rank.getNode().getChildren().size() - 1) parentBuilder.append(", ");
			}

			sender.sendMessage(MessageAddon.RANK_INFO_PRIMARY.toString()
			                                                 .replace("%rank%", rank.getName())
			                                                 .replace("%id%", String.valueOf(rank.getUsedId()))
			                                                 .replace("%parent%", parentBuilder.toString()));
			sender.sendMessage(
					MessageAddon.RANK_INFO_SECONDARY.toString().replace("%permissions%", permBuilder.toString()));
		});

		info.addSubArgument(infoName);

		// glw rank parent <add/remove> <name> <parent>
		Argument parent = new Argument("parent", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<add/remove>"));
		}, getPermission() + ".parent");

		Argument parentStr = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK.toString());
				return;
			}

			Rank childRank = rankManager.get(args[4]);

			if (childRank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK_PARENT.toString());
				return;
			}

			switch (args[2].toLowerCase()) {
				case "add" -> {
					if (rank.getNode().getChildren().contains(childRank.getNode())) {
						sender.sendMessage(MessageAddon.RANK_EXIST.toString());
						return;
					}
					rank.getNode().add(childRank.getNode());
					sender.sendMessage(MessageAddon.RANK_PARENT_ADD.toString()
					                                               .replace("%parent%", childRank.getName())
					                                               .replace("%rank%", rank.getName()));
				}

				case "remove" -> {
					if (!rank.getNode().getChildren().contains(childRank.getNode())) {
						sender.sendMessage(MessageAddon.INVALID_RANK_PARENT.toString());
						return;
					}
					rank.getNode().remove(childRank.getNode());
					sender.sendMessage(MessageAddon.RANK_PARENT_REMOVE.toString()
					                                                  .replace("%parent%", childRank.getName())
					                                                  .replace("%rank%", rank.getName()));
				}
			}
		});

		Argument parentName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<parent>"));
		});

		parentName.addSubArgument(parentStr);

		Argument addParent = new Argument("add", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, parent.getPermission() + ".add");

		Argument removeParent = new Argument("remove", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, parent.getPermission() + ".remove");

		parent.addSubArgument(addParent);
		parent.addSubArgument(removeParent);

		addParent.addSubArgument(parentName);
		removeParent.addSubArgument(parentName);

		// glw rank traverse
		Argument traverseTree = new Argument("traverse", getArgumentTree(), (argument, sender, args) -> {
			StringBuilder builder = new StringBuilder();
			List<Rank>    ranks   = rankManager.getRankTree().getAllNodes().stream().map(Tree.Node::getData).toList();

			for (int i = 0; i < ranks.size(); i++) {
				builder.append(ranks.get(i).getName());
				if (i < ranks.size() - 1) builder.append(" -> ");
			}

			sender.sendMessage(builder.toString());
		}, getPermission() + ".traverse");

		getArgument().addSubArgument(traverseTree);

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);
		arguments.add(list);
		arguments.add(permission);
		arguments.add(info);
		arguments.add(parent);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Rank");
	}

}
