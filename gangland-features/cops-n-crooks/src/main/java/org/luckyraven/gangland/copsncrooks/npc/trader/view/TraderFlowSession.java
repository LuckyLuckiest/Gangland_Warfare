package org.luckyraven.gangland.copsncrooks.npc.trader.view;

import org.luckyraven.gangland.copsncrooks.npc.trader.TraderNpc;
import org.luckyraven.gangland.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import org.luckyraven.gangland.inventory.flow.FlowSession;
import org.luckyraven.gangland.shop.ShopDefinition;
import org.luckyraven.gangland.shop.ShopItemEntry;

import java.math.BigDecimal;

/**
 * Mutable state shared by every panel inside the trader flow. Replaces the per-view
 * {@code WeakHashMap<Player, Session>} fields the old standalone views kept.
 */
public final class TraderFlowSession implements FlowSession {

	public static final String PANEL_MODE_SELECT = "mode_select";
	public static final String PANEL_SHOP        = "shop";
	public static final String PANEL_NEGOTIATION = "negotiation";
	public static final String PANEL_SELL        = "sell";
	public static final String PANEL_BARTER      = "barter";
	public static final String PANEL_QUANTITY    = "quantity";

	public final TraderNpc             trader;
	public final ShopDefinition        definition;
	public final TraderTraitDefinition trait;

	public int currentShopPage = 0;

	public ShopItemEntry selectedEntry;
	public BigDecimal    basePrice;
	public double        moodMultiplier;

	// Quantity-picker state (populated by NegotiationView.onBuyAmount before switchTo PANEL_QUANTITY; reset on entry
	// and on confirm so the next open starts fresh).
	public int quantityStaged = 1;
	public int quantityMode   = 1;

	public TraderFlowSession(TraderNpc trader, ShopDefinition definition, TraderTraitDefinition trait) {
		this.trader     = trader;
		this.definition = definition;
		this.trait      = trait;
	}

}
