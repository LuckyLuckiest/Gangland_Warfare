package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

class CivilianSpawnGroupCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	CivilianSpawnGroupCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "spawngroup", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		groupIdArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<groupId>"));
		};
	}

	private void groupIdArgument() {
		Argument groupArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}

			String          groupId = args[2];
			CivilianService service = gangland.getInitializer().getCivilianService();

			if (!service.getMarkerConfig().groups().containsKey(groupId)) {
				sender.sendMessage(GanglandChatUtil.commandMessage("&cUnknown group &e" + groupId + "&c."));
				return;
			}

			service.spawnGroup(player.getLocation(), groupId);

			sender.sendMessage(
					GanglandChatUtil.commandMessage("&aCivilian group &e" + groupId + "&a spawned at your location."));
		}, sender -> {
			CivilianService service = gangland.getInitializer().getCivilianService();
			return new ArrayList<>(service.getMarkerConfig().groups().keySet());
		});

		this.addSubArgument(groupArg);
	}
}
