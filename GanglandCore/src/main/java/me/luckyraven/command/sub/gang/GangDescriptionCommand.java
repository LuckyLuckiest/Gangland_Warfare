package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.TriConsumer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
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

	protected GangDescriptionCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, new String[]{"desc", "description"}, tree, parent, "description");

		this.gangland = gangland;

		this.userManager = gangland.getInitializer().getUserManager();
		this.gangManager = gangland.getInitializer().getGangManager();
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
				Gang   gang1  = gangManager.getGang(userManager.getUser(stateSnapshot.getPlayer()).getGangId());
				String output = stateSnapshot.getText();
				String old    = gang1.getDescription();

				// no change
				if (output == null || output.isEmpty() || output.equals(old)) {
					stateSnapshot.getPlayer().sendMessage(MessageAddon.GANG_DESCRIPTION_NO_CHANGE.toString());
					return Collections.emptyList();
				}

				// change the gang description
				gang1.setDescription(output);

				stateSnapshot.getPlayer()
							 .sendMessage(MessageAddon.GANG_DESCRIPTION_CHANGE.toString()
																			  .replace("%old_desc%", old)
																			  .replace("%new_desc%", output));
				return List.of(AnvilGUI.ResponseAction.close());
			}).text(gang.getDescription()).title("Gang description").plugin(gangland).open(player);
		};
	}

}
