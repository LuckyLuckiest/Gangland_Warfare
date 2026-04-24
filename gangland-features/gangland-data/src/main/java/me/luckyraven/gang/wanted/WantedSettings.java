package me.luckyraven.gang.wanted;

import java.math.BigDecimal;

/**
 * Provides wanted-timer configuration values to {@link WantedExecutor}.
 * <p>
 * Implementations live in {@code gangland-impl} and delegate to {@code me.luckyraven.file.configuration.SettingAddon}
 * and {@code me.luckyraven.file.configuration.MessageAddon}, keeping {@code cops-n-crooks} decoupled from the main
 * plugin's file-loading infrastructure.
 */
public interface WantedSettings {

	/**
	 * Whether the timer interval should scale with the wanted level.
	 */
	boolean isTimerMultiplierEnabled();

	/**
	 * Base for the exponential timer scaling: {@code interval * multiplier ^ level}.
	 */
	double getTimerMultiplierAmount();

	/**
	 * Base timer interval in seconds before applying the multiplier.
	 */
	int getTimerTime();

	/**
	 * Base money amount taken from the player per timer tick.
	 */
	BigDecimal getTakeMoneyAmount();

	/**
	 * Multiplier for money taken: {@code base * multiplier ^ level}.
	 */
	double getTakeMoneyMultiplier();

	/**
	 * Returns the raw wanted-decreased message template. May contain {@code %level%} and {@code %stars%} placeholders.
	 */
	String getWantedDecreasedMessageTemplate();

	/**
	 * Returns a fully formatted money-loss notification string (e.g. {@code "&c&l-$5.00"}). Color codes should use the
	 * {@code &} notation; the caller's {@code sendMessage} is expected to translate them.
	 */
	String formatMoneyLoss(BigDecimal amount);
}
