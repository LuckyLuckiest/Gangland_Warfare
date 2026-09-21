package org.luckyraven.gangland.copsncrooks.detainment.paperwork;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.bribe.BribeResult;
import org.luckyraven.gangland.copsncrooks.detainment.bribe.BribeService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.ChatUtil;

/**
 * GUI opened when a HANDCUFFED player right-clicks the cop currently guarding them. Shows a single bribe button —
 * clicking it charges the player's balance and releases them if they have enough.
 */
@RequiredArgsConstructor
public final class HandcuffBribeView {

	private static final int ROWS       = 3;
	private static final int SLOT_BRIBE = 13;
	private static final int SLOT_CLOSE = 22;

	private final JavaPlugin                plugin;
	private final InventoryService          inventoryService;
	private final BribeService              bribeService;
	private final DetainmentEconomyContract economy;
	private final MoneyIconProvider         moneyIconProvider;
	private final DetainmentMessageContract messages;

	private static String formatMoney(double amount) {
		return String.format("%,.2f", amount);
	}

	public void open(Player player, CopNpc cop) {
		String title = ChatUtil.color(messages.handcuffBribeGuiTitle());

		double cost    = bribeService.computeHandcuffBribeCost(player);
		double balance = economy.getBalance(player);

		ChestMenuBuilder builder = ChestMenu.builder(inventoryService).title(title).rows(ROWS);

		ItemStack   iconSource = moneyIconProvider.buildIcon(cost);
		ItemBuilder button     = new ItemBuilder(iconSource);
		button.setDisplayName(messages.handcuffBribeButtonLabel(formatMoney(cost)))
		      .setLore(messages.handcuffBribeButtonLore(formatMoney(cost), formatMoney(balance)));
		builder.slot(SLOT_BRIBE, ItemComponent.of(button).onAnyClick(ctx -> {
			ctx.player().closeInventory();
			BribeResult result = bribeService.tryHandcuffBribe(ctx.player(), cop);
			handleResult(ctx.player(), result);
		}));

		ItemStack   closeSource = XMaterial.BARRIER.parseItem();
		ItemBuilder close       = new ItemBuilder(closeSource != null ? closeSource : new ItemStack(Material.BARRIER));
		close.setDisplayName(ChatUtil.color("&cClose"));
		builder.slot(SLOT_CLOSE, ItemComponent.of(close).onAnyClick(ctx -> ctx.player().closeInventory()));

		builder.fill(FillComponent.of(Material.BLACK_STAINED_GLASS_PANE).name(" "));

		DetainmentGuiAccess.authorize(player.getUniqueId());
		builder.build().open(player);
	}

	private void handleResult(Player player, BribeResult result) {
		switch (result) {
			case SUCCESS:
				ChatUtil.sendTitle(player, messages.handcuffBribeSuccessTitle(),
				                   messages.handcuffBribeSuccessSubtitle());
				return;
			case INSUFFICIENT_FUNDS:
			case ECONOMY_ERROR:
				player.sendMessage(ChatUtil.color(messages.handcuffBribeInsufficient()));
				return;
			default:
				// WRONG_COP / NOT_HANDCUFFED — silently ignored; the GUI should not have been open.
		}
	}
}
