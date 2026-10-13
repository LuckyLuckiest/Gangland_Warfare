package org.luckyraven.gangland.copsncrooks.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;

import java.util.List;
import java.util.function.Supplier;

/**
 * The two cops-owned items, the Police Scanner and the Scanner Map: a factory and a PDC marker check
 * ({@code PaperworkItem} pattern). Name, lore and material come from {@code Cops.Scanner} / {@code Cops.Scanner_Map}
 * of the config loaded at call time, so a reload is seen on the next item.
 *
 * @since 0.20.0
 */
public class CopsItems {

	/** Item id of the Police Scanner, as typed in {@code /glw cop item} and {@code copsncrooks:police_scanner}. */
	public static final String POLICE_SCANNER = "police_scanner";
	/** Item id of the Scanner Map. */
	public static final String SCANNER_MAP    = "scanner_map";

	/**
	 * @param plugin owner of the PDC marker key.
	 * @param config the loaded cop config, {@code null} until the first load (then the shipped defaults apply).
	 */
	public CopsItems(JavaPlugin plugin, Supplier<@Nullable CopConfigProvider> config) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The item ids {@link #create(String)} accepts, in tab-completion order. */
	public static List<String> ids() {
		return List.of();
	}

	/** A fresh item for {@code id} (case-insensitive), or {@code null} when {@code id} is not one of {@link #ids()}. */
	public @Nullable ItemStack create(String id) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** {@code true} when {@code item} is a marked Police Scanner; {@code null}, air and plain clocks are not. */
	public boolean isScanner(@Nullable ItemStack item) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** {@code true} when {@code item} is a marked Scanner Map; {@code null}, air and plain maps are not. */
	public boolean isScannerMap(@Nullable ItemStack item) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
