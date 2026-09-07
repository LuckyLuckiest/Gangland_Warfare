package org.luckyraven.gangland.command.sub.jail;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

class JailInfoCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final JailRegistry   jailRegistry;

	protected JailInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent, JailRegistry jailRegistry) {
		super(gangland, "info", tree, parent);

		this.gangland     = gangland;
		this.tree         = tree;
		this.jailRegistry = jailRegistry;

		this.idArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	private void idArgument() {
		Argument idArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String idStr = args[2];
			int    id;
			try {
				id = Integer.parseInt(idStr);
			} catch (NumberFormatException e) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", idStr));
				return;
			}

			Jail jail = jailRegistry.getJail(id);

			if (jail == null) {
				sender.sendMessage(Messages.LOCATION_NOT_FOUND.toString().replace("%location%", idStr));
				return;
			}

			Location location = jail.getLocation();
			int      x        = location.getBlockX();
			int      y        = location.getBlockY();
			int      z        = location.getBlockZ();
			String   world    = location.getWorld() != null ? location.getWorld().getName() : "?";

			String tpCommand = String.format("/%s jail teleport %d", Gangland.SHORT_PREFIX, id);

			String color = GanglandChatUtil.color("&7&lJail &e(&b" + id + "&e)&7: ");
			var message = new ComponentBuilder(color).append(GanglandChatUtil.color("&e(&btp&e)"))
			                                         .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
			                                         .create();

			sender.spigot().sendMessage(message);

			String info = String.format(" &bX: &7%d\n &bY: &7%d\n &bZ: &7%d\n &bWorld: &7%s\n &bCapacity: &7%d/%d", x,
			                            y, z, world, jail.getJailedPlayersId().size(), jail.getMaxCapacity());

			sender.sendMessage(GanglandChatUtil.color(info));
		}, sender -> {
			return jailRegistry.getCells()
					.stream().map(jail -> String.valueOf(jail.getId())).toList();
		});

		this.addSubArgument(idArg);
	}
}
