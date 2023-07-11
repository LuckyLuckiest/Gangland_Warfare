package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.ConfirmArgument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.RankDatabase;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.rank.Rank;
import me.luckyraven.rank.RankManager;
import me.luckyraven.timer.CountdownTimer;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;

import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SCRank extends CommandHandler {

	public SCRank(Gangland gangland) {
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
			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<name>"));
		}, getPermission() + ".create");

		HashMap<CommandSender, AtomicReference<String>> createRankName = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Rank rank = new Rank(createRankName.get(sender).get());

			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof RankDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						String permissions = database.createList(rank.getPermissions());
						database.table("data").insert(new String[]{"id", "name", "permissions"},
						                              new Object[]{rank.getUsedId(), rank.getName(), permissions},
						                              new int[]{Types.INTEGER, Types.VARCHAR, Types.VARCHAR});
					});

					break;
				}

			sender.sendMessage(MessageAddon.RANK_CREATED.replace("%rank%", rank.getName()));
			rankManager.add(rank);
			createRankName.remove(sender);
		});

		create.addSubArgument(confirmCreate);

		Argument createName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank != null) {
				sender.sendMessage(MessageAddon.RANK_EXISTS);
				return;
			}

			if (confirmCreate.isConfirmed()) return;

			sender.sendMessage(ChatUtil.color(MessageAddon.confirmCommand(new String[]{"rank", "create"})));
			confirmCreate.setConfirmed(true);
			createRankName.put(sender, new AtomicReference<>(args[2]));

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				sender.sendMessage(
						MessageAddon.RANK_CREATE_CONFIRM.replace("%timer%", String.valueOf(time.getDuration())));
			}, null, time -> {
				confirmCreate.setConfirmed(false);
				createRankName.remove(sender);
			});

			timer.start();
		});

		create.addSubArgument(createName);

		// glw rank delete <name>
		String[] delArr = new String[]{"delete", "remove"};
		Argument delete = new Argument(delArr, getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<name>"));
		});

		HashMap<CommandSender, AtomicReference<String>> deleteRankName = new HashMap<>();

		ConfirmArgument confirmDelete = new ConfirmArgument(getArgumentTree(), (argument, sender, args) -> {
			Rank rank = rankManager.get(deleteRankName.get(sender).get());

			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof RankDatabase rankDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						database.table("data").delete("id", String.valueOf(rank.getUsedId()));
					});

					// important to refactor the ids, so they are in the correct id order
					rankManager.refactorIds(rankDatabase);
					break;
				}

			sender.sendMessage(MessageAddon.RANK_REMOVED.replace("%rank%", rank.getName()));
			rankManager.remove(rank);
			deleteRankName.remove(sender);
		});

		delete.addSubArgument(confirmDelete);

		Argument deleteName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK);
				return;
			}

			if (confirmDelete.isConfirmed()) return;

			sender.sendMessage(ChatUtil.color(MessageAddon.confirmCommand(new String[]{"rank", "delete"})));
			confirmDelete.setConfirmed(true);
			deleteRankName.put(sender, new AtomicReference<>(args[2]));

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				sender.sendMessage(
						MessageAddon.RANK_REMOVE_CONFIRM.replace("%timer%", String.valueOf(time.getDuration())));
			}, null, time -> {
				confirmDelete.setConfirmed(false);
				deleteRankName.remove(sender);
			});

			timer.start();
		});

		delete.addSubArgument(deleteName);

		// glw rank list
		Argument list = new Argument("list", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(MessageAddon.RANK_LIST_PRIMARY);

			StringBuilder builder = new StringBuilder();
			List<Rank>    ranks   = rankManager.getRanks().values().stream().toList();

			for (int i = 0; i < ranks.size(); i++) {
				builder.append(ranks.get(i).getName());
				if (i < ranks.size() - 1) builder.append(", ");
			}

			sender.sendMessage(MessageAddon.RANK_LIST_SECONDARY.replace("%ranks%", builder.toString()));
		});

		// glw rank permission <add/remove> <name> <permission>
		String[] permArr = {"permission", "perm"};
		Argument permission = new Argument(permArr, getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<add/remove>"));
		});

		Argument perm = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			// check if rank exists
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK);
				return;
			}

			// get the list
			String permString = args[4];
			String message    = "";
			switch (args[2].toLowerCase()) {
				case "add" -> {
					if (rank.contains("")) rank.removePermission("");
					rank.addPermission(permString);
					message = MessageAddon.RANK_PERMISSION_ADD.replace("%rank%", rank.getName()).replace("%permission%",
					                                                                                     permString);
				}
				case "remove" -> {
					if (!rank.contains(permString)) {
						sender.sendMessage(MessageAddon.INVALID_PERMISSION);
						return;
					}
					rank.removePermission(permString);
					message = MessageAddon.RANK_PERMISSION_REMOVE.replace("%rank%", rank.getName()).replace(
							"%permission%", permString);
				}
			}

			// compile it
			for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
				if (handler instanceof RankDatabase rankDatabase) {
					DatabaseHelper helper = new DatabaseHelper(gangland, handler);

					helper.runQueries(database -> {
						rankDatabase.updateDataTable(rank);
					});

					break;
				}

			sender.sendMessage(message);
		});

		Argument permName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<permission>"));
		});

		permName.addSubArgument(perm);

		Argument addPerm = new Argument("add", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<name>"));
		});

		Argument removePerm = new Argument("remove", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<name>"));
		});

		addPerm.addSubArgument(permName);
		removePerm.addSubArgument(permName);

		permission.addSubArgument(addPerm);
		permission.addSubArgument(removePerm);

		// glw rank info <name>
		Argument info = new Argument("info", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING, "<name>"));
		});

		Argument infoName = new OptionalArgument(getArgumentTree(), (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK);
				return;
			}

			StringBuilder builder = new StringBuilder();

			for (int i = 0; i < rank.getPermissions().size(); i++) {
				builder.append(rank.getPermissions().get(i));
				if (i < rank.getPermissions().size() - 1) builder.append(", ");
			}

			sender.sendMessage(MessageAddon.RANK_INFO_PRIMARY.replace("%rank%", rank.getName()));
			sender.sendMessage(MessageAddon.RANK_INFO_SECONDARY.replace("%permissions%", builder.toString()));
		});

		info.addSubArgument(infoName);

		getArgument().addSubArgument(create);
		getArgument().addSubArgument(delete);
		getArgument().addSubArgument(list);
		getArgument().addSubArgument(permission);
		getArgument().addSubArgument(info);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Rank");
	}

}
