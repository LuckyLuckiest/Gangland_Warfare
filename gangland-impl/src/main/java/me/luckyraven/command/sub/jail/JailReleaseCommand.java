package me.luckyraven.command.sub.jail;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class JailReleaseCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	public JailReleaseCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "release", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		playerInfo();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<player>"));
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

			DetainmentService detainmentService = gangland.getInitializer().getDetainmentService();
			detainmentService.release(target);

			sender.sendMessage(ChatUtil.commandMessage("&aReleased &e" + target.getName() + "&a from jail."));
		});

		this.addSubArgument(playerInfo);
	}
}
