package org.luckyraven.gangland.copsncrooks.npc.police.config;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.npc.FieldCareSettings;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.gangland.npc.radio.RadioSettings;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcMeleeProfile;
import org.luckyraven.keystone.item.ItemParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;
import org.luckyraven.keystone.persistence.config.Severity;
import org.luckyraven.keystone.persistence.config.SourceLocation;

import java.util.*;

/**
 * Reads cop configuration from a positional {@link NodeReader}.
 * <p>
 * Armor and weapon-pool entries are resolved through the shared {@link ItemParser} so that custom item syntax
 * ({@code weapon:rifle}, {@code LEATHER_HELMET\{color=blue\}}, etc.) is supported in addition to plain vanilla material
 * names. Cop-count scaling is delegated to {@link CopSettings}, whose implementation lives in {@code gangland-impl} and
 * reads from {@code settings.yml} via {@code SettingAddon}.
 */
@CustomLog
public class YamlCopConfigProvider implements CopConfigProvider {

	/**
	 * Code defaults for a cops.yml with no Tactics blocks (a 0.11 copy is never replaced on upgrade): Cops.Tactics
	 * falls back to a 270-degree arc, and a tier with no Tactics block of its own, under a file with no Cops.Tactics
	 * either, takes its decided arc here (Lieutenant 200, SWAT 270, Military 330). An operator's Cops.Tactics always
	 * wins over these.
	 */
	static final TacticsConfig        COP_TACTICS_DEFAULT = new TacticsConfig(TacticsConfig.DEFAULT.engagement(), 270.0);
	static final Map<Integer, Double> TIER_ARC_DEFAULTS   = Map.of(3, 200.0, 4, 270.0, 5, 330.0);

	/**
	 * Code default for a cops.yml with no Squad_Composition block (every pre-0.13 copy): roles are on, filled in this
	 * order per wanted level from the {@link #builtInRoles built-in catalogue}. A Commander leads from level 3; Assault
	 * comes last, so backup and extra cops past the list are Assault.
	 */
	static final Map<Integer, List<String>> COMPOSITION_DEFAULTS = Map.of(
			1, List.of("Pointman", "Assault"),
			2, List.of("Pointman", "Assault", "Assault"),
			3, List.of("Commander", "Pointman", "Defender", "Marksman", "Assault"),
			4, List.of("Commander", "Pointman", "Defender", "Marksman", "Medic", "Assault"));

	private final Map<Integer, CopTierConfig> tiers;
	private final Map<Integer, Integer>       copsPerWantedLevel;

	private final int    maxCopsPerPlayer;
	private final int    aiTickRate;
	private final int    spawnCheckRate;
	private final double cuffRadius;
	private final int    maxCuffAttempts;
	private final int    cuffCooldownTicks;
	private final double alertRange;
	private final double combatRange;
	private final int    attackCooldownTicks;

	// Spawn settings
	private final double minSpawnDistance;
	private final double maxSpawnDistance;
	private final double phase1MinDistance;
	private final double spawnRadiusShrinkStep;
	private final int    verticalSearchRange;
	private final int    spawnYOffset;
	private final double maxSpawnYDiff;
	private final double spawnerMaxYDiff;
	private final int    minOpenHorizontalSides;
	private final double spawnerPreferenceRadius;
	private final double visibilityCheckDistance;
	private final int    spawnPhase1Attempts;
	private final int    spawnPhase2Attempts;

	// Navigation settings
	private final int    navigationRecalculationTicks;
	private final int    stuckCheckIntervalTicks;
	private final int    maxStuckChecks;
	private final int    maxHopelessStuckChecks;
	private final double hopelessCloseThreshold;
	private final double minProgressDistance;
	private final double rangedMinDistance;
	private final double rangedMaxDistance;
	private final int    minRepathAfterLossTicks;

	// Pursuit leash settings
	private final double pursuitMaxDistance;
	private final int    pursuitMaxTicks;

	// Return / despawn settings
	private final int    maxReturnTicks;
	private final double stationArrivalDistance;

	// Misc
	private final double guardRadius;

	// Squad tactics / melee / radio / backup (phase H12)
	private final NpcMeleeProfile meleeProfile;
	private final TacticsConfig   tacticsDefault;
	private final RadioSettings   radioSettings;
	private final BackupSettings  backupSettings;
	private final RetreatSettings retreatSettings;
	private final FieldCareSettings fieldCareSettings;

	// Roles (phase H13): the composition per wanted level; empty with roles off
	private final TreeMap<Integer, List<CopRole>> compositions = new TreeMap<>();

	/**
	 * Primary positional-config constructor.
	 *
	 * @param copsReader positional reader over the cops.yml root mapping
	 * @param report issue collector drained by the enclosing loader
	 * @param copSettings cop-count-per-wanted-level provider (may be {@code null})
	 * @param itemParser item parser for weapon pool and armor entries (may be {@code null})
	 */
	public YamlCopConfigProvider(NodeReader copsReader, ConfigReport report,
	                             @Nullable CopSettings copSettings, @Nullable ItemParser itemParser) {
		this.tiers              = new LinkedHashMap<>();
		this.copsPerWantedLevel = new LinkedHashMap<>();

		this.maxCopsPerPlayer    = copSettings != null ? copSettings.getMaxCopsPerPlayer() : 8;
		this.aiTickRate          = copSettings != null ? copSettings.getAiTickRate() : 10;
		this.spawnCheckRate      = copSettings != null ? copSettings.getSpawnCheckRate() : 40;
		this.cuffRadius          = copSettings != null ? copSettings.getCuffRadius() : 3.0;
		this.maxCuffAttempts     = copSettings != null ? copSettings.getMaxCuffAttempts() : 3;
		this.cuffCooldownTicks   = copSettings != null ? copSettings.getCuffCooldownTicks() : 100;
		this.alertRange          = copSettings != null ? copSettings.getAlertRange() : 40.0;
		this.combatRange         = copSettings != null ? copSettings.getCombatRange() : 4.0;
		this.attackCooldownTicks = copSettings != null ? copSettings.getAttackCooldownTicks() : 20;

		this.minSpawnDistance        = copSettings != null ? copSettings.getMinSpawnDistance() : 10.0;
		this.maxSpawnDistance        = copSettings != null ? copSettings.getMaxSpawnDistance() : 50.0;
		this.phase1MinDistance       = copSettings != null ? copSettings.getPhase1MinDistance() : 30.0;
		this.spawnRadiusShrinkStep   = copSettings != null ? copSettings.getSpawnRadiusShrinkStep() : 5.0;
		this.verticalSearchRange     = copSettings != null ? copSettings.getVerticalSearchRange() : 10;
		this.spawnYOffset            = copSettings != null ? copSettings.getSpawnYOffset() : 0;
		this.maxSpawnYDiff           = copSettings != null ? copSettings.getMaxSpawnYDiff() : 4.0;
		this.spawnerMaxYDiff         = copSettings != null ? copSettings.getSpawnerMaxYDiff() : 16.0;
		this.minOpenHorizontalSides  = copSettings != null ? copSettings.getMinOpenHorizontalSides() : 2;
		this.spawnerPreferenceRadius = copSettings != null ? copSettings.getSpawnerPreferenceRadius() : 80.0;
		this.visibilityCheckDistance = copSettings != null ? copSettings.getVisibilityCheckDistance() : 48.0;
		this.spawnPhase1Attempts     = copSettings != null ? copSettings.getSpawnPhase1Attempts() : 20;
		this.spawnPhase2Attempts     = copSettings != null ? copSettings.getSpawnPhase2Attempts() : 15;

		this.navigationRecalculationTicks = copSettings != null ? copSettings.getNavigationRecalculationTicks() : 10;
		this.stuckCheckIntervalTicks      = copSettings != null ? copSettings.getStuckCheckIntervalTicks() : 5;
		this.maxStuckChecks               = copSettings != null ? copSettings.getMaxStuckChecks() : 3;
		this.maxHopelessStuckChecks       = copSettings != null ? copSettings.getMaxHopelessStuckChecks() : 6;
		this.hopelessCloseThreshold       = copSettings != null ? copSettings.getHopelessCloseThreshold() : 8.0;
		this.minProgressDistance          = copSettings != null ? copSettings.getMinProgressDistance() : 0.75;
		this.rangedMinDistance            = copSettings != null ? copSettings.getRangedMinDistance() : 7.0;
		this.rangedMaxDistance            = copSettings != null ? copSettings.getRangedMaxDistance() : 12.0;
		this.minRepathAfterLossTicks      = copSettings != null ? copSettings.getMinRepathAfterLossTicks() : 2;

		this.pursuitMaxDistance = copSettings != null ? copSettings.getPursuitMaxDistance() : 80.0;
		this.pursuitMaxTicks    = copSettings != null ? copSettings.getPursuitMaxTicks() : 120;

		this.maxReturnTicks         = copSettings != null ? copSettings.getMaxReturnTicks() : 600;
		this.stationArrivalDistance = copSettings != null ? copSettings.getStationArrivalDistance() : 3.0;

		this.guardRadius = copSettings != null ? copSettings.getGuardRadius() : 5.0;

		MappingNode copsSection = copsReader.get("Cops").asMapping().required().orNull();
		NodeReader  cops        = copsSection != null ? NodeReader.of(copsSection, report) : null;

		this.meleeProfile   = parseMeleeProfile(cops, report);
		this.tacticsDefault = parseTacticsDefault(cops, report);
		this.radioSettings  = parseRadioSettings(cops, report);
		this.backupSettings = parseBackupSettings(cops, report);
		MappingNode retreatSection = cops == null ? null : cops.get("Retreat").asMapping().orNull();
		this.retreatSettings = RetreatSettings.read(retreatSection != null ? NodeReader.of(retreatSection, report) : null,
		                                            report, RetreatSettings.DEFAULT);
		MappingNode careSection = cops == null ? null : cops.get("Field_Care").asMapping().orNull();
		this.fieldCareSettings = FieldCareSettings.read(careSection != null ? NodeReader.of(careSection, report) : null,
		                                                report, FieldCareSettings.DEFAULT);

		loadTiers(cops, report, itemParser);
		loadRoles(cops, report, itemParser);
		buildCopsPerWantedLevel(copSettings);
	}

	@Override
	public CopTierConfig getTierConfig(int tier) {
		int clampedTier = Math.min(tier, getMaxTier());
		return tiers.getOrDefault(clampedTier, tiers.get(getMaxTier()));
	}

	@Override
	public int getMaxTier() {
		return tiers.keySet()
				.stream().mapToInt(Integer::intValue).max().orElse(1);
	}

	@Override
	public Map<Integer, Integer> getCopsPerWantedLevel() {
		return Collections.unmodifiableMap(copsPerWantedLevel);
	}

	@Override
	public int getMaxCopsPerPlayer() {
		return maxCopsPerPlayer;
	}

	@Override
	public int getAiTickRate() {
		return aiTickRate;
	}

	@Override
	public int getSpawnCheckRate() {
		return spawnCheckRate;
	}

	@Override
	public double getCuffRadius() {
		return cuffRadius;
	}

	@Override
	public int getMaxCuffAttempts() {
		return maxCuffAttempts;
	}

	@Override
	public int getCuffCooldownTicks() {
		return cuffCooldownTicks;
	}

	@Override
	public double getAlertRange() {
		return alertRange;
	}

	@Override
	public double getCombatRange() {
		return combatRange;
	}

	@Override
	public int getAttackCooldownTicks() {
		return attackCooldownTicks;
	}

	@Override
	public double getMinSpawnDistance() {
		return minSpawnDistance;
	}

	@Override
	public double getMaxSpawnDistance() {
		return maxSpawnDistance;
	}

	@Override
	public double getPhase1MinDistance() {
		return phase1MinDistance;
	}

	@Override
	public double getSpawnRadiusShrinkStep() {
		return spawnRadiusShrinkStep;
	}

	@Override
	public int getVerticalSearchRange() {
		return verticalSearchRange;
	}

	@Override
	public int getSpawnYOffset() {
		return spawnYOffset;
	}

	@Override
	public double getMaxSpawnYDiff() {
		return maxSpawnYDiff;
	}

	@Override
	public double getSpawnerMaxYDiff() {
		return spawnerMaxYDiff;
	}

	@Override
	public int getMinOpenHorizontalSides() {
		return minOpenHorizontalSides;
	}

	@Override
	public double getSpawnerPreferenceRadius() {
		return spawnerPreferenceRadius;
	}

	@Override
	public double getVisibilityCheckDistance() {
		return visibilityCheckDistance;
	}

	@Override
	public int getSpawnPhase1Attempts() {
		return spawnPhase1Attempts;
	}

	@Override
	public int getSpawnPhase2Attempts() {
		return spawnPhase2Attempts;
	}

	@Override
	public int getNavigationRecalculationTicks() {
		return navigationRecalculationTicks;
	}

	@Override
	public int getStuckCheckIntervalTicks() {
		return stuckCheckIntervalTicks;
	}

	@Override
	public int getMaxStuckChecks() {
		return maxStuckChecks;
	}

	@Override
	public int getMaxHopelessStuckChecks() {
		return maxHopelessStuckChecks;
	}

	@Override
	public double getHopelessCloseThreshold() {
		return hopelessCloseThreshold;
	}

	@Override
	public double getMinProgressDistance() {
		return minProgressDistance;
	}

	@Override
	public double getRangedMinDistance() {
		return rangedMinDistance;
	}

	@Override
	public double getRangedMaxDistance() {
		return rangedMaxDistance;
	}

	@Override
	public int getMinRepathAfterLossTicks() {
		return minRepathAfterLossTicks;
	}

	@Override
	public double getPursuitMaxDistance() {
		return pursuitMaxDistance;
	}

	@Override
	public int getPursuitMaxTicks() {
		return pursuitMaxTicks;
	}

	@Override
	public int getMaxReturnTicks() {
		return maxReturnTicks;
	}

	@Override
	public double getStationArrivalDistance() {
		return stationArrivalDistance;
	}

	@Override
	public double getGuardRadius() {
		return guardRadius;
	}

	@Override
	public NpcMeleeProfile getMeleeProfile() {
		return meleeProfile;
	}

	@Override
	public RadioSettings getRadioSettings() {
		return radioSettings;
	}

	@Override
	public BackupSettings getBackupSettings() {
		return backupSettings;
	}

	@Override
	public RetreatSettings getRetreatSettings() {
		return retreatSettings;
	}

	@Override
	public FieldCareSettings getFieldCareSettings() {
		return fieldCareSettings;
	}

	@Override
	public @Nullable List<CopRole> getSquadComposition(int wantedLevel) {
		Map.Entry<Integer, List<CopRole>> entry = compositions.floorEntry(wantedLevel);
		return entry != null ? entry.getValue() : null;
	}

	/**
	 * The built-in role catalogue: what an old cops.yml gets, and what a {@code Cops.Roles.<Name>} entry of the same
	 * name is read over. Each role's retreat threshold is laid over {@code retreat} ({@code Cops.Retreat}).
	 */
	static Map<String, CopRole> builtInRoles(RetreatSettings retreat) {
		Map<String, CopRole> roles = new LinkedHashMap<>();
		// front and centre, close in; leads (radio voice) whenever no Commander is on the squad
		roles.put("Pointman", new CopRole("Pointman", "Pointman", NpcFanPlacement.CENTER, 4.0, 8.0, 1.0, null, 1, null,
		                                  1.0, 0, null, 0, 60, false, false));
		// the fan's ends, pushing and strafing wide
		roles.put("Assault", new CopRole("Assault", "Assault", NpcFanPlacement.FLANK, 5.0, 9.0, 1.0, null, 0, 20.0, 1.0,
		                                 0, retreatAt(retreat, 0.25), 0, 60, false, false));
		// holds the centre post up front behind a shield that takes half of every hit from the front
		roles.put("Defender", new CopRole("Defender", "Defender", NpcFanPlacement.CENTER, 4.0, 7.0, 1.5,
		                                  new ItemStack(Material.SHIELD), 0, 0.0, 1.0, 0, retreatAt(retreat, 0.15), 0.5,
		                                  60, false, false));
		// the back of the fan (band clamped under the weapon's reach at spawn), slower but surer shots
		roles.put("Marksman", new CopRole("Marksman", "Marksman", NpcFanPlacement.ANY, 14.0, 22.0, 1.0, null, 0, null,
		                                  0.6, 1, retreatAt(retreat, 0.4), 0, 60, false, false));
		roles.put("Medic", new CopRole("Medic", "Medic", NpcFanPlacement.CENTER, 8.0, 12.0, 1.0, null, 0, null, 1.0, 0,
		                               retreatAt(retreat, 0.5), 0, 60, true, false));
		// leads from the rear of the band; the squad falls back briefly when it goes down
		roles.put("Commander", new CopRole("Commander", "Commander", NpcFanPlacement.ANY, 10.0, 14.0, 1.0, null, 2,
		                                   null, 1.0, 0, retreatAt(retreat, 0.4), 0, 60, false, true));
		return roles;
	}

	private static RetreatSettings retreatAt(RetreatSettings retreat, double healthFraction) {
		return new RetreatSettings(retreat.enabled(), healthFraction, retreat.radius());
	}

	/**
	 * {@code Cops.Roles} read over {@link #builtInRoles}, then {@code Cops.Squad_Composition} (or
	 * {@link #COMPOSITION_DEFAULTS} without one). {@code Cops.Roles_Enabled: false} turns roles off.
	 */
	private void loadRoles(@Nullable NodeReader cops, ConfigReport report, @Nullable ItemParser itemParser) {
		if (cops != null && !cops.get("Roles_Enabled").asBool().orDefault(true)) {
			// read but unused: switching roles off must not turn the shipped blocks into unknown keys
			cops.get("Roles");
			cops.get("Squad_Composition");
			return;
		}

		Map<String, CopRole> roles        = builtInRoles(retreatSettings);
		MappingNode          rolesSection = cops == null ? null : cops.get("Roles").asMapping().orNull();
		if (rolesSection != null) {
			NodeReader rolesReader = NodeReader.of(rolesSection, report);
			for (String name : rolesReader.keys()) {
				MappingNode roleNode = rolesReader.get(name).asMapping().required().orNull();
				if (roleNode == null) continue;
				CopRole base = roles.getOrDefault(name, new CopRole(name, name, NpcFanPlacement.ANY, null, null, 1.0,
				                                                    null, 0, null, 1.0, 0, null, 0, 60, false, false));
				roles.put(name, readRole(NodeReader.of(roleNode, report), report, base, itemParser));
			}
		}

		MappingNode compositionSection = cops == null ? null : cops.get("Squad_Composition").asMapping().orNull();
		if (compositionSection == null) {
			COMPOSITION_DEFAULTS.forEach((level, names) -> compositions.put(level, names.stream().map(roles::get).toList()));
			return;
		}

		NodeReader composition = NodeReader.of(compositionSection, report);
		for (String key : composition.keys()) {
			NodeReader.NodeAccess access = composition.get(key);
			int                   level;
			try {
				level = Integer.parseInt(key.trim());
			} catch (NumberFormatException e) {
				report.add(Severity.WARNING, locationOf(access, composition), "Cops.Squad_Composition." + key,
				           "wanted level '" + key + "' is not a number, skipped", "config.type");
				continue;
			}

			List<CopRole> list = new ArrayList<>();
			for (String name : access.asList().ofStrings().orEmpty()) {
				CopRole role = name != null ? roles.get(name.trim()) : null;
				if (role != null) list.add(role);
				else report.add(Severity.WARNING, locationOf(access, composition), "Cops.Squad_Composition." + key,
				                "unknown role '" + name + "' skipped (not under Cops.Roles or built in)",
				                "config.unknown_role");
			}
			if (!list.isEmpty()) compositions.put(level, List.copyOf(list));
		}
	}

	/** One {@code Cops.Roles.<Name>} entry, each absent or invalid key keeping {@code base}'s value. */
	private CopRole readRole(NodeReader role, ConfigReport report, CopRole base, @Nullable ItemParser itemParser) {
		NpcFanPlacement placement = base.placement();
		String          rawPlacement = role.get("Fan_Placement").asString().orNull();
		if (rawPlacement != null) {
			try {
				placement = NpcFanPlacement.valueOf(rawPlacement.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				report.add(Severity.WARNING, locationOf(role.get("Fan_Placement"), role), "Fan_Placement",
				           "unknown Fan_Placement '" + rawPlacement + "' (ANY, CENTER or FLANK), ignored",
				           "config.enum");
			}
		}

		MappingNode     retreatNode = role.get("Retreat").asMapping().orNull();
		RetreatSettings retreat     = retreatNode == null ? base.retreat()
		                              : RetreatSettings.read(NodeReader.of(retreatNode, report), report,
		                                                     base.retreat() != null ? base.retreat() : retreatSettings);

		return new CopRole(base.name(),
		                   role.get("Display_Name").asString().orDefault(base.displayName()),
		                   placement,
		                   optionalDouble(role, "Ranged_Min_Distance", 0, 64, base.rangedMin()),
		                   optionalDouble(role, "Ranged_Max_Distance", 0, 64, base.rangedMax()),
		                   role.get("Health_Multiplier").asDouble().min(0.1).max(10).orDefault(base.healthMultiplier()),
		                   role.has("Off_Hand") ? parseItem(role.get("Off_Hand").asString().orNull(), itemParser)
		                                        : base.offHand(),
		                   role.get("Leader_Priority").asInt().orDefault(base.leaderPriority()),
		                   optionalDouble(role, "Strafe_Degrees", 0, 180, base.strafeDegrees()),
		                   role.get("Fire_Rate_Scale").asDouble().min(0.05).max(10).orDefault(base.fireRateScale()),
		                   role.get("Difficulty_Bonus").asInt().min(0).max(3).orDefault(base.difficultyBonus()),
		                   retreat,
		                   role.get("Block_Fraction").asDouble().min(0).max(1).orDefault(base.blockFraction()),
		                   role.get("Block_Cone_Degrees").asDouble().min(0).max(360).orDefault(base.blockConeDegrees()),
		                   role.get("Medic").asBool().orDefault(base.medic()),
		                   role.get("Commander").asBool().orDefault(base.commander()));
	}

	/** A number that may stay unset: {@code def} when absent or out of {@code [min, max]} (reported). */
	private static @Nullable Double optionalDouble(NodeReader node, String key, double min, double max,
	                                               @Nullable Double def) {
		if (!node.has(key)) return def;
		double value = node.get(key).asDouble().min(min).max(max).orDefault(Double.NaN);
		return Double.isNaN(value) ? def : value;
	}

	private static SourceLocation locationOf(NodeReader.NodeAccess access, NodeReader parent) {
		return access.node() != null ? access.node().location() : parent.mapping().location();
	}

	private NpcMeleeProfile parseMeleeProfile(@Nullable NodeReader cops, ConfigReport report) {
		NpcMeleeProfile defaults = NpcMeleeProfile.DEFAULT;
		MappingNode meleeSection = cops == null ? null : cops.get("Melee").asMapping().orNull();
		if (meleeSection == null) {
			return new NpcMeleeProfile(defaults.reach(), defaults.approach(), attackCooldownTicks,
			                           defaults.damageSpread(), defaults.edgeDamage());
		}

		NodeReader melee = NodeReader.of(meleeSection, report);
		return new NpcMeleeProfile(
				melee.get("Reach").asDouble().min(0).orDefault(defaults.reach()),
				melee.get("Approach").asDouble().min(0).orDefault(defaults.approach()),
				attackCooldownTicks,
				melee.get("Damage_Spread").asDouble().orDefault(defaults.damageSpread()),
				melee.get("Edge_Damage").asDouble().orDefault(defaults.edgeDamage()));
	}

	private TacticsConfig parseTacticsDefault(@Nullable NodeReader cops, ConfigReport report) {
		MappingNode tacticsSection = cops == null ? null : cops.get("Tactics").asMapping().orNull();
		NodeReader  tactics        = tacticsSection != null ? NodeReader.of(tacticsSection, report) : null;
		return TacticsConfig.read(tactics, report, COP_TACTICS_DEFAULT);
	}

	private RadioSettings parseRadioSettings(@Nullable NodeReader cops, ConfigReport report) {
		MappingNode radioSection = cops == null ? null : cops.get("Radio").asMapping().orNull();
		NodeReader  radio        = radioSection != null ? NodeReader.of(radioSection, report) : null;
		return RadioSettings.read(radio, report, COP_RADIO_DEFAULTS);
	}

	private BackupSettings parseBackupSettings(@Nullable NodeReader cops, ConfigReport report) {
		BackupSettings defaults     = BackupSettings.DEFAULT;
		MappingNode    backupSection = cops == null ? null : cops.get("Backup").asMapping().orNull();
		if (backupSection == null) return defaults;

		NodeReader backup = NodeReader.of(backupSection, report);
		boolean    enabled   = backup.get("Enabled").asBool().orDefault(defaults.enabled());
		int        extraCops = backup.get("Extra_Cops").asInt().min(0).orDefault(defaults.extraCops());
		long durationTicks = backup.get("Duration_Ticks").asInt().min(0)
				.orDefault((int) (defaults.durationMs() / 50L));
		long cooldownTicks = backup.get("Cooldown_Ticks").asInt().min(0)
				.orDefault((int) (defaults.cooldownMs() / 50L));

		return new BackupSettings(enabled, extraCops, durationTicks * 50L, cooldownTicks * 50L);
	}

	private void loadTiers(@Nullable NodeReader cops, ConfigReport report, @Nullable ItemParser itemParser) {
		if (cops == null) return;

		MappingNode tiersSection = cops.get("Tiers").asMapping().required().orNull();
		if (tiersSection == null) return;

		NodeReader tiersReader = NodeReader.of(tiersSection, report);
		boolean    hasCopsTactics = cops.get("Tactics").asMapping().orNull() != null;

		for (String key : tiersReader.keys()) {
			MappingNode tierNode = tiersReader.get(key).asMapping().required().orNull();
			if (tierNode == null) continue;

			int tierNum;
			try {
				tierNum = Integer.parseInt(key);
			} catch (NumberFormatException e) {
				log.warn("Cop tier key '{}' is not an integer — skipping", key);
				continue;
			}

			NodeReader tier = NodeReader.of(tierNode, report);

			List<String>    weaponNamePool = new ArrayList<>();
			List<ItemStack> weaponPool     = new ArrayList<>();

			for (String entry : tier.get("Weapon_Pool").asList().ofStrings().orEmpty()) {
				if (entry == null || entry.isBlank()) continue;

				if (entry.toLowerCase(Locale.ROOT).startsWith("weapon:")) {
					weaponNamePool.add(entry.substring("weapon:".length()).trim());
				} else {
					weaponNamePool.add(entry);
					ItemStack parsed = parseItem(entry, itemParser);
					if (parsed != null) weaponPool.add(parsed);
				}
			}

			String difficultyStr = tier.get("Difficulty").asString().orNull();

			MappingNode wearSection = tier.get("Wearables").asMapping().orNull();
			NodeReader  wear        = wearSection != null ? NodeReader.of(wearSection, report) : null;

			MappingNode tierTacticsSection = tier.get("Tactics").asMapping().orNull();
			NodeReader  tierTactics = tierTacticsSection != null ? NodeReader.of(tierTacticsSection, report) : null;

			CopTierConfig tierConfig = new CopTierConfig(
					tierNum,
					tier.get("Display_Name").asString().required().orDefault("&9Police"),
					tier.get("Health").asDouble().min(0).required().orDefault(20.0),
					tier.get("Damage").asDouble().min(0).required().orDefault(2.0),
					tier.get("Speed").asDouble().min(0).orDefault(1.0),
					tier.get("Cuff_Radius").asDouble().min(0).orDefault(cuffRadius),
					tier.get("Can_Use_Weapons").asBool().orDefault(false),
					tier.get("Skip_Cuffing").asBool().orDefault(false),
					weaponNamePool, weaponPool,
					parseItem(wear == null ? null : wear.get("Helmet").asString().orNull(), itemParser),
					parseItem(wear == null ? null : wear.get("Chestplate").asString().orNull(), itemParser),
					parseItem(wear == null ? null : wear.get("Leggings").asString().orNull(), itemParser),
					parseItem(wear == null ? null : wear.get("Boots").asString().orNull(), itemParser),
					parseDifficulty(difficultyStr, "tier " + tierNum),
					TacticsConfig.read(tierTactics, report, hasCopsTactics ? tacticsDefault
							: new TacticsConfig(tacticsDefault.engagement(),
							                    TIER_ARC_DEFAULTS.getOrDefault(tierNum, tacticsDefault.formationArc()))),
					// no key: one weapon tick per AI tick, the cadence cops had before 1.13 moved guns onto server ticks
					tier.get("Fire_Rate_Multiplier").asDouble().min(0.01).orDefault(1.0 / Math.max(1, aiTickRate)));

			tiers.put(tierNum, tierConfig);
		}
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

	private void buildCopsPerWantedLevel(@Nullable CopSettings copSettings) {
		if (copSettings == null) {
			for (int level = 1; level <= 5; level++) {
				copsPerWantedLevel.put(level, Math.min(1 + level, maxCopsPerPlayer));
			}
			return;
		}

		for (int level = 1; level <= copSettings.getMaxWantedLevel(); level++) {
			copsPerWantedLevel.put(level, copSettings.getCountForLevel(level));
		}
	}

	@Nullable
	private ItemStack parseItem(@Nullable String entry, @Nullable ItemParser itemParser) {
		if (entry == null || entry.isBlank()) return null;

		if (itemParser != null) return itemParser.parse(entry);

		try {
			Optional<XMaterial> xMaterial = XMaterial.matchXMaterial(entry.toUpperCase(Locale.ROOT));
			if (xMaterial.isPresent()) {
				Material mat = xMaterial.get().get();
				if (mat != null) return new ItemStack(mat);
			}

			return new ItemStack(Material.STICK);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

}
