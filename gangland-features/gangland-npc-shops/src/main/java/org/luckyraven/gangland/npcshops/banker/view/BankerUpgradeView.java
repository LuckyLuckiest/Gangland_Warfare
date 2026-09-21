package org.luckyraven.gangland.npcshops.banker.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.banker.config.BankerSettings;
import org.luckyraven.gangland.npcshops.banker.economy.BankerEconomyContract;
import org.luckyraven.gangland.npcshops.banker.economy.BankerEconomyContract.BankerSnapshot;
import org.luckyraven.gangland.npcshops.banker.message.BankerMessageContract;
import org.luckyraven.gangland.npcshops.banker.tier.BankTier;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.sound.SoundEffect;
import org.luckyraven.keystone.util.NumberUtil;

import java.math.BigDecimal;

/**
 * Upgrade panel inside the banker flow. Previously a standalone view with its own {@code open(Player, BankerNpc)} +
 * {@code closeInventory()/menuView.open(...)} return path; now a {@link Panel} that renders into the flow's active
 * inventory handle and uses {@link MenuFlow#back()} to return to the menu.
 *
 * <p>Preconditions (account present, a next tier exists) are already gated by {@link BankerMenuView}'s UPGRADE
 * button — it only shows when {@code snap.nextTier() != null}. If a race condition removes the next tier between click
 * and render (e.g. admin grant), the panel renders in its "no upgrade available" stub state with just a cancel button.
 */
@RequiredArgsConstructor
public final class BankerUpgradeView implements Panel<BankerFlowSession> {

	private static final int ROWS         = 3;
	private static final int SLOT_CURRENT = 11;
	private static final int SLOT_ARROW   = 13;
	private static final int SLOT_NEXT    = 15;
	private static final int SLOT_CANCEL  = 20;
	private static final int SLOT_CONFIRM = 24;

	private static final SoundEffect SOUND_CONFIRM = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                               "ENTITY_PLAYER_LEVELUP", 1.0f, 1.0f);
	private static final SoundEffect SOUND_DENY    = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                               "ENTITY_VILLAGER_NO", 0.8f, 1.0f);
	private static final SoundEffect SOUND_CANCEL  = new SoundEffect(SoundEffect.SoundType.VANILLA,
	                                                                               "UI_BUTTON_CLICK", 0.8f, 0.8f);

	private final JavaPlugin            plugin;
	private final BankerSettings        settings;
	private final BankerEconomyContract economy;
	private final BankerMessageContract messages;

	private static String format(BigDecimal value) {
		return NumberUtil.valueFormat(value);
	}

	@Override
	public int rows(BankerFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(BankerFlowSession session) {
		return "&8&l[&b&l" + session.displayName() + "&8&l] &7Upgrade";
	}

	@Override
	public void render(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder, BankerFlowSession session) {
		BankerSnapshot snap = economy.snapshot(flow.viewer());
		BankTier       next = snap.hasBank() ? snap.nextTier() : null;

		if (next == null) {
			renderUnavailable(flow, builder, snap);
		} else {
			renderUpgradeOffer(flow, builder, snap, next, session);
		}

		builder.fill(FillComponent.of(materialOf(settings.getInventoryFillItem())).name(settings.getInventoryFillName()));
	}

	private void renderUnavailable(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder, BankerSnapshot snap) {
		ItemBuilder info = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER));
		info.setDisplayName("&7No upgrade available")
		    .setLore(snap.hasBank() ? "&8Your account is at the top tier." : "&8Open a bank account first.");
		builder.slot(SLOT_CURRENT, ItemComponent.of(info));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cBACK");
		builder.slot(SLOT_CANCEL, ItemComponent.of(cancel).onAnyClick(ctx -> {
			flow.back();
			playSoundNextTick(ctx.player(), SOUND_CANCEL);
		}));
	}

	private void renderUpgradeOffer(MenuFlow<BankerFlowSession> flow, ChestMenuBuilder builder, BankerSnapshot snap,
	                                BankTier next, BankerFlowSession session) {
		BankTier    current     = snap.currentTier();
		ItemBuilder currentItem = new ItemBuilder(material(XMaterial.IRON_BLOCK, Material.IRON_BLOCK));
		currentItem.setDisplayName("&7Current: " + (current != null ? current.displayName() : "&8None"))
		           .setLore("&7Cap: &f$" + format(current != null ? current.maxBalance() : null),
		                    "&7Balance: &f$" + format(snap.bankBalance()));
		builder.slot(SLOT_CURRENT, ItemComponent.of(currentItem));

		ItemBuilder arrow = new ItemBuilder(material(XMaterial.ARROW, Material.ARROW));
		arrow.setDisplayName("&e→ Upgrade").setLore("&7Pay &6$" + format(next.upgradeCost()));
		builder.slot(SLOT_ARROW, ItemComponent.of(arrow));

		ItemBuilder nextItem = new ItemBuilder(material(XMaterial.DIAMOND_BLOCK, Material.DIAMOND_BLOCK));
		nextItem.setDisplayName("&aNext: " + next.displayName())
		        .setLore("&7New cap: &f$" + format(next.maxBalance()),
		                 "&7Upgrade cost: &6$" + format(next.upgradeCost()));
		builder.slot(SLOT_NEXT, ItemComponent.of(nextItem));

		ItemBuilder confirm = new ItemBuilder(material(XMaterial.LIME_WOOL, Material.GREEN_WOOL));
		confirm.setDisplayName("&a&lCONFIRM UPGRADE")
		       .setLore("&7Pay &6$" + format(next.upgradeCost()) + " &7from your bank.");
		builder.slot(SLOT_CONFIRM,
		            ItemComponent.of(confirm).onAnyClick(ctx -> performUpgrade(flow, ctx.player(), session, next)));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.RED_WOOL, Material.RED_WOOL));
		cancel.setDisplayName("&cCANCEL");
		builder.slot(SLOT_CANCEL, ItemComponent.of(cancel).onAnyClick(ctx -> {
			flow.back();
			playSoundNextTick(ctx.player(), SOUND_CANCEL);
		}));
	}

	private void performUpgrade(MenuFlow<BankerFlowSession> flow, Player viewer, BankerFlowSession session,
	                            BankTier next) {
		BankerEconomyContract.Result result = economy.tryUpgrade(viewer);
		SoundEffect           sound  = null;
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
			flow.back();
		} else {
			flow.rerender();
		}
		if (sound != null) playSoundNextTick(viewer, sound);
	}

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}

	/**
	 * Defers the sound by one tick so it plays after the panel swap has settled on the client. Playing the sound in the
	 * same tick as a flow transition makes the client render the audio cue and the inventory change together, which the
	 * viewer experiences as a flicker.
	 */
	private void playSoundNextTick(Player player, SoundEffect sound) {
		Bukkit.getScheduler().runTask(plugin, () -> sound.playSound(player));
	}

}
