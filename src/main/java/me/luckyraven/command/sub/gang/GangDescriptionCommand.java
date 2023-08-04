package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

class GangDescriptionCommand extends SubArgument {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	GangDescriptionCommand(Gangland gangland, Tree<Argument> tree, UserManager<Player> userManager,
	                       GangManager gangManager) {
		super(new String[]{"desc", "description"}, tree);

		setPermission(getPermission() + ".description");

		this.gangland = gangland;

		this.userManager = userManager;
		this.gangManager = gangManager;
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

			Gang gang = gangManager.getGang(user.getGangId());

			// display an anvil
			new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
				String output = stateSnapshot.getText();
				String old    = gang.getDescription();

				// no change
				if (output == null || output.isEmpty() || output.equals(old)) {
					stateSnapshot.getPlayer().sendMessage(MessageAddon.GANG_DESCRIPTION_NO_CHANGE.toString());
					return Collections.emptyList();
				}

				// change the gang description
				gang.setDescription(output);

				// update the database
				for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
					if (handler instanceof GangDatabase gangDatabase) {
						DatabaseHelper helper = new DatabaseHelper(gangland, handler);

						helper.runQueries(database -> gangDatabase.updateDataTable(gang));

						break;
					}

				stateSnapshot.getPlayer().sendMessage(MessageAddon.GANG_DESCRIPTION_CHANGE.toString()
				                                                                          .replace("%old_desc%", old)
				                                                                          .replace("%new_desc%",
				                                                                                   output));
				return List.of(AnvilGUI.ResponseAction.close());
			}).text(gang.getDescription()).title("Gang description").plugin(gangland).open(player);
		};
	}

}
