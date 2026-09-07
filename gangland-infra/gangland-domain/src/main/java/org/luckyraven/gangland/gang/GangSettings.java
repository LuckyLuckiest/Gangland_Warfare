package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.contract.GangSettingsContract;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Static facade over {@link GangSettingsContract} for gang-module data classes (Gang, User, Level) whose constructors
 * run at object-construction time and cannot easily receive the contract via DI.
 *
 * <p>The impl-side {@code GangModuleConfig} calls {@link #bind(GangSettingsContract)}
 * during the CONFIG phase before any gang data class is constructed. Calls made before binding throw a clear error
 * rather than NPE.
 */
public final class GangSettings {

	private static GangSettingsContract delegate;

	private GangSettings() { }

	public static void bind(GangSettingsContract contract) {
		delegate = Objects.requireNonNull(contract, "GangSettingsContract must not be null");
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

	public static String getGangDisplayNameChar() {
		return require().getGangDisplayNameChar();
	}

	public static String getGangRankHead() {
		return require().getGangRankHead();
	}

	public static String getGangRankTail() {
		return require().getGangRankTail();
	}

	private static GangSettingsContract require() {
		if (delegate == null) {
			throw new IllegalStateException(
					"GangSettings accessed before GangModuleConfig bound the contract. " +
					"This means a gang data class was constructed before the CONFIG phase ran.");
		}
		return delegate;
	}
}
