package me.luckyraven.command.sub.trader;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.npc.trader.TraderData;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitRegistry;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
