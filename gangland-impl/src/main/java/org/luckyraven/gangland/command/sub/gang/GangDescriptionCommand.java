package org.luckyraven.gangland.command.sub.gang;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

import java.util.Collections;
import java.util.List;

class GangDescriptionCommand extends SubArgument {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	protected GangDescriptionCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                 UserManager<Player> userManager, GangManager gangManager) {
		super(gangland, new String[]{"desc", "description"}, tree, parent, "description");

		this.gangland    = gangland;
		this.userManager = userManager;
		this.gangManager = gangManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			// display an anvil
			new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
				User<Player> user1 = userManager.getUser(stateSnapshot.getPlayer());

				if (user1 == null) return Collections.emptyList();

				Gang   gang1  = gangManager.getGang(user1.getGangId());
				String output = stateSnapshot.getText();
				String old    = gang1.getDescription();

				// no change
				if (output == null || output.isEmpty() || output.equals(old)) {
					stateSnapshot.getPlayer().sendMessage(Messages.GANG_DESCRIPTION_NO_CHANGE.toString());
					return Collections.emptyList();
				}

				// change the gang description
				gang1.setDescription(output);

				stateSnapshot.getPlayer()
				             .sendMessage(Messages.GANG_DESCRIPTION_CHANGE.toString()
				                                                          .replace("%old_desc%", old)
				                                                          .replace("%new_desc%", output));
				return List.of(AnvilGUI.ResponseAction.close());
			}).text(gang.getDescription()).title("Gang description").plugin(gangland).open(player);
		};
	}

}
