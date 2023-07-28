package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.OptionalArgument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class GangRenameCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	GangRenameCommand(Gangland gangland, Tree<Argument> tree, UserManager<Player> userManager,
	                         GangManager gangManager) {
		super("rename", tree);

		setPermission(getPermission() + ".rename");

		this.gangland = gangland;
		this.tree = tree;

		this.userManager = userManager;
		this.gangManager = gangManager;

		gangRename();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(CommandManager.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void gangRename() {
		Argument changeName = new OptionalArgument(tree, (argument, sender, args) -> {
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

		this.addSubArgument(changeName);
	}

}
