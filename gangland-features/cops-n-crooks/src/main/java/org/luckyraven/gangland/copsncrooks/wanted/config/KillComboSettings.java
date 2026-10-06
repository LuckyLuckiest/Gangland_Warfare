package org.luckyraven.gangland.copsncrooks.wanted.config;

import lombok.CustomLog;
import org.luckyraven.gangland.file.configuration.MovedSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code Wanted.Kill_Combo} of {@code copsncrooks/wanted.yml} (0.15.1, formerly settings.yml under the same path).
 *
 * @param enabled           {@code false} = no combo: with heat off every counted kill adds a star at once, and no
 *                          crime is ever chained.
 * @param resetAfterSeconds seconds until the combo resets; also the heat streak window.
 * @param killCounter       kill counts that each add a wanted level.
 * @since 0.15.1
 */
@CustomLog
public record KillComboSettings(boolean enabled, int resetAfterSeconds, List<Integer> killCounter) {

	/** The shipped {@code Wanted.Kill_Combo}. */
	public static final KillComboSettings DEFAULT = new KillComboSettings(true, 10, List.of(2, 5, 10, 15, 20));

	private static final String PATH = "Wanted.Kill_Combo.";

	/**
	 * Reads the three keys through the settings.yml bridge. wanted.yml keeps the settings.yml path, so the module path
	 * and the legacy path are the same. The defaults are the old {@code Settings} ones, except Kill_Counter, whose
	 * code default was empty: the shipped settings.yml list is the default here.
	 */
	public static KillComboSettings read(MovedSetting moved) {
		boolean enabled = moved.getBoolean(PATH + "Enable", PATH + "Enable", DEFAULT.enabled());
		int     reset   = moved.getInt(PATH + "Reset_After", PATH + "Reset_After", DEFAULT.resetAfterSeconds());

		List<String> defaultCounter = DEFAULT.killCounter().stream().map(String::valueOf).toList();
		List<String> rawCounter     = moved.getStringList(PATH + "Kill_Counter", PATH + "Kill_Counter",
		                                                  defaultCounter);

		List<Integer> counter = new ArrayList<>(rawCounter.size());
		for (String raw : rawCounter) {
			try {
				counter.add(Integer.parseInt(raw.trim()));
			} catch (NumberFormatException exception) {
				log.warn("Wanted.Kill_Combo.Kill_Counter: '{}' is not a whole number and is skipped", raw);
			}
		}

		return new KillComboSettings(enabled, reset, List.copyOf(counter));
	}

	/** The heat streak window: the reset time while the combo is on, 0 (no crime is ever chained) while it is off. */
	public int chainSeconds() {
		return enabled ? resetAfterSeconds : 0;
	}
}
