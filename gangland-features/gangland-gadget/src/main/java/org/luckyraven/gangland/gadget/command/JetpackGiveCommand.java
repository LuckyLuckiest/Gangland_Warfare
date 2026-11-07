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
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.gadget.jetpack.message.JetpackMessages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

/**
 * {@code /glw jetpack give <id> [amount]}. Self-give, mirroring {@link CarGiveCommand} exactly (plan §5, W16 shape
 * correction — an earlier revision gave to a named target player; that was a dispatch-wording error, not the
 * plan's own binding shape).
 */
class JetpackGiveCommand extends SubArgument {

	private final JavaPlugin      plugin;
	private final Tree<Argument>  tree;
	private final JetpackAddon    jetpackAddon;
	private final JetpackMessages messages;

	JetpackGiveCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, JetpackAddon jetpackAddon,
	                   JetpackMessages messages) {
		super(plugin, "give", tree, parent);

		this.plugin       = plugin;
		this.tree         = tree;
		this.jetpackAddon = jetpackAddon;
		this.messages     = messages;

		jetpackGive();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void jetpackGive() {
		Argument name = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			Player player = (Player) sender;

			String jetpackId = args[2];
			int    gave      = giveJetpackItem(player, jetpackId, 1);

			if (gave >= 0) {
				player.sendMessage(messages.gave(jetpackId, String.valueOf(gave)));
			} else {
				player.sendMessage(messages.invalid(jetpackId));
			}
		}, sender -> jetpackAddon.getJetpacks().keySet().stream().toList());

		Argument amount = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			Player player = (Player) sender;

			String jetpackId = args[2];
			int    jetpackAmount;

			try {
				jetpackAmount = Integer.parseInt(args[3]);
			} catch (NumberFormatException exception) {
				player.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", args[3]));
				return;
			}

			if (jetpackAmount <= 0) {
				player.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", args[3]));
				return;
			}

			int gave = giveJetpackItem(player, jetpackId, jetpackAmount);

			if (gave >= 0) {
				player.sendMessage(messages.gave(jetpackId, String.valueOf(gave)));
			} else {
				player.sendMessage(messages.invalid(jetpackId));
			}
		}, sender -> List.of("<amount>"));

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	/**
	 * @return the actual number of items given (after the 36-stack cap), or {@code -1} if {@code name} names no
	 * 		jetpack
	 */
	private int giveJetpackItem(Player player, String name, int amount) {
		Jetpack jetpack = jetpackAddon.getJetpack(name);

		if (jetpack == null) return -1;

		return GiveItems.give(player, () -> jetpack.buildItem(player), amount);
	}

}
