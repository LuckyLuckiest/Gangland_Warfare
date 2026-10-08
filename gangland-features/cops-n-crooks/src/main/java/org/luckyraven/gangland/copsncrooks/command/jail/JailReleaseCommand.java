package org.luckyraven.gangland.copsncrooks.command.jail;

import java.util.Map;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleaseReason;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.Collection;

class JailReleaseCommand extends SubArgument {

	private final JavaPlugin          plugin;
	private final Tree<Argument>    tree;
	private final DetainmentService detainmentService;
	private final ReleasePipeline   releasePipeline;
	private final CommandMessages commandMessages;

	protected JailReleaseCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                             DetainmentService detainmentService, ReleasePipeline releasePipeline, CommandMessages commandMessages) {
		super(plugin, "release", tree, parent);

		this.plugin          = plugin;
		this.tree              = tree;
		this.detainmentService = detainmentService;
		this.releasePipeline   = releasePipeline;
		this.commandMessages = commandMessages;

		playerInfo();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(commandMessages.usage("/glw jail release <player>"));
		};
	}

	private void playerInfo() {
		Argument playerInfo = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			String playerStr = args[2];
			Player target    = Bukkit.getPlayer(playerStr);

			if (target == null) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.PLAYER_NOT_FOUND, Map.of("player", playerStr)));
				return;
			}

			if (!detainmentService.isJailed(target)) {
				sender.sendMessage(commandMessages.format(CommandMessages.Key.JAIL_NOT_JAILED, Map.of("target", target.getName())));
				return;
			}

			releasePipeline.release(target, ReleaseReason.ADMIN);

			sender.sendMessage(commandMessages.format(CommandMessages.Key.JAIL_RELEASED, Map.of("target", target.getName())));
		}, sender -> {
			Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

			return onlinePlayers.stream()
					.filter(detainmentService::isJailed)
					.map(Player::getName)
					.toList();
		});

		this.addSubArgument(playerInfo);
	}
}
