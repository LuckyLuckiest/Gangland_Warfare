package me.luckyraven.gang.wanted;

import java.math.BigDecimal;

/**
 * Provides the data a {@link WantedExecutor} needs from its owning entity.
 * <p>
 * Implementations live in {@code gangland-impl} (e.g. {@code User}) so that {@code cops-n-crooks} stays decoupled from
 * the main plugin's account and economy layers.
 */
public interface WantedContext {

	/**
	 * Returns the wanted-level tracker for the entity.
	 */
	Wanted getWanted();

	/**
	 * Withdraws up to {@code requestedAmount} from the entity's balance.
	 * <p>
	 * If the balance is insufficient, the implementation must withdraw the remaining balance and return that amount
	 * instead. Never throws - economy exceptions are handled internally.
	 *
	 * @param requestedAmount the desired withdrawal amount
	 *
	 * @return the actual amount withdrawn (may be less than requested; 0 if nothing was withdrawn)
	 */
	BigDecimal withdraw(BigDecimal requestedAmount);

	/**
	 * Sends a message to the entity. Implementations may apply color codes and placeholder replacement before
	 * delivery.
	 */
	void sendMessage(String message);
}
