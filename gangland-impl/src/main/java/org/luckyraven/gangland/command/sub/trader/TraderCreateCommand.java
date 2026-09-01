package org.luckyraven.gangland.command.sub.trader;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderData;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderManager;
import org.luckyraven.gangland.copsncrooks.npc.trader.trait.TraderTraitRegistry;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.shop.ShopRegistry;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class TraderCreateCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final TraderManager       traderManager;
	private final ShopRegistry        shopRegistry;
	private final TraderTraitRegistry traitRegistry;

	protected TraderCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              TraderManager traderManager, ShopRegistry shopRegistry,
	                              TraderTraitRegistry traitRegistry) {
		super(gangland, "create", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.traderManager = traderManager;
		this.shopRegistry  = shopRegistry;
		this.traitRegistry = traitRegistry;

		registerArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<shopKey>"));
	}

	private void registerArguments() {
		Argument shopArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<traitId>")),
		                                        sender -> new ArrayList<>(shopRegistry.keys()));

		Argument traitArg = new OptionalArgument(gangland, tree, (argument, sender, args) ->
				spawnTrader(sender, args, null),
		                                         sender -> new ArrayList<>(traitRegistry.ids()));

		Argument displayArg = new OptionalArgument(gangland, tree, (argument, sender, args) ->
				spawnTrader(sender, args, args[4]),
		                                           sender -> List.of("<displayName>"));

		traitArg.addSubArgument(displayArg);
		shopArg.addSubArgument(traitArg);
		this.addSubArgument(shopArg);
	}

	private void spawnTrader(CommandSender sender, String[] args, String display) {
		if (!(sender instanceof Player player)) return;

		String shopKey = args[2].toLowerCase();
		String traitId = args[3].toLowerCase();

		if (!shopRegistry.exists(shopKey)) {
			player.sendMessage(Messages.TRADER_SHOP_MISSING.toString().replace("%shop%", shopKey));
			return;
		}

		if (!traitRegistry.exists(traitId)) {
			player.sendMessage(Messages.TRADER_TRAIT_MISSING.toString()
			                                                .replace("%trait%", traitId)
			                                                .replace("%available%",
			                                                         String.join(", ", traitRegistry.ids())));
			return;
		}

		Location   loc  = player.getLocation();
		TraderData data = new TraderData(UUID.randomUUID(), shopKey, loc, display, traitId);
		traderManager.create(data);

		player.sendMessage(GanglandChatUtil.commandMessage(
				"&aSpawned trader (&f" + (display != null ? display : "unnamed")
				+ "&a) with trait &f" + traitId + " &aselling &f" + shopKey));
	}

}
