package org.luckyraven.gangland.copsncrooks.npc.police.config;

import java.util.List;

/** The Police Scanner item and range ({@code Cops.Scanner}). @since 0.20.0 */
public record ScannerSettings(boolean enabled, double range, String itemMaterial, String itemName, List<String> itemLore) {

	/** TDD stub: neutral values until the green chunk sets the shipped defaults and clamps. */
	public static final ScannerSettings DEFAULT = new ScannerSettings(false, 0.0, "", "", List.of());
}
