package me.luckyraven.command.sub.jail;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.detainment.release.ReleasePipeline;
import me.luckyraven.copsncrooks.detainment.release.ReleaseReason;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;

class JailReleaseCommand extends SubArgument {

	private final Gangland          gangland;
	private final Tree<Argument>    tree;
	private final DetainmentService detainmentService;
	private final ReleasePipeline   releasePipeline;

	protected JailReleaseCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                             DetainmentService detainmentService, ReleasePipeline releasePipeline) {
		super(gangland, "release", tree, parent);

		this.gangland          = gangland;
		this.tree              = tree;
		this.detainmentService = detainmentService;
		this.releasePipeline   = releasePipeline;

		playerInfo();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<player>"));
		};
	}

	private void playerInfo() {
		Argument playerInfo = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String playerStr = args[2];
			Player target    = Bukkit.getPlayer(playerStr);

			if (target == null) {
				sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", playerStr));
				return;
			}

			if (!detainmentService.isJailed(target)) {
				sender.sendMessage(Messages.JAIL_NOT_JAILED.toString().replace("%target%", target.getName()));
				return;
			}

			releasePipeline.release(target, ReleaseReason.ADMIN);

			sender.sendMessage(Messages.JAIL_RELEASED.toString().replace("%target%", target.getName()));
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
