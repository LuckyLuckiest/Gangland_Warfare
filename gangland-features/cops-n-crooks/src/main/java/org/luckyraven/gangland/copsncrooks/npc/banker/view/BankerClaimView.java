package org.luckyraven.gangland.copsncrooks.npc.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.banker.config.BankerSettings;
import org.luckyraven.gangland.copsncrooks.npc.banker.economy.BankerEconomyContract;
import org.luckyraven.gangland.copsncrooks.npc.banker.economy.BankerEconomyContract.ClaimInfo;
import org.luckyraven.gangland.copsncrooks.npc.banker.message.BankerMessageContract;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.inventory.flow.Panel;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.util.InventoryUtil;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Rewards-claim panel inside the banker flow. Shows the weekly + monthly bonus icons with their current
 * ready/cooldown/disabled state; clicking a ready icon grants the reward into the bank balance and
 * {@link MultiPanelInventory#rerender() rerenders} the panel so the cooldown icon updates immediately.
 */
@RequiredArgsConstructor
public final class BankerClaimView implements Panel<BankerFlowSession> {

	private static final int SIZE         = 27;
	private static final int SLOT_INFO    = 4;
	private static final int SLOT_WEEKLY  = 11;
	private static final int SLOT_MONTHLY = 15;
	private static final int SLOT_BACK    = 22;

	private static final SoundEffect SOUND_CONFIRM = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                               "ENTITY_PLAYER_LEVELUP", 1.0f, 1.2f);
	private static final SoundEffect SOUND_DENY    = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                               "ENTITY_VILLAGER_NO", 0.8f, 1.0f);
	private static final SoundEffect SOUND_CANCEL  = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                               "UI_BUTTON_CLICK", 0.8f, 0.8f);

	private final JavaPlugin            plugin;
	private final BankerSettings        settings;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;

	@Override
	public int size(BankerFlowSession session) {
		return SIZE;
	}

	@Override
	public String title(BankerFlowSession session) {
		return "&8&l[&b&l" + session.displayName() + "&8&l] &6Rewards";
	}

	@Override
	public void render(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler, Player viewer,
	                   BankerFlowSession session) {
		ClaimInfo info = economy.claimInfo(viewer);

		if (!info.hasAccount()) {
			renderNoAccount(host, handler);
		} else {
			renderRewards(host, handler, info);
		}

		ItemBuilder back = new ItemBuilder(material(XMaterial.ARROW, Material.ARROW)).setDisplayName("&7Back");
		handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> {
			host.back();
			playSoundNextTick(p, SOUND_CANCEL);
		});

		InventoryUtil.fillInventory(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));
	}

	private void renderNoAccount(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler) {
		ItemBuilder infoItem = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
		infoItem.setDisplayName("&cNo bank account on file")
		        .setLore("&8Open an account first to unlock weekly + monthly rewards.");
		handler.setItem(SLOT_INFO, infoItem, false, (p, inv, b) -> { });
	}

	private void renderRewards(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler, ClaimInfo info) {
		ItemBuilder infoItem = new ItemBuilder(material(XMaterial.PAPER, Material.PAPER));
		infoItem.setDisplayName("&6&lFree Rewards")
		        .setLore("&7Claim tier-scaled bonuses on cooldown.", "&7Grants go straight to your bank balance.");
		handler.setItem(SLOT_INFO, infoItem, false, (p, inv, b) -> { });

		renderRewardIcon(host, handler, SLOT_WEEKLY, "WEEKLY", info.weeklyAmount(), info.weeklyReadyAt(),
		                 ClaimKind.WEEKLY);
		renderRewardIcon(host, handler, SLOT_MONTHLY, "MONTHLY", info.monthlyAmount(), info.monthlyReadyAt(),
		                 ClaimKind.MONTHLY);
	}

	private void renderRewardIcon(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler, int slot,
	                              String label, BigDecimal amount, Instant readyAt, ClaimKind kind) {
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
			performClaim(host, p, kind);
		});
	}

	private void performClaim(MultiPanelInventory<BankerFlowSession> host, Player viewer, ClaimKind kind) {
		BankerEconomyContract.Result result = kind == ClaimKind.WEEKLY ?
		                                      economy.tryClaimWeekly(viewer) :
		                                      economy.tryClaimMonthly(viewer);

		ClaimInfo  refreshed = economy.claimInfo(viewer);
		BigDecimal amount    = kind == ClaimKind.WEEKLY ? refreshed.weeklyAmount() : refreshed.monthlyAmount();

		SoundEffect sound = null;
		String             msg;
		switch (result) {
			case SUCCESS -> {
				msg   = kind == ClaimKind.WEEKLY ? messages.weeklyLoanSuccess(amount)
				                                 : messages.monthlyLoanSuccess(amount);
				sound = SOUND_CONFIRM;
			}
			case LOAN_ON_COOLDOWN -> {
				Instant readyAt = kind == ClaimKind.WEEKLY ? refreshed.weeklyReadyAt() : refreshed.monthlyReadyAt();
				String  remain  = readyAt == null ? "<1m" : formatDuration(Duration.between(Instant.now(), readyAt));
				msg   = messages.loanOnCooldown(remain);
				sound = SOUND_DENY;
			}
			case LOAN_DISABLED -> {
				msg   = messages.loanDisabled();
				sound = SOUND_DENY;
			}
			case LOAN_CAP_FULL -> {
				msg   = messages.loanCapFull(amount);
				sound = SOUND_DENY;
			}
			case NO_ACCOUNT -> msg = messages.noAccount();
			case TIER_MISSING -> msg = messages.tierMissing();
			default -> msg = null;
		}
		if (msg != null) viewer.sendMessage(msg);

		// Re-render so cooldown icon updates immediately against the fresh ClaimInfo snapshot.
		host.rerender();
		if (sound != null) playSoundNextTick(viewer, sound);
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

	/**
	 * Defers the sound by one tick so it plays after the panel swap / rerender has settled on the client. Playing the
	 * sound inline in the same tick as a flow transition makes the client render audio and inventory change together,
	 * which the viewer experiences as a flicker.
	 */
	private void playSoundNextTick(Player player, SoundEffect sound) {
		Bukkit.getScheduler().runTask(plugin, () -> sound.playSound(player));
	}

	private enum ClaimKind {
		WEEKLY,
		MONTHLY
	}

}
