package me.luckyraven.item.money;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Contract used by gangland-item money listeners to credit a player's balance and notify them of the deposit.
 *
 * <p>The implementation in gangland-impl looks the player's {@code User} up via {@code UserManager}, calls
 * {@code EconomyHandler#deposit}, and emits both a chat message (using the existing {@code Messages} system) and an
 * action-bar flash (via {@code ActionBarManager}). The listener stays free of any impl-side imports.
 */
public interface MoneyDepositService {

	/**
	 * Credits the player's balance with {@code amount} and notifies them.
	 *
	 * @param player the player picking up the cash
	 * @param amount the amount rolled by the variation
	 * @param variationId the variation that was picked up (for logging / future analytics)
	 */
	void deposit(Player player, double amount, String variationId);

	/**
	 * Returns the current balance for the given player, or {@code 0} if the player is not loaded. Used by the drop
	 * listener when computing balance-scaled drops on player death.
	 */
	double getBalance(Player player);

	/**
	 * Returns the configured currency symbol (e.g. {@code $}). Listeners use this when they need to render a value
	 * outside of the standard pickup notification, such as in the drop log.
	 */
	String getCurrencySymbol();

	/**
	 * Runs the given text through the impl-side placeholder pipeline (PlaceholderAPI when present, the gangland
	 * placeholder handler otherwise) so callers in gangland-item can resolve {@code %gangland_*%} placeholders without
	 * importing the impl module. {@code player} may be {@code null} when there is no specific viewer (e.g. the item
	 * parser building a money template); resolution then falls back to player-independent placeholders such as the
	 * settings-backed {@code %gangland_money_symbol%}.
	 */
	String resolvePlaceholders(@Nullable Player player, String text);

}
