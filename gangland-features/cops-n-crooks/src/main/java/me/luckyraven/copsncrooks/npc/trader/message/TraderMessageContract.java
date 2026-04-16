package me.luckyraven.copsncrooks.npc.trader.message;

/**
 * Trader-scoped message contract. Covers the flows that are specific to the trader NPC feature (tipping, bargaining,
 * trait validation). Integrations (gangland-impl) implement this interface by routing each call to their preferred
 * {@code Messages} enum entry with placeholder substitution.
 *
 * <p>Generic shop flows (purchase, barter, admin edit) live in {@code ShopMessageContract} in shop-api.
 */
public interface TraderMessageContract {

	String tipSuccess(double amount);

	String tipInsufficientFunds(double amount);

	String traitInvalid();

	String bargainAccepted(double resolvedPrice);

	String bargainCounterOffer(double resolvedPrice);

	String bargainRejected();

	String bargainHardRejected();

	String bargainAlreadyBelow();

	String angered(String traderName);

	String sellProposePriceAngered();

	String sellProposePriceInvalid();

	String sellProposePriceAccepted(double price);

	String bargainOnCooldown(long secondsRemaining);

}
