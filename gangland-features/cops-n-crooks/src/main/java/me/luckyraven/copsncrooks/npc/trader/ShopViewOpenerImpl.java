package me.luckyraven.copsncrooks.npc.trader;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.copsncrooks.npc.trader.view.TraderFlow;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.shop.message.ShopMessageContract;
import me.luckyraven.shop.view.ShopAdminFlow;
import org.bukkit.entity.Player;

@CustomLog
@RequiredArgsConstructor
public class ShopViewOpenerImpl implements ShopViewOpener {

	public static final String ADMIN_PERMISSION = "gangland.shop.admin";

	private final TraderManager         traderManager;
	private final ShopRegistry          shopRegistry;
	private final TraderFlow            traderFlow;
	private final ShopAdminFlow         adminFlow;
	private final ShopMessageContract   shopMessages;
	private final TraderMessageContract traderMessages;

	@Override
	public void openFor(Player player, TraderNpc trader) {
		ShopDefinition def = shopRegistry.get(trader.getData().getShopKey());
		if (def == null) {
			player.sendMessage(shopMessages.shopNotDefined(trader.getData().getShopKey()));
			return;
		}

		TraderTraitDefinition trait = traderManager.resolveTrait(trader.getData());
		if (trait == null) {
			player.sendMessage(traderMessages.traitInvalid());
			return;
		}

		if (player.hasPermission(ADMIN_PERMISSION) && player.isSneaking()) {
			adminFlow.start(player, def);
			return;
		}

		traderFlow.start(player, trader, def, trait);
	}

	public void openAdminView(Player admin, String shopKey) {
		ShopDefinition def = shopRegistry.get(shopKey);
		if (def == null) {
			admin.sendMessage(shopMessages.shopNotDefined(shopKey));
			return;
		}
		adminFlow.start(admin, def);
	}

}
