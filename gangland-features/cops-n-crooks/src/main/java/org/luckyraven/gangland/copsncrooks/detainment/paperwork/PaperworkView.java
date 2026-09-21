package org.luckyraven.gangland.copsncrooks.detainment.paperwork;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.bail.BailResult;
import org.luckyraven.gangland.copsncrooks.detainment.bail.BailService;
import org.luckyraven.gangland.copsncrooks.detainment.bribe.BribeResult;
import org.luckyraven.gangland.copsncrooks.detainment.bribe.BribeService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.detainment.sentence.SentenceService;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.ChatUtil;

/**
 * Three-row menu opened when a jailed player right-clicks their Jail Paperwork item. Bail / Bribe / Sentence release
 * paths all funnel through the appropriate service, which in turn routes successful exits through the release
 * pipeline.
 */
@RequiredArgsConstructor
public final class PaperworkView {

	private static final int ROWS          = 3;
	private static final int SLOT_BAIL     = 11;
	private static final int SLOT_BRIBE    = 13;
	private static final int SLOT_SENTENCE = 15;
	private static final int SLOT_INFO     = 22;

	private final JavaPlugin                plugin;
	private final InventoryService          inventoryService;
	private final DetainmentRegistry        detainmentRegistry;
	private final DetainmentCostsContract   costs;
	private final DetainmentEconomyContract economy;
	private final BailService               bailService;
	private final BribeService              bribeService;
	private final SentenceService           sentenceService;
	private final MoneyIconProvider         moneyIconProvider;
	private final DetainmentMessageContract messages;

	private static String formatMoney(double amount) {
		return String.format("%,.2f", amount);
	}

	public void open(Player player) {
		String title = ChatUtil.color(messages.paperworkGuiTitle());

		double bailCost  = bailService.computeCost(player);
		double bribeCost = bribeService.computeJailBribeCost(player);
		double balance   = economy.getBalance(player);

		DetainedPlayer detained = detainmentRegistry.getDetainedPlayers().get(player.getUniqueId());
		int wantedAtArrest = detained == null || detained.getWantedAtArrest() == null
		                     ? 0 : detained.getWantedAtArrest();
		long   remainingSec  = sentenceService.getRemainingSeconds(player);
		double chancePercent = costs.getJailBribeSuccessChance() * 100.0;

		ChestMenuBuilder builder = ChestMenu.builder(inventoryService).title(title).rows(ROWS);

		// Bail
		ItemStack   bailIcon   = moneyIconProvider.buildIcon(bailCost);
		ItemBuilder bailButton = new ItemBuilder(bailIcon);
		bailButton.setDisplayName(messages.paperworkBailLabel(formatMoney(bailCost)))
		          .setLore(messages.paperworkBailLore(formatMoney(bailCost), formatMoney(balance)));
		builder.slot(SLOT_BAIL, ItemComponent.of(bailButton).onAnyClick(ctx -> {
			ctx.player().closeInventory();
			BailResult result = bailService.tryPayBail(ctx.player());
			handleBailResult(ctx.player(), result);
		}));

		// Bribe
		ItemStack   bribeIcon   = moneyIconProvider.buildIcon(bribeCost);
		ItemBuilder bribeButton = new ItemBuilder(bribeIcon);
		bribeButton.setDisplayName(messages.paperworkBribeLabel(formatMoney(bribeCost)))
		           .setLore(messages.paperworkBribeLore(formatMoney(bribeCost),
		                                                String.format("%.0f", chancePercent)));
		builder.slot(SLOT_BRIBE, ItemComponent.of(bribeButton).onAnyClick(ctx -> {
			ctx.player().closeInventory();
			BribeResult result = bribeService.tryJailBribe(ctx.player());
			handleBribeResult(ctx.player(), result);
		}));

		// Sentence
		ItemStack sentenceIcon = XMaterial.CLOCK.parseItem();
		ItemBuilder sentenceButton = new ItemBuilder(
				sentenceIcon != null ? sentenceIcon : new ItemStack(Material.CLOCK));
		sentenceButton.setDisplayName(messages.paperworkSentenceLabel())
		              .setLore(messages.paperworkSentenceLore(remainingSec));
		builder.slot(SLOT_SENTENCE, ItemComponent.of(sentenceButton).onAnyClick(ctx -> ctx.player().closeInventory()));

		// Info
		ItemStack   infoIcon = XMaterial.PAPER.parseItem();
		ItemBuilder info     = new ItemBuilder(infoIcon != null ? infoIcon : new ItemStack(Material.PAPER));
		info.setDisplayName(messages.paperworkInfoLabel())
		    .setLore(messages.paperworkInfoLore(wantedAtArrest, remainingSec, formatMoney(balance)));
		builder.slot(SLOT_INFO, ItemComponent.of(info));

		builder.fill(FillComponent.of(Material.BLACK_STAINED_GLASS_PANE).name(" "));

		DetainmentGuiAccess.authorize(player.getUniqueId());
		builder.build().open(player);
	}

	private void handleBailResult(Player player, BailResult result) {
		switch (result) {
			case SUCCESS:
				player.sendMessage(ChatUtil.color(messages.bailSuccess()));
				return;
			case INSUFFICIENT_FUNDS:
			case ECONOMY_ERROR:
				player.sendMessage(ChatUtil.color(messages.bailInsufficient()));
				return;
			default:
				// NOT_JAILED — GUI should not have been open; silently ignored
		}
	}

	private void handleBribeResult(Player player, BribeResult result) {
		switch (result) {
			case SUCCESS:
				player.sendMessage(ChatUtil.color(messages.jailBribeSuccess()));
				return;
			case FAIL:
				player.sendMessage(ChatUtil.color(
						messages.jailBribeFail(costs.getJailBribeFailPenaltySeconds())));
				return;
			case INSUFFICIENT_FUNDS:
			case ECONOMY_ERROR:
				player.sendMessage(ChatUtil.color(messages.jailBribeInsufficient()));
				return;
			default:
				// NOT_JAILED / WRONG_COP — silently ignored
		}
	}
}
