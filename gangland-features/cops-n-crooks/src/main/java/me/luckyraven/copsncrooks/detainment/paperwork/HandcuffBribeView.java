package me.luckyraven.copsncrooks.detainment.paperwork;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.detainment.bribe.BribeResult;
import me.luckyraven.copsncrooks.detainment.bribe.BribeService;
import me.luckyraven.copsncrooks.detainment.economy.DetainmentEconomyContract;
import me.luckyraven.copsncrooks.detainment.message.DetainmentMessageContract;
import me.luckyraven.copsncrooks.npc.police.npc.CopNpc;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * GUI opened when a HANDCUFFED player right-clicks the cop currently guarding them. Shows a single bribe button —
 * clicking it charges the player's balance and releases them if they have enough.
 */
@RequiredArgsConstructor
public final class HandcuffBribeView {

	private static final int SIZE       = 27;
	private static final int SLOT_BRIBE = 13;
	private static final int SLOT_CLOSE = 22;

	private final JavaPlugin                plugin;
	private final BribeService              bribeService;
	private final DetainmentEconomyContract economy;
	private final MoneyIconProvider         moneyIconProvider;
	private final DetainmentMessageContract messages;

	private static String formatMoney(double amount) {
		return String.format("%,.2f", amount);
	}

	public void open(Player player, CopNpc cop) {
		String           title   = ChatUtil.color(messages.handcuffBribeGuiTitle());
		InventoryHandler handler = new InventoryHandler(plugin, title, SIZE, player);

		double cost    = bribeService.computeHandcuffBribeCost(player);
		double balance = economy.getBalance(player);

		ItemStack   iconSource = moneyIconProvider.buildIcon(cost);
		ItemBuilder button     = new ItemBuilder(iconSource);
		button.setDisplayName(messages.handcuffBribeButtonLabel(formatMoney(cost)))
		      .setLore(messages.handcuffBribeButtonLore(formatMoney(cost), formatMoney(balance)));

		handler.setItem(SLOT_BRIBE, button, false, (p, inv, b) -> {
			p.closeInventory();
			BribeResult result = bribeService.tryHandcuffBribe(p, cop);
			handleResult(p, result);
		});

		ItemStack   closeSource = XMaterial.BARRIER.parseItem();
		ItemBuilder close       = new ItemBuilder(closeSource != null ? closeSource : new ItemStack(Material.BARRIER));
		close.setDisplayName(ChatUtil.color("&cClose"));
		handler.setItem(SLOT_CLOSE, close, false, (p, inv, b) -> p.closeInventory());

		InventoryUtil.fillInventory(handler, new Fill(" ", "BLACK_STAINED_GLASS_PANE"));

		DetainmentGuiAccess.authorize(player.getUniqueId());
		handler.open(player);
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
