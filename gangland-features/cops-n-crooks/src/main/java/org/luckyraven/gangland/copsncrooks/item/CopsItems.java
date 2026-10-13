package org.luckyraven.gangland.copsncrooks.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerMapSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerSettings;
import org.luckyraven.keystone.item.ItemBuilder;

import java.util.List;
import java.util.Locale;
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

	private final NamespacedKey                         key;
	private final Supplier<@Nullable CopConfigProvider> config;

	/**
	 * @param plugin owner of the PDC marker key.
	 * @param config the loaded cop config, {@code null} until the first load (then the shipped defaults apply).
	 */
	public CopsItems(JavaPlugin plugin, Supplier<@Nullable CopConfigProvider> config) {
		this.key    = new NamespacedKey(plugin, "cops_item");
		this.config = config;
	}

	/** The item ids {@link #create(String)} accepts, in tab-completion order. */
	public static List<String> ids() {
		return List.of(POLICE_SCANNER, SCANNER_MAP);
	}

	/** A fresh item for {@code id} (case-insensitive), or {@code null} when {@code id} is not one of {@link #ids()}. */
	public @Nullable ItemStack create(String id) {
		if (id == null) {
			return null;
		}

		String            normalized = id.toLowerCase(Locale.ROOT);
		CopConfigProvider provider   = config.get();
		String            name;
		List<String>      lore;
		Material          material;

		if (normalized.equals(POLICE_SCANNER)) {
			ScannerSettings settings = provider == null ? ScannerSettings.DEFAULT : provider.getScannerSettings();
			name     = settings.itemName();
			lore     = settings.itemLore();
			material = materialOf(settings.itemMaterial());
		} else if (normalized.equals(SCANNER_MAP)) {
			ScannerMapSettings settings = provider == null ? ScannerMapSettings.DEFAULT : provider.getScannerMapSettings();
			name     = settings.itemName();
			lore     = settings.itemLore();
			material = Material.FILLED_MAP;
		} else {
			return null;
		}

		ItemBuilder builder = new ItemBuilder(new ItemStack(material));
		builder.setDisplayName(name);
		builder.setLore(lore);

		ItemStack stack = builder.build();
		ItemMeta  meta  = stack.getItemMeta();
		if (meta != null) {
			meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, normalized);
			stack.setItemMeta(meta);
		}
		return stack;
	}

	/** {@code true} when {@code item} is a marked Police Scanner; {@code null}, air and plain clocks are not. */
	public boolean isScanner(@Nullable ItemStack item) {
		return isMarkedAs(item, POLICE_SCANNER);
	}

	/** {@code true} when {@code item} is a marked Scanner Map; {@code null}, air and plain maps are not. */
	public boolean isScannerMap(@Nullable ItemStack item) {
		return isMarkedAs(item, SCANNER_MAP);
	}

	private boolean isMarkedAs(@Nullable ItemStack item, String id) {
		if (item == null || !item.hasItemMeta()) {
			return false;
		}

		ItemMeta meta = item.getItemMeta();
		if (meta == null) {
			return false;
		}
		return id.equals(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING));
	}

	/** The configured material, or {@link Material#CLOCK} when the name is missing or unknown to this server. */
	private static Material materialOf(@Nullable String name) {
		Material material = name == null ? null : Material.getMaterial(name.toUpperCase(Locale.ROOT));
		return material == null ? Material.CLOCK : material;
	}
}
