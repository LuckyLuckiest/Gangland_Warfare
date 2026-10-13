package org.luckyraven.gangland.copsncrooks.npc.police.config;

import java.util.List;

/** The Police Scanner item and range ({@code Cops.Scanner}). @since 0.20.0 */
public record ScannerSettings(boolean enabled, double range, String itemMaterial, String itemName, List<String> itemLore) {

	/** Matches the 0.20.0 cops.yml defaults. */
	public static final ScannerSettings DEFAULT = new ScannerSettings(true, 160.0, "CLOCK", "&9Police Scanner", List.of("&7Hear the police radio"));

	public ScannerSettings {
		range = Math.max(0.0, range);
		itemLore = itemLore == null ? List.of() : List.copyOf(itemLore);
	}
}
