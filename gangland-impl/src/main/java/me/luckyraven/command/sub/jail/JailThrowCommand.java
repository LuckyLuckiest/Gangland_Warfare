package me.luckyraven.command.sub.jail;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.detainment.DetainmentRegistry;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.jail.Jail;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;

class JailThrowCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	protected JailThrowCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "throw", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		throwPlayer();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {

		};
	}

	private void throwPlayer() {
		Argument playerThrow = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String playerStr = args[2];
			Player target    = Bukkit.getPlayer(playerStr);

			if (target == null) {
				sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", playerStr));
				return;
			}

			// find an empty jail and throw the player there
			DetainmentRegistry detainmentRegistry = gangland.getInitializer().getDetainmentRegistry();
			DetainmentService  detainmentService  = gangland.getInitializer().getDetainmentService();

			Jail jail = detainmentRegistry.findEmptyJail();

			if (jail == null) {
				sender.sendMessage(GanglandChatUtil.errorMessage("No empty jail found!"));
				return;
			}

			if (detainmentService.isJailed(target)) {
				sender.sendMessage(GanglandChatUtil.errorMessage("Player is already jailed!"));
				return;
			}

			detainmentService.jail(target, jail.getId());
			sender.sendMessage(GanglandChatUtil.commandMessage("&aThrown &e" + target.getName() + "&a to jail."));
		}, sender -> {
			Collection<? extends Player> onlinePlayers     = Bukkit.getOnlinePlayers();
			DetainmentService            detainmentService = gangland.getInitializer().getDetainmentService();

			return onlinePlayers.stream()
					.filter(player -> !detainmentService.isHandcuffed(player))
					.map(Player::getName)
					.toList();
		});

		this.addSubArgument(playerThrow);
	}
}
