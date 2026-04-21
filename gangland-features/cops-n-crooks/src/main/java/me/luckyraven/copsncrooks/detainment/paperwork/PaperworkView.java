package me.luckyraven.copsncrooks.detainment.paperwork;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.detainment.DetainedPlayer;
import me.luckyraven.copsncrooks.detainment.DetainmentRegistry;
import me.luckyraven.copsncrooks.detainment.bail.BailResult;
import me.luckyraven.copsncrooks.detainment.bail.BailService;
import me.luckyraven.copsncrooks.detainment.bribe.BribeResult;
import me.luckyraven.copsncrooks.detainment.bribe.BribeService;
import me.luckyraven.copsncrooks.detainment.economy.DetainmentCostsContract;
import me.luckyraven.copsncrooks.detainment.economy.DetainmentEconomyContract;
import me.luckyraven.copsncrooks.detainment.message.DetainmentMessageContract;
import me.luckyraven.copsncrooks.detainment.sentence.SentenceService;
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
 * Three-row menu opened when a jailed player right-clicks their Jail Paperwork item. Bail / Bribe / Sentence release
 * paths all funnel through the appropriate service, which in turn routes successful exits through the release
 * pipeline.
 */
@RequiredArgsConstructor
public final class PaperworkView {

	private static final int SIZE          = 27;
	private static final int SLOT_BAIL     = 11;
	private static final int SLOT_BRIBE    = 13;
	private static final int SLOT_SENTENCE = 15;
	private static final int SLOT_INFO     = 22;

	private final JavaPlugin                plugin;
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
		String           title   = ChatUtil.color(messages.paperworkGuiTitle());
		InventoryHandler handler = new InventoryHandler(plugin, title, SIZE, player);

		double bailCost  = bailService.computeCost(player);
		double bribeCost = bribeService.computeJailBribeCost(player);
		double balance   = economy.getBalance(player);

		DetainedPlayer detained = detainmentRegistry.getDetainedPlayers().get(player.getUniqueId());
		int wantedAtArrest = detained == null || detained.getWantedAtArrest() == null
		                     ? 0 : detained.getWantedAtArrest();
		long   remainingSec  = sentenceService.getRemainingSeconds(player);
		double chancePercent = costs.getJailBribeSuccessChance() * 100.0;

		// Bail
		ItemStack   bailIcon   = moneyIconProvider.buildIcon(bailCost);
		ItemBuilder bailButton = new ItemBuilder(bailIcon);
		bailButton.setDisplayName(messages.paperworkBailLabel(formatMoney(bailCost)))
		          .setLore(messages.paperworkBailLore(formatMoney(bailCost), formatMoney(balance)));
		handler.setItem(SLOT_BAIL, bailButton, false, (p, inv, b) -> {
			p.closeInventory();
			BailResult result = bailService.tryPayBail(p);
			handleBailResult(p, result);
		});

		// Bribe
		ItemStack   bribeIcon   = moneyIconProvider.buildIcon(bribeCost);
		ItemBuilder bribeButton = new ItemBuilder(bribeIcon);
		bribeButton.setDisplayName(messages.paperworkBribeLabel(formatMoney(bribeCost)))
		           .setLore(messages.paperworkBribeLore(formatMoney(bribeCost),
		                                                String.format("%.0f", chancePercent)));
		handler.setItem(SLOT_BRIBE, bribeButton, false, (p, inv, b) -> {
			p.closeInventory();
			BribeResult result = bribeService.tryJailBribe(p);
			handleBribeResult(p, result);
		});

		// Sentence
		ItemStack sentenceIcon = XMaterial.CLOCK.parseItem();
		ItemBuilder sentenceButton = new ItemBuilder(
				sentenceIcon != null ? sentenceIcon : new ItemStack(Material.CLOCK));
		sentenceButton.setDisplayName(messages.paperworkSentenceLabel())
		              .setLore(messages.paperworkSentenceLore(remainingSec));
		handler.setItem(SLOT_SENTENCE, sentenceButton, false, (p, inv, b) -> p.closeInventory());

		// Info
		ItemStack   infoIcon = XMaterial.PAPER.parseItem();
		ItemBuilder info     = new ItemBuilder(infoIcon != null ? infoIcon : new ItemStack(Material.PAPER));
		info.setDisplayName(messages.paperworkInfoLabel())
		    .setLore(messages.paperworkInfoLore(wantedAtArrest, remainingSec, formatMoney(balance)));
		handler.setItem(SLOT_INFO, info, false, (p, inv, b) -> { });

		InventoryUtil.fillInventory(handler, new Fill(" ", "BLACK_STAINED_GLASS_PANE"));

		DetainmentGuiAccess.authorize(player.getUniqueId());
		handler.open(player);
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
