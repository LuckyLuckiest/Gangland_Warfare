package org.luckyraven.gangland.copsncrooks.npc.police.config;

import java.util.List;

/** The Scanner Map item ({@code Cops.Scanner_Map}). @since 0.20.0 */
public record ScannerMapSettings(boolean enabled, int blocksPerPixel, int refreshTicks, boolean showHideouts, String itemName, List<String> itemLore) {

	/** TDD stub: neutral values until the green chunk sets the shipped defaults and clamps. */
	public static final ScannerMapSettings DEFAULT = new ScannerMapSettings(false, 0, 0, false, "", List.of());
}
