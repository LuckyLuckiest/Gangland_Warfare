package org.luckyraven.gangland.core.user;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Static facade over {@link IdentitySettingsContract} for identity-slice data classes ({@link User}, {@link Level})
 * whose constructors run at object-construction time and cannot easily receive the contract via DI.
 *
 * <p>The impl-side identity config calls {@link #bind(IdentitySettingsContract)} during the CONFIG phase before any
 * identity data class is constructed. Calls made before binding throw a clear error rather than NPE.
 *
 * <p>Split out of {@code GangSettings} (WS5 G0, B2) — the gang-module remainder keeps the {@code GangSettings} name
 * for its 3 display getters.
 */
public final class IdentitySettings {

	private static IdentitySettingsContract delegate;

	private IdentitySettings() { }

	public static void bind(IdentitySettingsContract contract) {
		delegate = Objects.requireNonNull(contract, "IdentitySettingsContract must not be null");
	}

	public static boolean isAutoSave() {
		return require().isAutoSave();
	}

	public static int getUserMaxLevel() {
		return require().getUserMaxLevel();
	}

	public static int getUserLevelBaseAmount() {
		return require().getUserLevelBaseAmount();
	}

	public static String getUserLevelFormula() {
		return require().getUserLevelFormula();
	}

	public static BigDecimal getBountyEachKillValue() {
		return require().getBountyEachKillValue();
	}

	public static double getBountyTimerMultiple() {
		return require().getBountyTimerMultiple();
	}

	public static double getBountyTimerMax() {
		return require().getBountyTimerMax();
	}

	public static boolean isBountyTimerEnabled() {
		return require().isBountyTimerEnabled();
	}

	public static int getWantedLevelIncrement() {
		return require().getWantedLevelIncrement();
	}

	public static int getWantedMaximumLevel() {
		return require().getWantedMaximumLevel();
	}

	public static boolean isWantedTimerEnabled() {
		return require().isWantedTimerEnabled();
	}

	private static IdentitySettingsContract require() {
		if (delegate == null) {
			throw new IllegalStateException(
					"IdentitySettings accessed before the CONFIG phase bound the contract. " +
					"This means an identity data class was constructed before bootstrap wired it.");
		}
		return delegate;
	}
}
