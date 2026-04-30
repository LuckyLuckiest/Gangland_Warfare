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
import org.luckyraven.gangland.copsncrooks.npc.banker.economy.BankerEconomyContract.BankerSnapshot;
import org.luckyraven.gangland.copsncrooks.npc.banker.message.BankerMessageContract;
import org.luckyraven.gangland.copsncrooks.npc.banker.tier.BankTier;
import org.luckyraven.gangland.core.ItemBuilder;
import org.luckyraven.gangland.core.configuration.SoundConfiguration;
import org.luckyraven.gangland.core.utilities.NumberUtil;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.inventory.flow.Panel;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.util.InventoryUtil;

import java.math.BigDecimal;

/**
 * Upgrade panel inside the banker flow. Previously a standalone view with its own {@code open(Player, BankerNpc)} +
 * {@code closeInventory()/menuView.open(...)} return path; now a {@link Panel} that renders into the flow's active
 * inventory handle and uses {@link MultiPanelInventory#back()} to return to the menu.
 *
 * <p>Preconditions (account present, a next tier exists) are already gated by {@link BankerMenuView}'s UPGRADE
 * button — it only shows when {@code snap.nextTier() != null}. If a race condition removes the next tier between click
 * and render (e.g. admin grant), the panel renders in its "no upgrade available" stub state with just a cancel button.
 */
@RequiredArgsConstructor
public final class BankerUpgradeView implements Panel<BankerFlowSession> {

	private static final int SIZE         = 27;
	private static final int SLOT_CURRENT = 11;
	private static final int SLOT_ARROW   = 13;
	private static final int SLOT_NEXT    = 15;
	private static final int SLOT_CANCEL  = 20;
	private static final int SLOT_CONFIRM = 24;

	private static final SoundConfiguration SOUND_CONFIRM = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
	private static final SoundConfiguration SOUND_DENY    = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "ENTITY_VILLAGER_NO", 0.8f, 1.0f);
	private static final SoundConfiguration SOUND_CANCEL  = new SoundConfiguration(SoundConfiguration.SoundType.VANILLA,
	                                                                               "UI_BUTTON_CLICK", 0.8f, 0.8f);

	private final JavaPlugin            plugin;
	private final BankerSettings        settings;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;

	private static String format(BigDecimal value) {
		return NumberUtil.valueFormat(value);
	}

	@Override
	public int size(BankerFlowSession session) {
		return SIZE;
	}

	@Override
	public String title(BankerFlowSession session) {
		return "&8&l[&b&l" + session.displayName() + "&8&l] &7Upgrade";
	}

	@Override
	public void render(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler, Player viewer,
	                   BankerFlowSession session) {
		BankerSnapshot snap = economy.snapshot(viewer);
		BankTier       next = snap.hasBank() ? snap.nextTier() : null;

		if (next == null) {
			renderUnavailable(host, handler, snap);
		} else {
			renderUpgradeOffer(host, handler, snap, next, session);
		}

		InventoryUtil.fillInventory(handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));
	}

	private void renderUnavailable(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler,
	                               BankerSnapshot snap) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
		info.setDisplayName("&7No upgrade available")
		    .setLore(snap.hasBank() ? "&8Your account is at the top tier." : "&8Open a bank account first.");
		handler.setItem(SLOT_CURRENT, info, false, (p, inv, b) -> { });

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cBACK");
		handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> {
			host.back();
			playSoundNextTick(p, SOUND_CANCEL);
		});
	}

	private void renderUpgradeOffer(MultiPanelInventory<BankerFlowSession> host, InventoryHandler handler,
	                                BankerSnapshot snap, BankTier next, BankerFlowSession session) {
		BankTier    current     = snap.currentTier();
		ItemBuilder currentItem = new ItemBuilder(material(XMaterial.IRON_BLOCK, Material.IRON_BLOCK));
		currentItem.setDisplayName("&7Current: " + (current != null ? current.displayName() : "&8None"))
		           .setLore("&7Cap: &f$" + format(current != null ? current.maxBalance() : null),
		                    "&7Balance: &f$" + format(snap.bankBalance()));
		handler.setItem(SLOT_CURRENT, currentItem, false, (p, inv, b) -> { });

		ItemBuilder arrow = new ItemBuilder(material(XMaterial.ARROW, Material.ARROW));
		arrow.setDisplayName("&e→ Upgrade").setLore("&7Pay &6$" + format(next.upgradeCost()));
		handler.setItem(SLOT_ARROW, arrow, false, (p, inv, b) -> { });

		ItemBuilder nextItem = new ItemBuilder(material(XMaterial.DIAMOND_BLOCK, Material.DIAMOND_BLOCK));
		nextItem.setDisplayName("&aNext: " + next.displayName())
		        .setLore("&7New cap: &f$" + format(next.maxBalance()),
		                 "&7Upgrade cost: &6$" + format(next.upgradeCost()));
		handler.setItem(SLOT_NEXT, nextItem, false, (p, inv, b) -> { });

		ItemBuilder confirm = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		confirm.setDisplayName("&a&lCONFIRM UPGRADE")
		       .setLore("&7Pay &6$" + format(next.upgradeCost()) + " &7from your bank.");
		handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> performUpgrade(host, p, session, next));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL");
		handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> {
			host.back();
			playSoundNextTick(p, SOUND_CANCEL);
		});
	}

	private void performUpgrade(MultiPanelInventory<BankerFlowSession> host, Player viewer, BankerFlowSession session,
	                            BankTier next) {
		BankerEconomyContract.Result result = economy.tryUpgrade(viewer);
		SoundConfiguration           sound  = null;
		String                       msg;
		switch (result) {
			case SUCCESS -> {
				msg   = messages.upgradeSuccess(next.displayName());
				sound = SOUND_CONFIRM;
			}
			case ALREADY_MAX_TIER -> {
				msg   = messages.upgradeMaxTier();
				sound = SOUND_DENY;
			}
			case INSUFFICIENT_BANK_FUNDS -> {
				msg   = messages.upgradeInsufficientFunds(next.upgradeCost());
				sound = SOUND_DENY;
			}
			case NO_ACCOUNT -> msg = messages.noAccount();
			case TIER_MISSING -> msg = messages.tierMissing();
			default -> msg = null;
		}
		if (msg != null) viewer.sendMessage(msg);

		// After the economy mutation: on success, return to the menu so the new tier renders; on failure, re-render
		// this panel so the offer reflects the (still) current bank state.
		if (result == BankerEconomyContract.Result.SUCCESS) {
			host.back();
		} else {
			host.rerender();
		}
		if (sound != null) playSoundNextTick(viewer, sound);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	/**
	 * Defers the sound by one tick so it plays after the panel swap has settled on the client. Playing the sound in the
	 * same tick as a flow transition makes the client render the audio cue and the inventory change together, which the
	 * viewer experiences as a flicker.
	 */
	private void playSoundNextTick(Player player, SoundConfiguration sound) {
		Bukkit.getScheduler().runTask(plugin, () -> sound.playSound(player));
	}

}
