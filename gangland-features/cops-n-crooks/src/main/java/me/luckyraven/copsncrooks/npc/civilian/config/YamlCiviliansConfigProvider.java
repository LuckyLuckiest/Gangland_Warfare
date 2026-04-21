package me.luckyraven.copsncrooks.npc.civilian.config;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import lombok.Getter;
import me.luckyraven.copsncrooks.npc.NpcDifficulty;
import me.luckyraven.item.ItemParser;
import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.persistence.config.MappingNode;
import me.luckyraven.persistence.config.NodeReader;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Reads {@code civilians.yml} and builds a {@link CiviliansConfig}.
 * <p>
 * Weapon-pool and wearable entries follow the same parsing convention as {@code YamlCopConfigProvider}: entries
 * prefixed with {@code weapon:} go into {@code weaponNamePool} for gangland weapon resolution; all other entries are
 * parsed via {@link ItemParser} into ItemStacks for {@code weaponPool}.
 */
@Getter
@CustomLog
public class YamlCiviliansConfigProvider {

	private final CiviliansConfig config;

	public YamlCiviliansConfigProvider(NodeReader reader, ConfigReport report,
	                                   boolean aiEnabled, int aiTickRate,
	                                   @Nullable ItemParser itemParser) {
		List<String> defaultCivilian = loadDefaultEntities(reader, "Civilian");
		List<String> defaultPolice   = loadDefaultEntities(reader, "Police");

		Map<String, CivilianTypeConfig>  types  = loadTypes(reader, report, itemParser);
		Map<String, CivilianGroupConfig> groups = loadGroups(reader, report);

		this.config = new CiviliansConfig(defaultCivilian, defaultPolice, types, groups, aiEnabled, aiTickRate);
	}

	// ── Loaders ───────────────────────────────────────────────────────────────

	private List<String> loadDefaultEntities(NodeReader reader, String key) {
		MappingNode defaults = reader.get("Default_Entities").asMapping().orNull();
		if (defaults == null) return new ArrayList<>();

		return NodeReader.of(defaults, reader.report()).get(key).asList().ofStrings().orEmpty();
	}

	private Map<String, CivilianTypeConfig> loadTypes(NodeReader reader, ConfigReport report,
	                                                  @Nullable ItemParser itemParser) {
		Map<String, CivilianTypeConfig> result = new LinkedHashMap<>();

		MappingNode typesSection = reader.get("Types").asMapping().orNull();
		if (typesSection == null) return result;

		NodeReader types = NodeReader.of(typesSection, report);

		for (String typeId : types.keys()) {
			MappingNode typeNode = types.get(typeId).asMapping().required().orNull();
			if (typeNode == null) continue;

			NodeReader type = NodeReader.of(typeNode, report);

			String displayName = type.get("Display_Name").asString().orDefault("&7" + typeId);
			EntityType entityType = parseEntityType(
					type.get("Entity_Type").asString().required().orDefault("VILLAGER"));
			double  health  = type.get("Health").asDouble().min(0).orDefault(20.0);
			boolean hostile = type.get("Hostile").asBool().orDefault(false);

			CivilianWearableConfig wearables = parseWearables(type, report);

			List<String> itemPool = type.get("Item_Pool").asList().ofStrings().orEmpty();

			List<String>    weaponNamePool = new ArrayList<>();
			List<ItemStack> weaponPool     = new ArrayList<>();

			for (String entry : type.get("Weapon_Pool").asList().ofStrings().orEmpty()) {
				if (entry == null || entry.isBlank()) continue;

				if (entry.toLowerCase(Locale.ROOT).startsWith("weapon:")) {
					weaponNamePool.add(entry.substring("weapon:".length()).trim());
				} else {
					weaponNamePool.add(entry);
					ItemStack parsed = parseItem(entry, itemParser);
					if (parsed != null) weaponPool.add(parsed);
				}
			}

			CivilianDropConfig drops = parseDrops(type, report);

			CivilianAIBehaviorConfig ai = parseAI(type.get("AI").asMapping().orNull(), report, typeId);

			result.put(typeId, new CivilianTypeConfig(typeId, displayName, entityType, health, hostile,
			                                          wearables, itemPool, weaponNamePool, weaponPool,
			                                          drops, ai));
		}

		return result;
	}

	private Map<String, CivilianGroupConfig> loadGroups(NodeReader reader, ConfigReport report) {
		Map<String, CivilianGroupConfig> result = new LinkedHashMap<>();

		MappingNode groupsSection = reader.get("Groups").asMapping().orNull();
		if (groupsSection == null) return result;

		NodeReader groups = NodeReader.of(groupsSection, report);

		for (String groupId : groups.keys()) {
			MappingNode groupNode = groups.get(groupId).asMapping().required().orNull();
			if (groupNode == null) continue;

			NodeReader group = NodeReader.of(groupNode, report);

			String  displayName       = group.get("Display_Name").asString().orDefault("&7" + groupId);
			boolean hostile           = group.get("Hostile").asBool().orDefault(false);
			double  healthBonus       = group.get("Health_Bonus").asDouble().orDefault(0.0);
			double  speedBonus        = group.get("Speed_Bonus").asDouble().orDefault(0.0);
			double  stayTogetherRange = group.get("Stay_Together_Range").asDouble().min(0).orDefault(20.0);

			Map<String, Integer> members = new LinkedHashMap<>();

			MappingNode membersSection = group.get("Members").asMapping().orNull();
			if (membersSection != null) {
				NodeReader membersReader = NodeReader.of(membersSection, report);

				for (String typeId : membersReader.keys()) {
					members.put(typeId, membersReader.get(typeId).asInt().min(0).orDefault(1));
				}
			}

			result.put(groupId, new CivilianGroupConfig(groupId, displayName, hostile, healthBonus,
			                                            speedBonus, stayTogetherRange, members));
		}

		return result;
	}

	// ── Parse helpers ─────────────────────────────────────────────────────────

	private CivilianWearableConfig parseWearables(NodeReader typeReader, ConfigReport report) {
		MappingNode wearSection = typeReader.get("Wearables").asMapping().orNull();
		if (wearSection == null) return new CivilianWearableConfig("", "", "", "");

		NodeReader wear = NodeReader.of(wearSection, report);

		return new CivilianWearableConfig(
				wear.get("Helmet").asString().orDefault(""),
				wear.get("Chestplate").asString().orDefault(""),
				wear.get("Leggings").asString().orDefault(""),
				wear.get("Boots").asString().orDefault(""));
	}

	private CivilianDropConfig parseDrops(NodeReader typeReader, ConfigReport report) {
		MappingNode dropsSection = typeReader.get("Drops").asMapping().orNull();
		if (dropsSection == null) return new CivilianDropConfig(Collections.emptyList(), 0);

		NodeReader drops = NodeReader.of(dropsSection, report);

		List<CivilianDropConfig.DropEntry> entries = drops.get("Items").asList().ofStrings().orEmpty()
				.stream()
				.map(this::parseDropEntry)
				.toList();

		return new CivilianDropConfig(
				entries,
				drops.get("Experience").asDouble().min(0).orDefault(0.0));
	}

	/**
	 * Parses an optional trailing {@code @<chance>} suffix on a drop entry. {@code "material:BREAD@0.5"} → entry
	 * {@code "material:BREAD"} with chance {@code 0.5}. Missing/malformed suffix defaults to chance {@code 1.0}.
	 */
	private CivilianDropConfig.DropEntry parseDropEntry(String raw) {
		if (raw == null) return new CivilianDropConfig.DropEntry("", 1.0);

		int at = raw.lastIndexOf('@');
		if (at < 0) return new CivilianDropConfig.DropEntry(raw, 1.0);

		String entry  = raw.substring(0, at);
		String suffix = raw.substring(at + 1);
		try {
			double chance = Double.parseDouble(suffix);
			return new CivilianDropConfig.DropEntry(entry, Math.clamp(chance, 0.0, 1.0));
		} catch (NumberFormatException ignored) {
			return new CivilianDropConfig.DropEntry(raw, 1.0);
		}
	}

	private CivilianAIBehaviorConfig parseAI(@Nullable MappingNode aiSection, ConfigReport report, String typeId) {
		if (aiSection == null) {
			return new CivilianAIBehaviorConfig(false, 0, false, 0, false, 0.0, 0.0, 0, NpcDifficulty.NORMAL);
		}

		NodeReader ai = NodeReader.of(aiSection, report);

		boolean wanderEnabled = dottedBool(ai, "Wander", "Enabled", report, false);
		int     wanderRange   = dottedInt(ai, "Wander", "Range", report, 15);

		boolean fleeEnabled = dottedBool(ai, "Flee_On_Damage", "Enabled", report, false);
		int     fleeRange   = dottedInt(ai, "Flee_On_Damage", "Flee_Range", report, 15);

		MappingNode combatSection = ai.get("Combat").asMapping().orNull();
		NodeReader  combat        = combatSection != null ? NodeReader.of(combatSection, report) : null;

		boolean combatEnabled = combat != null && combat.get("Enabled").asBool().orDefault(false);

		double attackDamage;
		double attackRange;
		int    attackIntervalTicks;

		if (combat == null) {
			attackDamage        = 2.0;
			attackRange         = 10.0;
			attackIntervalTicks = 20;
		} else if (combatEnabled) {
			// Combat is on, so these fields must be sensibly set — surface missing/bad values with locations.
			attackDamage        = combat.get("Attack_Damage").asDouble().min(0).required().orDefault(2.0);
			attackRange         = combat.get("Attack_Range").asDouble().min(0).required().orDefault(10.0);
			attackIntervalTicks = combat.get("Attack_Interval_Ticks").asInt().min(1).required().orDefault(20);
		} else {
			// Combat is off — values are inert; don't fire range errors on placeholder zeros.
			attackDamage        = combat.get("Attack_Damage").asDouble().orDefault(2.0);
			attackRange         = combat.get("Attack_Range").asDouble().orDefault(10.0);
			attackIntervalTicks = combat.get("Attack_Interval_Ticks").asInt().orDefault(20);
		}

		NpcDifficulty difficulty = parseDifficulty(combat == null ? null : combat.get("Difficulty").asString().orNull(),
		                                           "civilian type '" + typeId + "'");

		return new CivilianAIBehaviorConfig(wanderEnabled, wanderRange, fleeEnabled, fleeRange,
		                                    combatEnabled, attackDamage, attackRange, attackIntervalTicks, difficulty);
	}

	private boolean dottedBool(NodeReader parent, String section, String key, ConfigReport report, boolean def) {
		MappingNode s = parent.get(section).asMapping().orNull();
		if (s == null) return def;
		return NodeReader.of(s, report).get(key).asBool().orDefault(def);
	}

	private int dottedInt(NodeReader parent, String section, String key, ConfigReport report, int def) {
		MappingNode s = parent.get(section).asMapping().orNull();
		if (s == null) return def;
		return NodeReader.of(s, report).get(key).asInt().orDefault(def);
	}

	private NpcDifficulty parseDifficulty(@Nullable String raw, String contextLabel) {
		if (raw == null || raw.isBlank()) return NpcDifficulty.NORMAL;
		try {
			return NpcDifficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			log.warn("Unknown NPC difficulty '{}' for {} — defaulting to NORMAL.", raw, contextLabel);
			return NpcDifficulty.NORMAL;
		}
	}

	private EntityType parseEntityType(String name) {
		try {
			return EntityType.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return EntityType.VILLAGER;
		}
	}

	@Nullable
	private ItemStack parseItem(@Nullable String entry, @Nullable ItemParser itemParser) {
		if (entry == null || entry.isBlank()) return null;

		if (itemParser != null) return itemParser.parse(entry);

		try {
			Optional<XMaterial> xMat = XMaterial.matchXMaterial(entry.toUpperCase(Locale.ROOT));
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
