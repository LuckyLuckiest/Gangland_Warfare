package org.luckyraven.gangland.copsncrooks.npc.trader.message;

import java.math.BigDecimal;

/**
 * Trader-scoped message contract. Covers the flows that are specific to the trader NPC feature (tipping, trait
 * validation). Integrations (gangland-impl) implement this interface by routing each call to their preferred
 * {@code Messages} enum entry with placeholder substitution.
 *
 * <p>Generic shop flows (purchase, barter, admin edit) live in {@code ShopMessageContract} in shop-api.
 */
public interface TraderMessageContract {

	String tipSuccess(BigDecimal amount);

	String tipInsufficientFunds(BigDecimal amount);

	String traitInvalid();

}
