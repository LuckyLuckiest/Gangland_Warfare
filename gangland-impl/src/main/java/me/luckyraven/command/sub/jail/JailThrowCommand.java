package me.luckyraven.command.sub.jail;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.detainment.DetainmentRegistry;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.detainment.intake.JailIntakeService;
import me.luckyraven.copsncrooks.jail.Jail;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;

class JailThrowCommand extends SubArgument {

	private final Gangland           gangland;
	private final Tree<Argument>     tree;
	private final DetainmentService  detainmentService;
	private final DetainmentRegistry detainmentRegistry;
	private final JailIntakeService  jailIntakeService;

	protected JailThrowCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                           DetainmentService detainmentService,
	                           DetainmentRegistry detainmentRegistry,
	                           JailIntakeService jailIntakeService) {
		super(gangland, "throw", tree, parent);

		this.gangland           = gangland;
		this.tree               = tree;
		this.detainmentService  = detainmentService;
		this.detainmentRegistry = detainmentRegistry;
		this.jailIntakeService  = jailIntakeService;

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
			Jail jail = detainmentRegistry.findEmptyJail();

			if (jail == null) {
				sender.sendMessage(Messages.JAIL_NO_EMPTY.toString());
				return;
			}

			if (detainmentService.isJailed(target)) {
				sender.sendMessage(Messages.JAIL_ALREADY_JAILED.toString());
				return;
			}

			boolean admitted = jailIntakeService.admit(target);
			if (!admitted) {
				sender.sendMessage(Messages.JAIL_NO_EMPTY.toString());
				return;
			}
			sender.sendMessage(Messages.JAIL_THROWN.toString().replace("%target%", target.getName()));
		}, sender -> {
			Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

			return onlinePlayers.stream()
					.filter(player -> !detainmentService.isHandcuffed(player))
					.map(Player::getName)
					.toList();
		});

		this.addSubArgument(playerThrow);
	}
}
