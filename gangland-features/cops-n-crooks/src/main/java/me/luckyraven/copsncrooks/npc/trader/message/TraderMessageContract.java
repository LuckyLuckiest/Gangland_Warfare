package me.luckyraven.copsncrooks.npc.trader.message;

/**
 * Trader-scoped message contract. Covers the flows that are specific to the trader NPC feature (tipping, trait
 * validation). Integrations (gangland-impl) implement this interface by routing each call to their preferred
 * {@code Messages} enum entry with placeholder substitution.
 *
 * <p>Generic shop flows (purchase, barter, admin edit) live in {@code ShopMessageContract} in shop-api.
 */
public interface TraderMessageContract {

	String tipSuccess(double amount);

	String tipInsufficientFunds(double amount);

	String traitInvalid();

}
