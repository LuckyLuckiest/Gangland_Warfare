package me.luckyraven.command.sub.shop;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.util.GanglandChatUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ShopTitleCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final ShopRegistry   shopRegistry;

	protected ShopTitleCommand(Gangland gangland, Tree<Argument> tree, Argument parent, ShopRegistry shopRegistry) {
		super(gangland, "title", tree, parent);

		this.gangland     = gangland;
		this.tree         = tree;
		this.shopRegistry = shopRegistry;

		registerArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<key>"));
	}

	private void registerArguments() {
		Argument keyArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.SHOP_ONLY_PLAYERS.toString());
				return;
			}

			String         raw = args[2].toLowerCase();
			ShopDefinition def = shopRegistry.get(raw);
			if (def == null) {
				player.sendMessage(Messages.SHOP_NOT_DEFINED.toString().replace("%shop%", raw));
				return;
			}

			openTitleAnvil(player, def);
		}, sender -> new ArrayList<>(shopRegistry.keys()));

		this.addSubArgument(keyArg);
	}

	private void openTitleAnvil(Player admin, ShopDefinition def) {
		new AnvilGUI.Builder()
				.plugin(gangland)
				.title("Shop Title")
				.itemLeft(new ItemStack(Material.NAME_TAG))
				.text(def.getTitle())
				.onClick((slot, state) -> {
					if (slot != AnvilGUI.Slot.OUTPUT) return Collections.emptyList();

					String text = state.getText() == null ? "" : state.getText();
					if (text.isBlank()) {
						admin.sendMessage(Messages.SHOP_TITLE_EMPTY.toString());
						return Collections.emptyList();
					}

					shopRegistry.save(def.withTitle(text));
					admin.sendMessage(Messages.SHOP_TITLE_SET.toString()
					                                         .replace("%shop%", def.getKey())
					                                         .replace("%title%", text));
					return List.of(AnvilGUI.ResponseAction.close());
				})
				.open(admin);
	}

}
