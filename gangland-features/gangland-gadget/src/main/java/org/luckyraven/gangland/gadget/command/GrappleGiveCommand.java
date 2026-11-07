package org.luckyraven.gangland.gadget.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gadget.grapple.Grapple;
import org.luckyraven.gangland.gadget.grapple.config.GrappleAddon;
import org.luckyraven.gangland.gadget.grapple.message.GrappleMessages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

/**
 * {@code /glw grapple give <id> [amount]}. Self-give, mirrors {@link JetpackGiveCommand} exactly (itself mirroring
 * {@link CarGiveCommand}). Permission: {@code SubArgument}'s own constructor derives {@code
 * gangland.command.grapple.give} from the parent {@code GrappleCommand}'s {@code gangland.command.grapple} — no
 * manual permission check needed in this class's body (confirmed against Keystone's actual {@code
 * SubArgument}/{@code Argument.traverseList} source, same mechanism as {@code CarGiveCommand}/{@code
 * JetpackGiveCommand}). The per-item permission ({@code gangland.grapples.<id>}) is a separate, unrelated gate
 * already enforced at launch time, not here — mirrors car/jetpack's give-vs-use permission split exactly.
 */
class GrappleGiveCommand extends SubArgument {

	private final JavaPlugin      plugin;
	private final Tree<Argument>  tree;
	private final GrappleAddon    grappleAddon;
	private final GrappleMessages messages;

	GrappleGiveCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, GrappleAddon grappleAddon,
	                   GrappleMessages messages) {
		super(plugin, "give", tree, parent);

		this.plugin       = plugin;
		this.tree         = tree;
		this.grappleAddon = grappleAddon;
		this.messages     = messages;

		grappleGive();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void grappleGive() {
		Argument name = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			Player player = (Player) sender;

			String  grappleId = args[2];
			boolean gave      = giveGrappleItem(player, grappleId, 1);

			if (gave) {
				player.sendMessage(messages.gave(grappleId, "1"));
			} else {
				player.sendMessage(messages.invalid(grappleId));
			}
		}, sender -> grappleAddon.getGrapples().keySet().stream().toList());

		Argument amount = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			Player player = (Player) sender;

			String grappleId = args[2];
			int    grappleAmount;

			try {
				grappleAmount = Integer.parseInt(args[3]);
			} catch (NumberFormatException exception) {
				player.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", args[3]));
				return;
			}

			if (grappleAmount <= 0) {
				player.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", args[3]));
				return;
			}

			boolean gave = giveGrappleItem(player, grappleId, grappleAmount);

			if (gave) {
				player.sendMessage(messages.gave(grappleId, String.valueOf(grappleAmount)));
			} else {
				player.sendMessage(messages.invalid(grappleId));
			}
		}, sender -> List.of("<amount>"));

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	private boolean giveGrappleItem(Player player, String name, int amount) {
		Grapple grapple = grappleAddon.getGrapple(name);

		if (grapple == null) return false;

		GiveItems.give(player, () -> grapple.buildItem(player), amount);

		return true;
	}

}
