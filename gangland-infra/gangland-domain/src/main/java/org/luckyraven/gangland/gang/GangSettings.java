package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.contract.GangSettingsContract;

import java.util.Objects;

/**
 * Static facade over {@link GangSettingsContract} for gang-module data classes (Gang, RankManager) whose
 * constructors run at object-construction time and cannot easily receive the contract via DI.
 *
 * <p>The impl-side {@code GangModuleConfig} calls {@link #bind(GangSettingsContract)}
 * during the CONFIG phase before any gang data class is constructed. Calls made before binding throw a clear error
 * rather than NPE.
 *
 * <p>Split down to these 3 gang-display getters (WS5 G0, B2) — the identity-slice getters (user level/bounty/
 * wanted) moved to {@code IdentitySettings} in gangland-core, bound separately.
 */
public final class GangSettings {

	private static GangSettingsContract delegate;

	private GangSettings() { }

	public static void bind(GangSettingsContract contract) {
		delegate = Objects.requireNonNull(contract, "GangSettingsContract must not be null");
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
