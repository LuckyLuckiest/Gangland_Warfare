package me.luckyraven.command.sub.item.money;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyItem;
import me.luckyraven.item.money.MoneyItemUtil;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.JsonFormatter;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class ItemMoneyInfoCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	ItemMoneyInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = gangland.getInitializer().getUserManager();

		moneyInfo();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack itemStack = player.getInventory().getItemInMainHand();

			if (!MoneyItemUtil.isMoneyItem(itemStack)) {
				user.sendMessage(Messages.ITEM_MONEY_NOT_HELD.toString());
				return;
			}

			MoneyAddon moneyAddon = gangland.getInitializer().getMoneyAddon();
			String     id         = MoneyItemUtil.readVariationId(itemStack);
			MoneyItem  variation  = id == null ? null : moneyAddon.getVariation(id);

			if (variation == null) {
				user.sendMessage(Messages.ITEM_MONEY_NOT_REGISTERED.toString().replace("%name%", String.valueOf(id)));
				return;
			}

			int    stamped = MoneyItemUtil.readAmount(itemStack);
			String info    = buildInfo(variation, stamped);

			JsonFormatter jsonFormatter = new JsonFormatter();

			user.sendMessage(jsonFormatter.formatToJson(GanglandChatUtil.color(info), " ".repeat(3)));
		};
	}

	private void moneyInfo() {
		Argument name = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String     id         = args[3];
			MoneyAddon moneyAddon = gangland.getInitializer().getMoneyAddon();
			MoneyItem  variation  = moneyAddon.getVariation(id);

			if (variation == null) {
				user.sendMessage(Messages.ITEM_MONEY_INVALID.toString().replace("%name%", id));
				return;
			}

			String        info          = buildInfo(variation, -1);
			JsonFormatter jsonFormatter = new JsonFormatter();

			user.sendMessage(jsonFormatter.formatToJson(GanglandChatUtil.color(info), " ".repeat(3)));
		}, sender -> {
			MoneyAddon moneyAddon = gangland.getInitializer().getMoneyAddon();
			return moneyAddon.getVariations().keySet()
					.stream().toList();
		});

		this.addSubArgument(name);
	}

	private String buildInfo(MoneyItem variation, int stampedValue) {
		StringBuilder info = new StringBuilder();
		info.append("&7Id&8: &b").append(variation.getId())
		    .append("\n&7Display Name&8: &b").append(variation.getDisplayName())
		    .append("\n&7Material&8: &b").append(variation.getMaterial().name())
		    .append("\n&7Min Value&8: &b").append(variation.getMin())
		    .append("\n&7Max Value&8: &b").append(variation.getMax())
		    .append("\n&7Glow&8: &b").append(variation.isGlow())
		    .append("\n&7Custom Model Data&8: &b").append(variation.getCustomModelData());

		if (stampedValue >= 0) {
			info.append("\n&7Stamped Value&8: &b").append(stampedValue);
		}

		return info.toString();
	}

}
