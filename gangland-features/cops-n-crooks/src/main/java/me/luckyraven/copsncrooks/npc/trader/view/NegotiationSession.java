package me.luckyraven.copsncrooks.npc.trader.view;

import lombok.Getter;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopItemEntry;

import java.math.BigDecimal;

/**
 * Adapter value type carried from {@code NegotiationView} (the flow panel) into {@code BarterView} (still a standalone
 * legacy view). Exists to keep {@code BarterView}'s {@code open(..., NegotiationSession, ...)} signature unchanged
 * after the negotiation view was converted to a {@link me.luckyraven.inventory.flow.Panel}; the flow no longer needs
 * this object internally — it reads the equivalent state from {@code TraderFlowSession}.
 */
@Getter
public final class NegotiationSession {

	private final TraderNpc             trader;
	private final ShopDefinition        definition;
	private final ShopItemEntry         entry;
	private final TraderTraitDefinition trait;
	private final BigDecimal            basePrice;

	public NegotiationSession(TraderNpc trader, ShopDefinition definition, ShopItemEntry entry,
	                          TraderTraitDefinition trait, BigDecimal basePrice) {
		this.trader     = trader;
		this.definition = definition;
		this.entry      = entry;
		this.trait      = trait;
		this.basePrice  = basePrice;
	}

}
