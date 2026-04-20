package me.luckyraven.copsncrooks.npc.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.copsncrooks.npc.banker.config.BankerSettings;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract.ClaimInfo;
import me.luckyraven.copsncrooks.npc.banker.message.BankerMessageContract;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.utilities.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public final class BankerClaimView {

	private static final int SIZE         = 27;
	private static final int SLOT_INFO    = 4;
	private static final int SLOT_WEEKLY  = 11;
	private static final int SLOT_MONTHLY = 15;
	private static final int SLOT_BACK    = 22;

	private static final SoundConfiguration SOUND_CONFIRM = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "ENTITY_PLAYER_LEVELUP", 1.0f, 1.2f);
	private static final SoundConfiguration SOUND_DENY    = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "ENTITY_VILLAGER_NO", 0.8f, 1.0f);
	private static final SoundConfiguration SOUND_CANCEL  = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "UI_BUTTON_CLICK", 0.8f, 0.8f);

	private final JavaPlugin            plugin;
	private final BankerSettings        settings;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;

	@Setter
	private BankerMenuView menuView;

	public void open(Player viewer, BankerNpc banker) {
		ClaimInfo info = economy.claimInfo(viewer);
		if (!info.hasAccount()) {
			viewer.sendMessage(messages.noAccount());
			SOUND_DENY.playSound(viewer);
			if (menuView != null) Bukkit.getScheduler().runTask(plugin, () -> menuView.open(viewer, banker));
			return;
		}

		String name  = banker.getData().getDisplayName() != null ? banker.getData().getDisplayName() : "Banker";
		String title = "&8&l[&b&l" + name + "&8&l] &6Rewards";

		InventoryHandler handler = new InventoryHandler(plugin, title, SIZE, viewer);

		ItemBuilder infoItem = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		infoItem.setDisplayName("&6&lFree Rewards")
		        .setLore("&7Claim tier-scaled bonuses on cooldown.", "&7Grants go straight to your bank balance.");
		handler.setItem(SLOT_INFO, infoItem, false, (p, inv, b) -> { });

		renderRewardIcon(handler, SLOT_WEEKLY, "WEEKLY", info.weeklyAmount(), info.weeklyReadyAt(), banker,
		                 ClaimKind.WEEKLY);
		renderRewardIcon(handler, SLOT_MONTHLY, "MONTHLY", info.monthlyAmount(), info.monthlyReadyAt(), banker,
		                 ClaimKind.MONTHLY);

		ItemBuilder back = new ItemBuilder(material(XMaterial.ARROW, Material.ARROW)).setDisplayName("&7Back");
		handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> {
			SOUND_CANCEL.playSound(p);
			p.closeInventory();
			if (menuView != null) Bukkit.getScheduler().runTask(plugin, () -> menuView.open(p, banker));
		});

		InventoryUtil.fillInventory(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		handler.open(viewer);
	}

	private void renderRewardIcon(InventoryHandler handler, int slot, String label, BigDecimal amount,
	                              java.time.Instant readyAt, BankerNpc banker, ClaimKind kind) {
		boolean disabled = amount == null || amount.signum() <= 0;
		boolean ready    = !disabled && (readyAt == null || !Instant.now().isBefore(readyAt));

		XMaterial    preferred;
		Material     fallback;
		String       displayName;
		List<String> lore = new ArrayList<>();
		if (disabled) {
			preferred   = XMaterial.GRAY_WOOL;
			fallback    = Material.GRAY_WOOL;
			displayName = "&7" + label + " reward (disabled)";
			lore.add("&8Upgrade your bank tier to unlock this.");
		} else if (ready) {
			preferred   = kind == ClaimKind.WEEKLY ? XMaterial.LIME_WOOL : XMaterial.GOLD_BLOCK;
			fallback    = kind == ClaimKind.WEEKLY ? Material.LIME_WOOL : Material.GOLD_BLOCK;
			displayName = "&a&l" + label + " REWARD";
			lore.add("&7Amount: &a+$" + NumberUtil.valueFormat(amount));
			lore.add("&aAvailable now!");
			lore.add("&8Click to claim — lands in your bank balance.");
		} else {
			preferred   = XMaterial.YELLOW_WOOL;
			fallback    = Material.YELLOW_WOOL;
			displayName = "&e" + label + " reward";
			lore.add("&7Amount: &a+$" + NumberUtil.valueFormat(amount));
			Duration remaining = Duration.between(Instant.now(), readyAt);
			lore.add("&7Available in: &f" + formatDuration(remaining));
		}

		ItemBuilder icon = new ItemBuilder(material(preferred, fallback)).setDisplayName(displayName).setLore(lore);
		handler.setItem(slot, icon, false, (p, inv, b) -> {
			if (disabled) {
				SOUND_DENY.playSound(p);
				p.sendMessage(messages.loanDisabled());
				return;
			}
			if (!ready) {
				SOUND_DENY.playSound(p);
				p.sendMessage(messages.loanOnCooldown(formatDuration(Duration.between(Instant.now(), readyAt))));
				return;
			}
			performClaim(p, banker, kind);
		});
	}

	private void performClaim(Player viewer, BankerNpc banker, ClaimKind kind) {
		BankerEconomyContract.Result result = kind == ClaimKind.WEEKLY ?
		                                      economy.tryClaimWeekly(viewer) :
		                                      economy.tryClaimMonthly(viewer);

		ClaimInfo  refreshed = economy.claimInfo(viewer);
		BigDecimal amount    = kind == ClaimKind.WEEKLY ? refreshed.weeklyAmount() : refreshed.monthlyAmount();

		String msg;
		switch (result) {
			case SUCCESS -> {
				msg = kind == ClaimKind.WEEKLY ?
				      messages.weeklyLoanSuccess(amount) :
				      messages.monthlyLoanSuccess(amount);
				SOUND_CONFIRM.playSound(viewer);
			}
			case LOAN_ON_COOLDOWN -> {
				Instant readyAt = kind == ClaimKind.WEEKLY ? refreshed.weeklyReadyAt() : refreshed.monthlyReadyAt();
				String  remain  = readyAt == null ? "<1m" : formatDuration(Duration.between(Instant.now(), readyAt));
				msg = messages.loanOnCooldown(remain);
				SOUND_DENY.playSound(viewer);
			}
			case LOAN_DISABLED -> {
				msg = messages.loanDisabled();
				SOUND_DENY.playSound(viewer);
			}
			case LOAN_CAP_FULL -> {
				msg = messages.loanCapFull(amount);
				SOUND_DENY.playSound(viewer);
			}
			case NO_ACCOUNT -> msg = messages.noAccount();
			case TIER_MISSING -> msg = messages.tierMissing();
			default -> msg = null;
		}
		if (msg != null) viewer.sendMessage(msg);

		// Re-render so cooldown icon updates immediately.
		open(viewer, banker);
	}

	private String formatDuration(Duration duration) {
		long totalSec = Math.max(0, duration.getSeconds());
		long days     = totalSec / 86_400;
		long hours    = (totalSec % 86_400) / 3_600;
		long mins     = (totalSec % 3_600) / 60;

		if (days > 0) return days + "d " + hours + "h";
		if (hours > 0) return hours + "h " + mins + "m";
		if (mins > 0) return mins + "m";
		return "<1m";
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private enum ClaimKind {
		WEEKLY,
		MONTHLY
	}

}
