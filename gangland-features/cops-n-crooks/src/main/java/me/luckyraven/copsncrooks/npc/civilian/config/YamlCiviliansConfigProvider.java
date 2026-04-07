package me.luckyraven.copsncrooks.npc.civilian.config;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.copsncrooks.entity.npc.NpcDifficulty;
import me.luckyraven.item.ItemParser;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Reads {@code entity_marker.yml} and builds an {@link EntityMarkerConfig}.
 * <p>
 * Weapon-pool and wearable entries follow the same parsing convention as {@code YamlCopConfigProvider}: entries
 * prefixed with {@code weapon:} go into {@code weaponNamePool} for gangland weapon resolution; all other entries are
 * parsed via {@link ItemParser} into ItemStacks for {@code weaponPool}.
 */
@Getter
@CustomLog
public class YamlEntityMarkerConfigProvider {

	private final EntityMarkerConfig config;

	public YamlEntityMarkerConfigProvider(FileConfiguration yaml, boolean aiEnabled, int aiTickRate,
	                                      @Nullable ItemParser itemParser) {
		List<String> defaultCivilian = loadDefaultEntities(yaml, "Default_Entities.Civilian");
		List<String> defaultPolice   = loadDefaultEntities(yaml, "Default_Entities.Police");

		Map<String, CivilianTypeConfig>  types  = loadTypes(yaml, itemParser, aiEnabled);
		Map<String, CivilianGroupConfig> groups = loadGroups(yaml);

		this.config = new EntityMarkerConfig(defaultCivilian, defaultPolice, types, groups, aiEnabled, aiTickRate);
	}

	// ── Loaders ───────────────────────────────────────────────────────────────

	private List<String> loadDefaultEntities(FileConfiguration yaml, String path) {
		return yaml.getStringList(path);
	}

	private Map<String, CivilianTypeConfig> loadTypes(FileConfiguration yaml,
	                                                  @Nullable ItemParser itemParser,
	                                                  boolean globalAiEnabled) {
		Map<String, CivilianTypeConfig> result = new LinkedHashMap<>();

		ConfigurationSection typesSection = yaml.getConfigurationSection("Types");
		if (typesSection == null) return result;

		for (String typeId : typesSection.getKeys(false)) {
			ConfigurationSection section = typesSection.getConfigurationSection(typeId);
			if (section == null) continue;

			String     displayName = section.getString("Display_Name", "&7" + typeId);
			EntityType entityType  = parseEntityType(section.getString("Entity_Type", "VILLAGER"));
			double     health      = section.getDouble("Health", 20.0);
			boolean    hostile     = section.getBoolean("Hostile", false);

			// Wearables
			ConfigurationSection wearSection = section.getConfigurationSection("Wearables");
			CivilianWearableConfig wearables = wearSection == null
			                                   ? new CivilianWearableConfig("", "", "", "")
			                                   : new CivilianWearableConfig(
					wearSection.getString("Helmet", ""),
					wearSection.getString("Chestplate", ""),
					wearSection.getString("Leggings", ""),
					wearSection.getString("Boots", ""));

			// Item pool (general items given on spawn)
			List<String> itemPool = section.getStringList("Item_Pool");

			// Weapon pool (same parsing as cop weapon pool)
			List<String>    weaponNamePool = new ArrayList<>();
			List<ItemStack> weaponPool     = new ArrayList<>();

			for (String entry : section.getStringList("Weapon_Pool")) {
				if (entry == null || entry.isBlank()) continue;

				if (entry.toLowerCase().startsWith("weapon:")) {
					weaponNamePool.add(entry.substring("weapon:".length()).trim());
				} else {
					weaponNamePool.add(entry);
					ItemStack parsed = parseItem(entry, itemParser);
					if (parsed != null) weaponPool.add(parsed);
				}
			}

			// Drops
			ConfigurationSection dropsSection = section.getConfigurationSection("Drops");
			CivilianDropConfig drops = dropsSection == null
			                           ? new CivilianDropConfig(Collections.emptyList(), 0)
			                           : new CivilianDropConfig(
					dropsSection.getStringList("Items"),
					dropsSection.getDouble("Experience", 0));

			// AI behavior
			ConfigurationSection     aiSection = section.getConfigurationSection("AI");
			CivilianAIBehaviorConfig ai        = parseAI(aiSection, typeId);

			// Inventory (optional — trader type)
			CivilianInventoryConfig inventory = parseInventory(section.getConfigurationSection("Inventory"),
			                                                   itemParser);

			result.put(typeId, new CivilianTypeConfig(typeId, displayName, entityType, health, hostile,
			                                          wearables, itemPool, weaponNamePool, weaponPool,
			                                          drops, ai, inventory));
		}

		return result;
	}

	private Map<String, CivilianGroupConfig> loadGroups(FileConfiguration yaml) {
		Map<String, CivilianGroupConfig> result = new LinkedHashMap<>();

		ConfigurationSection groupsSection = yaml.getConfigurationSection("Groups");
		if (groupsSection == null) return result;

		for (String groupId : groupsSection.getKeys(false)) {
			ConfigurationSection section = groupsSection.getConfigurationSection(groupId);
			if (section == null) continue;

			String  displayName       = section.getString("Display_Name", "&7" + groupId);
			boolean hostile           = section.getBoolean("Hostile", false);
			double  healthBonus       = section.getDouble("Health_Bonus", 0.0);
			double  speedBonus        = section.getDouble("Speed_Bonus", 0.0);
			double  stayTogetherRange = section.getDouble("Stay_Together_Range", 20.0);

			Map<String, Integer> members = new LinkedHashMap<>();

			ConfigurationSection membersSection = section.getConfigurationSection("Members");
			if (membersSection != null) {
				for (String typeId : membersSection.getKeys(false)) {
					members.put(typeId, membersSection.getInt(typeId, 1));
				}
			}

			result.put(groupId, new CivilianGroupConfig(groupId, displayName, hostile, healthBonus,
			                                            speedBonus, stayTogetherRange, members));
		}

		return result;
	}

	// ── Parse helpers ─────────────────────────────────────────────────────────

	private CivilianAIBehaviorConfig parseAI(@Nullable ConfigurationSection ai, String typeId) {
		if (ai == null) {
			return new CivilianAIBehaviorConfig(false, 0, false, 0, false, 0.0, 0.0, 0, NpcDifficulty.NORMAL);
		}

		boolean wanderEnabled = ai.getBoolean("Wander.Enabled", false);
		int     wanderRange   = ai.getInt("Wander.Range", 15);

		boolean fleeEnabled = ai.getBoolean("Flee_On_Damage.Enabled", false);
		int     fleeRange   = ai.getInt("Flee_On_Damage.Flee_Range", 15);

		boolean combatEnabled       = ai.getBoolean("Combat.Enabled", false);
		double  attackDamage        = ai.getDouble("Combat.Attack_Damage", 2.0);
		double  attackRange         = ai.getDouble("Combat.Attack_Range", 10.0);
		int     attackIntervalTicks = ai.getInt("Combat.Attack_Interval_Ticks", 20);

		NpcDifficulty difficulty = parseDifficulty(ai.getString("Combat.Difficulty"), "civilian type '" + typeId + "'");

		return new CivilianAIBehaviorConfig(wanderEnabled, wanderRange, fleeEnabled, fleeRange,
		                                    combatEnabled, attackDamage, attackRange, attackIntervalTicks, difficulty);
	}

	/**
	 * Parses a difficulty key from YAML, defaulting to {@link NpcDifficulty#NORMAL} when the value is missing or
	 * unrecognised. Logs a single warning per invalid value so config typos surface during startup.
	 */
	private NpcDifficulty parseDifficulty(@Nullable String raw, String contextLabel) {
		if (raw == null || raw.isBlank()) return NpcDifficulty.NORMAL;
		try {
			return NpcDifficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			log.warn("Unknown NPC difficulty '{}' for {} — defaulting to NORMAL.", raw, contextLabel);
			return NpcDifficulty.NORMAL;
		}
	}

	@Nullable
	private CivilianInventoryConfig parseInventory(@Nullable ConfigurationSection inv,
	                                               @Nullable ItemParser itemParser) {
		if (inv == null) return null;

		String title = inv.getString("Title", "&8Inventory");
		int    size  = inv.getInt("Size", 27);

		Map<Integer, String> slotItems = new LinkedHashMap<>();

		ConfigurationSection itemsSection = inv.getConfigurationSection("Items");
		if (itemsSection != null) {
			for (String slotKey : itemsSection.getKeys(false)) {
				try {
					int    slot  = Integer.parseInt(slotKey);
					String entry = itemsSection.getString(slotKey, "");
					if (!entry.isBlank()) slotItems.put(slot, entry);
				} catch (NumberFormatException ignored) { }
			}
		}

		return new CivilianInventoryConfig(title, size, slotItems);
	}

	private EntityType parseEntityType(String name) {
		try {
			return EntityType.valueOf(name.toUpperCase());
		} catch (IllegalArgumentException e) {
			return EntityType.VILLAGER;
		}
	}

	@Nullable
	private ItemStack parseItem(@Nullable String entry, @Nullable ItemParser itemParser) {
		if (entry == null || entry.isBlank()) return null;

		if (itemParser != null) return itemParser.parse(entry);

		try {
			Optional<XMaterial> xMat = XMaterial.matchXMaterial(entry.toUpperCase());
			if (xMat.isPresent()) {
				Material mat = xMat.get().get();
				if (mat != null) return new ItemStack(mat);
			}
			return new ItemStack(Material.STICK);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
