package org.luckyraven.gangland.copsncrooks.npc.police.config;

import java.util.List;

/** The Scanner Map item ({@code Cops.Scanner_Map}). @since 0.20.0 */
public record ScannerMapSettings(boolean enabled, int blocksPerPixel, int refreshTicks, boolean showHideouts, String itemName, List<String> itemLore) {

	/** Matches the 0.20.0 cops.yml defaults. */
	public static final ScannerMapSettings DEFAULT = new ScannerMapSettings(true, 1, 10, true, "&9Scanner Map", List.of("&7Cops, search zone, hideouts"));

	public ScannerMapSettings {
		blocksPerPixel = Math.max(1, blocksPerPixel);
		refreshTicks = Math.max(1, refreshTicks);
		itemLore = itemLore == null ? List.of() : List.copyOf(itemLore);
	}
}
