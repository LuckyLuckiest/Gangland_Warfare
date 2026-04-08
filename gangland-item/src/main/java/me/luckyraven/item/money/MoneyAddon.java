package me.luckyraven.item.money;

import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.item.money.MoneyItem.PickupSound;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Loads and serves the cash variation registry plus the per-source drop rules from {@code money.yml}.
 *
 * <p>The addon is intentionally module-isolated: it accepts a {@link YamlConfiguration} so gangland-item never has
 * to import {@code FileManager}. The gangland-impl side opens the file via its existing {@code FileHandler} pipeline
 * and hands the configuration over via {@link #load(YamlConfiguration)}.
 */
@CustomLog
@Getter
public class MoneyAddon {

	private final Map<String, MoneyItem>            variations  = new HashMap<>();
	private final Map<MoneyDropContext, DropSource> dropSources = new EnumMap<>(MoneyDropContext.class);

	private String defaultVariationId = "small";
	private String pickupChatMessage  = "&a+ %gangland_money_symbol%%amount% &7added to your purse.";
	private String pickupActionBar    = "&a+ %gangland_money_symbol%%amount%";

	/**
	 * Master switch driven by {@code Money_Drop.Enabled} in {@code settings.yml}. The impl side calls
	 * {@link #setEnabled(boolean)} during initialization (and on reload). When false, the drop listener becomes inert
	 * and the pickup/interact listeners refuse to credit anything.
	 */
	@Setter
	private boolean enabled = true;

	/**
	 * Replaces the in-memory state with whatever is in {@code config}. Safe to call repeatedly (reload).
	 *
	 * <p>Accepts any {@link ConfigurationSection} (typically the root of a loaded {@code YamlConfiguration}) so the
	 * gangland-impl side can hand over the file configuration without gangland-item having to import
	 * {@code FileManager}.
	 */
	public void load(ConfigurationSection config) {
		clear();

		if (config == null) {
			log.warn("MoneyAddon.load received a null configuration; cash drops will be disabled until reload");
			return;
		}

		ConfigurationSection moneySection = config.getConfigurationSection("Money");
		if (moneySection != null) {
			defaultVariationId = moneySection.getString("Default_Variation", defaultVariationId);
			pickupChatMessage  = moneySection.getString("Pickup_Chat_Message", pickupChatMessage);
			pickupActionBar    = moneySection.getString("Pickup_Action_Bar", pickupActionBar);

			ConfigurationSection sourcesSection = moneySection.getConfigurationSection("Drop_Sources");
			if (sourcesSection != null) loadDropSources(sourcesSection);
		}

		ConfigurationSection variationsSection = config.getConfigurationSection("Variations");
		if (variationsSection != null) {
			for (String key : variationsSection.getKeys(false)) {
				ConfigurationSection vs = variationsSection.getConfigurationSection(key);
				if (vs == null) continue;
				MoneyItem variation = parseVariation(key, vs);
				if (variation != null) variations.put(key.toLowerCase(Locale.ROOT), variation);
			}
		}

		log.info("MoneyAddon loaded {} variation(s) and {} drop source(s)", variations.size(), dropSources.size());
	}

	public void clear() {
		variations.clear();
		dropSources.clear();
	}

	@Nullable
	public MoneyItem getVariation(String id) {
		if (id == null) return null;
		return variations.get(id.toLowerCase(Locale.ROOT));
	}

	@Nullable
	public MoneyItem getDefaultVariation() {
		return getVariation(defaultVariationId);
	}

	/**
	 * Picks a random variation eligible for the given drop source, or {@code null} if the source is disabled or has no
	 * configured variations.
	 */
	@Nullable
	public MoneyItem rollVariation(MoneyDropContext source) {
		DropSource ds = dropSources.get(source);
		if (ds == null || !ds.enabled || ds.weights.isEmpty()) return null;

		int totalWeight = 0;
		for (int weight : ds.weights.values()) totalWeight += Math.max(0, weight);
		if (totalWeight <= 0) return null;

		int roll       = ThreadLocalRandom.current().nextInt(totalWeight);
		int cumulative = 0;
		for (Map.Entry<String, Integer> entry : ds.weights.entrySet()) {
			cumulative += Math.max(0, entry.getValue());
			if (roll < cumulative) return getVariation(entry.getKey());
		}

		return null;
	}

	/**
	 * Rolls an amount inside the variation's [min, max] range (inclusive).
	 */
	public int rollAmount(MoneyItem variation) {
		if (variation == null) return 0;
		if (variation.getMin() >= variation.getMax()) return variation.getMin();
		return ThreadLocalRandom.current().nextInt(variation.getMin(), variation.getMax() + 1);
	}

	/**
	 * Returns the per-source drop configuration. Used by {@code MoneyDropListener} to read the
	 * {@code Scale_With_Balance} flag and balance fraction for player drops.
	 */
	@Nullable
	public DropSource getDropSource(MoneyDropContext context) {
		return dropSources.get(context);
	}

	private void loadDropSources(ConfigurationSection sourcesSection) {
		for (String key : sourcesSection.getKeys(false)) {
			MoneyDropContext context;
			try {
				context = MoneyDropContext.valueOf(key.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ex) {
				log.warn("Unknown money drop source '{}' in money.yml — skipping", key);
				continue;
			}

			ConfigurationSection sourceSection = sourcesSection.getConfigurationSection(key);
			if (sourceSection == null) continue;

			boolean enabled          = sourceSection.getBoolean("Enabled", true);
			boolean scaleWithBalance = sourceSection.getBoolean("Scale_With_Balance", false);
			double  balanceFraction  = sourceSection.getDouble("Balance_Fraction", 0.0D);

			Map<String, Integer> weights           = new HashMap<>();
			ConfigurationSection variationsSection = sourceSection.getConfigurationSection("Variations");
			if (variationsSection != null) {
				for (String vKey : variationsSection.getKeys(false)) {
					int weight = variationsSection.getInt(vKey + ".weight", 0);
					if (weight > 0) weights.put(vKey.toLowerCase(Locale.ROOT), weight);
				}
			}

			dropSources.put(context, new DropSource(enabled, weights, scaleWithBalance, balanceFraction));
		}
	}

	@Nullable
	private MoneyItem parseVariation(String id, ConfigurationSection vs) {
		String   materialName = vs.getString("Material", "PAPER");
		Material material;
		try {
			material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			log.warn("Invalid Material '{}' for money variation '{}'", materialName, id);
			return null;
		}

		int          customModelData = vs.getInt("Custom_Model_Data", 0);
		String       displayName     = vs.getString("Display_Name", "&aCash");
		List<String> lore            = new ArrayList<>(vs.getStringList("Lore"));
		int          min             = vs.getInt("Min", 0);
		int          max             = vs.getInt("Max", min);
		boolean      glow            = vs.getBoolean("Glow", false);

		PickupSound pickupSound = parsePickupSound(id, vs.getConfigurationSection("Pickup_Sound"));

		return new MoneyItem(id.toLowerCase(Locale.ROOT), material, customModelData, displayName,
		                     Collections.unmodifiableList(lore), min, max, glow, pickupSound);
	}

	@Nullable
	private PickupSound parsePickupSound(String id, @Nullable ConfigurationSection section) {
		if (section == null) return null;

		String soundName = section.getString("Sound");
		if (soundName == null || soundName.isEmpty()) return null;

		Sound sound;
		try {
			sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			log.warn("Invalid Pickup_Sound.Sound '{}' for money variation '{}'", soundName, id);
			return null;
		}

		float volume = (float) section.getDouble("Volume", 1.0D);
		float pitch  = (float) section.getDouble("Pitch", 1.0D);

		return new PickupSound(sound, volume, pitch);
	}

	/**
	 * Per-source drop rules: which variations are eligible (with weights), and an optional balance-scaling rule for
	 * player deaths.
	 */
	public record DropSource(boolean enabled,
	                         Map<String, Integer> weights,
	                         boolean scaleWithBalance,
	                         double balanceFraction) {

	}

}
