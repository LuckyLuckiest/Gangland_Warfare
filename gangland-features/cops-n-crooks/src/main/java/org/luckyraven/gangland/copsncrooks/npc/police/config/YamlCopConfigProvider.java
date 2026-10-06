package org.luckyraven.gangland.copsncrooks.npc.police.config;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole.Gear;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole.Kit;
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
	 * Code default for a cop_roles.yml with no Squad_Composition block (or no such file): roles are on, filled in this
	 * order per wanted level from the {@link #builtInRoles built-in catalogue}. A Commander leads from level 3; Assault
	 * comes last, so backup and extra cops past the list are Assault.
	 */
	static final Map<Integer, List<String>> COMPOSITION_DEFAULTS = Map.of(
			1, List.of("Pointman", "Assault"),
			2, List.of("Pointman", "Assault", "Assault"),
			3, List.of("Commander", "Pointman", "Defender", "Marksman", "Assault"),
			4, List.of("Commander", "Pointman", "Defender", "Marksman", "Medic", "Assault"));

	/** The built-in roles' leather dyes (the vanilla dye colours). */
	private static final Color RED   = Color.fromRGB(0xB02E26);
	private static final Color WHITE = Color.fromRGB(0xF9FFFE);
	private static final Color GREEN = Color.fromRGB(0x5E7C16);
	private static final Color BLUE  = Color.fromRGB(0x3C44AA);
	private static final Color BLACK = Color.fromRGB(0x1D1D21);

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
	private final RegroupSettings regroupSettings;
	private final ShotNoiseSettings shotNoiseSettings;
	private final RetreatSettings retreatSettings;
	private final CopNames        names;
	private final StuckSettings   stuckSettings;
	private final FieldCareSettings fieldCareSettings;

	// Roles (phase H13): the composition per wanted level; empty with roles off
	private final TreeMap<Integer, List<CopRole>> compositions = new TreeMap<>();

	/** {@link #YamlCopConfigProvider(NodeReader, NodeReader, ConfigReport, CopSettings, ItemParser)} with no roles file. */
	public YamlCopConfigProvider(NodeReader copsReader, ConfigReport report,
	                             @Nullable CopSettings copSettings, @Nullable ItemParser itemParser) {
		this(copsReader, null, report, copSettings, itemParser);
	}

	/**
	 * Primary positional-config constructor.
	 *
	 * @param copsReader positional reader over the cops.yml root mapping
	 * @param rolesReader positional reader over the copsncrooks/cop_roles.yml root mapping; {@code null} (no file) gives the
	 * 		built-in roles and compositions
	 * @param report issue collector drained by the enclosing loader
	 * @param copSettings cop-count-per-wanted-level provider (may be {@code null})
	 * @param itemParser item parser for weapon pool and armor entries (may be {@code null})
	 */
	public YamlCopConfigProvider(NodeReader copsReader, @Nullable NodeReader rolesReader, ConfigReport report,
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
		this.regroupSettings = parseRegroupSettings(cops, report);
		this.shotNoiseSettings = parseShotNoiseSettings(cops, report);
		MappingNode retreatSection = cops == null ? null : cops.get("Retreat").asMapping().orNull();
		this.retreatSettings = RetreatSettings.read(retreatSection != null ? NodeReader.of(retreatSection, report) : null,
		                                            report, RetreatSettings.DEFAULT);
		this.names = parseNames(cops, report);
		MappingNode stuckSection = cops == null ? null : cops.get("Stuck").asMapping().orNull();
		this.stuckSettings = StuckSettings.read(stuckSection != null ? NodeReader.of(stuckSection, report) : null);
		MappingNode careSection = cops == null ? null : cops.get("Field_Care").asMapping().orNull();
		this.fieldCareSettings = FieldCareSettings.read(careSection != null ? NodeReader.of(careSection, report) : null,
		                                                report, FieldCareSettings.DEFAULT);

		loadTiers(cops, report, itemParser);
		loadRoles(rolesReader, report, itemParser);
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
	public RegroupSettings getRegroupSettings() {
		return regroupSettings;
	}

	@Override
	public ShotNoiseSettings getShotNoiseSettings() {
		return shotNoiseSettings;
	}

	@Override
	public RetreatSettings getRetreatSettings() {
		return retreatSettings;
	}

	@Override
	public CopNames getNames() {
		return names;
	}

	@Override
	public StuckSettings getStuckSettings() {
		return stuckSettings;
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
	 * The built-in role catalogue: what a server without copsncrooks/cop_roles.yml gets, and what a {@code Roles.<Name>} entry
	 * of the same name is read over. Each role's retreat threshold is laid over {@code retreat} ({@code Cops.Retreat}).
	 * Each role keeps its identity piece (a dyed leather or gold helmet, the Medic's apple, the Defender's shield) on
	 * every tier; its tier kits carry the armour and the gun up the levels (Lieutenant chain/iron, SWAT iron/diamond,
	 * Military diamond/netherite). Only leather takes a dye, so a role-coloured piece stays leather and glints from SWAT
	 * up. The shipped cop_roles.yml spells out exactly this.
	 */
	public static Map<String, CopRole> builtInRoles(RetreatSettings retreat) {
		Map<String, CopRole> roles = new LinkedHashMap<>();
		// front and centre, close in; leads (radio voice) whenever no Commander is on the squad. Blue leather, a rifle
		roles.put("Pointman", new CopRole("Pointman", "Pointman", NpcFanPlacement.CENTER, 4.0, 8.0, 1.0, 1, null, 1.0,
		                                  0, null, 0, 60, false, false, "&e", "\u27A4",
		                                  kit(List.of("rifle"), leather(Material.LEATHER_HELMET, BLUE),
		                                      leather(Material.LEATHER_CHESTPLATE, BLUE), null, null, null),
		                                  tierBodies(glint(Material.LEATHER_HELMET, BLUE), null, List.of("steyr_aug"),
		                                             List.of("steyr_aug"))));
		// the fan's ends, pushing and strafing wide. A black balaclava over chainmail, an MP5
		roles.put("Assault", new CopRole("Assault", "Assault", NpcFanPlacement.FLANK, 5.0, 9.0, 1.0, 0, 20.0, 1.0, 0,
		                                 retreatAt(retreat, 0.25), 0, 60, false, false, "&4", "\u2694",
		                                 kit(List.of("mp5"), leather(Material.LEATHER_HELMET, BLACK),
		                                     piece(Material.CHAINMAIL_CHESTPLATE), piece(Material.CHAINMAIL_LEGGINGS),
		                                     null, null),
		                                 tierBodies(glint(Material.LEATHER_HELMET, BLACK), null,
		                                            List.of("steyr_aug"), List.of("golden_ak47"))));
		// holds the centre post up front behind a shield that takes half of every hit from the front; the heaviest
		// armour of the squad, a shotgun
		roles.put("Defender", new CopRole("Defender", "Defender", NpcFanPlacement.CENTER, 4.0, 7.0, 1.5, 0, 0.0, 1.0,
		                                  0, retreatAt(retreat, 0.15), 0.5, 60, false, false, "&9", "\u26E8",
		                                  kit(List.of("shotgun"), piece(Material.IRON_HELMET),
		                                      piece(Material.IRON_CHESTPLATE), piece(Material.IRON_LEGGINGS),
		                                      piece(Material.IRON_BOOTS), piece(Material.SHIELD)),
		                                  Map.of(4, kit(null, piece(Material.DIAMOND_HELMET),
		                                                piece(Material.DIAMOND_CHESTPLATE),
		                                                piece(Material.DIAMOND_LEGGINGS), null, null),
		                                         5, kit(List.of("sawn_off", "shotgun"), piece(Material.DIAMOND_HELMET),
		                                                piece(Material.NETHERITE_CHESTPLATE),
		                                                piece(Material.NETHERITE_LEGGINGS),
		                                                piece(Material.DIAMOND_BOOTS), null))));
		// far behind the fan (band clamped under the weapon's reach at spawn: scout 100, awp 120), slower but surer
		// shots. A green ghillie: hood and leggings stay leather on every tier
		roles.put("Marksman", new CopRole("Marksman", "Marksman", NpcFanPlacement.ANY, 22.0, 32.0, 1.0, 0, null, 0.6,
		                                  1, retreatAt(retreat, 0.4), 0, 60, false, false, "&2", "\u2316",
		                                  kit(List.of("scout"), leather(Material.LEATHER_HELMET, GREEN),
		                                      leather(Material.LEATHER_CHESTPLATE, GREEN),
		                                      leather(Material.LEATHER_LEGGINGS, GREEN),
		                                      leather(Material.LEATHER_BOOTS, GREEN), null),
		                                  Map.of(3, kit(null, null, piece(Material.IRON_CHESTPLATE), null,
		                                                piece(Material.CHAINMAIL_BOOTS), null),
		                                         4, kit(null, glint(Material.LEATHER_HELMET, GREEN),
		                                                piece(Material.DIAMOND_CHESTPLATE),
		                                                glint(Material.LEATHER_LEGGINGS, GREEN),
		                                                piece(Material.IRON_BOOTS), null),
		                                         5, kit(List.of("awp"), glint(Material.LEATHER_HELMET, GREEN),
		                                                piece(Material.NETHERITE_CHESTPLATE),
		                                                glint(Material.LEATHER_LEGGINGS, GREEN),
		                                                piece(Material.DIAMOND_BOOTS), null))));
		// patches up hurt squad mates. Red cap and white leather (the cap and boots stay on every tier), a golden apple
		// in the off hand, the weakest gun of the squad: an MP5 at Military, under every other role's
		roles.put("Medic", new CopRole("Medic", "Medic", NpcFanPlacement.CENTER, 8.0, 12.0, 1.0, 0, null, 1.0, 0,
		                               retreatAt(retreat, 0.5), 0, 60, true, false, "&c", "\u271A",
		                               kit(List.of("pistol"), leather(Material.LEATHER_HELMET, RED),
		                                   leather(Material.LEATHER_CHESTPLATE, WHITE),
		                                   leather(Material.LEATHER_LEGGINGS, WHITE),
		                                   leather(Material.LEATHER_BOOTS, WHITE), piece(Material.GOLDEN_APPLE)),
		                               Map.of(3, kit(List.of("revolver"), null, piece(Material.IRON_CHESTPLATE),
		                                             piece(Material.CHAINMAIL_LEGGINGS), null, null),
		                                      4, kit(List.of("mp5"), glint(Material.LEATHER_HELMET, RED),
		                                             piece(Material.DIAMOND_CHESTPLATE),
		                                             piece(Material.IRON_LEGGINGS), glint(Material.LEATHER_BOOTS, WHITE),
		                                             null),
		                                      5, kit(List.of("mp5"), glint(Material.LEATHER_HELMET, RED),
		                                             piece(Material.NETHERITE_CHESTPLATE),
		                                             piece(Material.DIAMOND_LEGGINGS),
		                                             glint(Material.LEATHER_BOOTS, WHITE), null))));
		// leads from the rear of the band; the squad falls back briefly when it goes down. A gold helmet, a revolver
		roles.put("Commander", new CopRole("Commander", "Commander", NpcFanPlacement.ANY, 10.0, 14.0, 1.0, 2, null,
		                                   1.0, 0, retreatAt(retreat, 0.4), 0, 60, false, true, "&6", "\u2605",
		                                   kit(List.of("revolver"), new Gear(Material.GOLDEN_HELMET, null, true),
		                                       piece(Material.IRON_CHESTPLATE), piece(Material.CHAINMAIL_LEGGINGS),
		                                       null, null),
		                                   tierBodies(null, null, null, List.of("golden_ak47"))));
		return roles;
	}

	private static Gear piece(Material material) {
		return new Gear(material, null, false);
	}

	private static Gear leather(Material material, Color color) {
		return new Gear(material, color, false);
	}

	/** A dyed leather piece with an enchantment glint: a role colour at SWAT and Military. */
	private static Gear glint(Material material, Color color) {
		return new Gear(material, color, true);
	}

	private static Kit kit(@Nullable List<String> guns, @Nullable Gear helmet, @Nullable Gear chestplate,
	                       @Nullable Gear leggings, @Nullable Gear boots, @Nullable Gear offHand) {
		return new Kit(guns, List.of(), helmet, chestplate, leggings, boots, offHand);
	}

	/**
	 * The common body armour per gun tier (3 chain/iron, 4 iron/diamond, 5 diamond/netherite) and its guns;
	 * {@code cap} is the helmet at 4 and 5 ({@code null}: the role's own).
	 */
	private static Map<Integer, Kit> tierBodies(@Nullable Gear cap, @Nullable List<String> guns3,
	                                            @Nullable List<String> guns4, @Nullable List<String> guns5) {
		return Map.of(3, kit(guns3, null, piece(Material.IRON_CHESTPLATE), piece(Material.CHAINMAIL_LEGGINGS),
		                     piece(Material.CHAINMAIL_BOOTS), null),
		              4, kit(guns4, cap, piece(Material.DIAMOND_CHESTPLATE), piece(Material.IRON_LEGGINGS),
		                     piece(Material.IRON_BOOTS), null),
		              5, kit(guns5, cap, piece(Material.NETHERITE_CHESTPLATE), piece(Material.DIAMOND_LEGGINGS),
		                     piece(Material.DIAMOND_BOOTS), null));
	}

	private static RetreatSettings retreatAt(RetreatSettings retreat, double healthFraction) {
		return new RetreatSettings(retreat.enabled(), healthFraction, retreat.radius());
	}

	/**
	 * {@code Roles} (copsncrooks/cop_roles.yml) read over {@link #builtInRoles}, then {@code Squad_Composition} (or
	 * {@link #COMPOSITION_DEFAULTS} without one). {@code Roles_Enabled: false} turns roles off. No file: the built-ins.
	 */
	private void loadRoles(@Nullable NodeReader file, ConfigReport report, @Nullable ItemParser itemParser) {
		if (file != null && !file.get("Roles_Enabled").asBool().orDefault(true)) {
			// read but unused: switching roles off must not turn the shipped blocks into unknown keys
			file.get("Roles");
			file.get("Squad_Composition");
			return;
		}

		Map<String, CopRole> roles        = builtInRoles(retreatSettings);
		MappingNode          rolesSection = file == null ? null : file.get("Roles").asMapping().orNull();
		if (rolesSection != null) {
			NodeReader rolesReader = NodeReader.of(rolesSection, report);
			for (String name : rolesReader.keys()) {
				MappingNode roleNode = rolesReader.get(name).asMapping().required().orNull();
				if (roleNode == null) continue;
				CopRole base = roles.getOrDefault(name, new CopRole(name, name, NpcFanPlacement.ANY, null, null, 1.0, 0,
				                                                    null, 1.0, 0, null, 0, 60, false, false, "", "",
				                                                    Kit.EMPTY, Map.of()));
				roles.put(name, readRole(NodeReader.of(roleNode, report), report, base, itemParser));
			}
		}

		MappingNode compositionSection = file == null ? null : file.get("Squad_Composition").asMapping().orNull();
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
				report.add(Severity.WARNING, locationOf(access, composition), "Squad_Composition." + key,
				           "wanted level '" + key + "' is not a number, skipped", "config.type");
				continue;
			}

			List<CopRole> list = new ArrayList<>();
			for (String name : access.asList().ofStrings().orEmpty()) {
				CopRole role = name != null ? roles.get(name.trim()) : null;
				if (role != null) list.add(role);
				else report.add(Severity.WARNING, locationOf(access, composition), "Squad_Composition." + key,
				                "unknown role '" + name + "' skipped (not under Roles or built in)",
				                "config.unknown_role");
			}
			if (!list.isEmpty()) compositions.put(level, List.copyOf(list));
		}
	}

	/** One {@code Roles.<Name>} entry, each absent or invalid key keeping {@code base}'s value. */
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

		MappingNode displayNode = role.get("Display").asMapping().orNull();
		NodeReader  display     = displayNode != null ? NodeReader.of(displayNode, report) : null;

		Map<Integer, Kit> tierKits  = new HashMap<>(base.tierKits());
		MappingNode       tiersNode = role.get("Tiers").asMapping().orNull();
		if (tiersNode != null) {
			NodeReader tiersReader = NodeReader.of(tiersNode, report);
			for (String key : tiersReader.keys()) {
				Integer     number  = tierNumber(key);
				MappingNode kitNode = tiersReader.get(key).asMapping().required().orNull();
				if (number == null) {
					report.add(Severity.WARNING, locationOf(tiersReader.get(key), tiersReader), "Tiers." + key,
					           "unknown tier '" + key + "' (a level number or a cops.yml tier Display_Name), skipped",
					           "config.unknown_tier");
				} else if (!tiers.isEmpty() && !tiers.containsKey(number)) {
					report.add(Severity.WARNING, locationOf(tiersReader.get(key), tiersReader), "Tiers." + key,
					           "tier " + number + " is not declared in cops.yml, skipped", "config.unknown_tier");
				} else if (kitNode != null) {
					tierKits.merge(number, readKit(NodeReader.of(kitNode, report), report, itemParser),
					               (old, read) -> read.over(old));
				}
			}
		}

		Double rangedMin = optionalDouble(role, "Ranged_Min_Distance", 0, 64, base.rangedMin());
		Double rangedMax = optionalDouble(role, "Ranged_Max_Distance", 0, 64, base.rangedMax());
		if ((rangedMin == null) != (rangedMax == null)) {
			report.add(Severity.WARNING, locationOf(role.get(rangedMin != null ? "Ranged_Min_Distance"
			                                                                   : "Ranged_Max_Distance"), role),
			           rangedMin != null ? "Ranged_Min_Distance" : "Ranged_Max_Distance",
			           "a firing band needs both Ranged_Min_Distance and Ranged_Max_Distance and this role has no " +
			           "base band: the settings.yml band is used", "config.incomplete_band");
			// store neither, so the role is classified (CopRadio#kindOf) by the band that is actually applied
			rangedMin = null;
			rangedMax = null;
		}

		return new CopRole(base.name(),
		                   display == null ? base.displayName()
		                                   : display.get("Name").asString().orDefault(base.displayName()),
		                   placement,
		                   rangedMin, rangedMax,
		                   role.get("Health_Multiplier").asDouble().min(0.1).max(10).orDefault(base.healthMultiplier()),
		                   role.get("Leader_Priority").asInt().orDefault(base.leaderPriority()),
		                   optionalDouble(role, "Strafe_Degrees", 0, 180, base.strafeDegrees()),
		                   role.get("Fire_Rate_Scale").asDouble().min(0.05).max(10).orDefault(base.fireRateScale()),
		                   role.get("Difficulty_Bonus").asInt().min(0).max(3).orDefault(base.difficultyBonus()),
		                   retreat,
		                   role.get("Block_Fraction").asDouble().min(0).max(1).orDefault(base.blockFraction()),
		                   role.get("Block_Cone_Degrees").asDouble().min(0).max(360).orDefault(base.blockConeDegrees()),
		                   role.get("Medic").asBool().orDefault(base.medic()),
		                   role.get("Commander").asBool().orDefault(base.commander()),
		                   display == null ? base.color() : display.get("Color").asString().orDefault(base.color()),
		                   display == null ? base.symbol() : display.get("Symbol").asString().orDefault(base.symbol()),
		                   readKit(role, report, itemParser).over(base.kit()),
		                   tierKits);
	}

	/**
	 * A {@code Weapon_Pool} and a {@code Gear} block. {@code weapon:<name>} entries are the Bartizan guns, any other
	 * entry a vanilla item held when no gun resolves. An absent pool or slot stays unset ({@code null}).
	 */
	private Kit readKit(NodeReader node, ConfigReport report, @Nullable ItemParser itemParser) {
		List<String>    guns  = null;
		List<ItemStack> items = new ArrayList<>();
		if (node.has("Weapon_Pool")) {
			guns = new ArrayList<>();
			for (String entry : node.get("Weapon_Pool").asList().ofStrings().orEmpty()) {
				if (entry == null || entry.isBlank()) continue;
				if (entry.toLowerCase(Locale.ROOT).startsWith("weapon:")) {
					guns.add(entry.substring("weapon:".length()).trim());
				} else {
					ItemStack item = parseItem(entry, itemParser);
					if (item != null) items.add(item);
				}
			}
		}

		MappingNode gearNode = node.get("Gear").asMapping().orNull();
		NodeReader  gear     = gearNode != null ? NodeReader.of(gearNode, report) : null;
		return new Kit(guns, items, readGear(gear, "Helmet", report), readGear(gear, "Chestplate", report),
		               readGear(gear, "Leggings", report), readGear(gear, "Boots", report),
		               readGear(gear, "Off_Hand", report));
	}

	/**
	 * One {@code Gear} slot: a material name, or a block with {@code Material}, {@code Leather_Color} ({@code #RRGGBB}
	 * or {@code R, G, B}) and {@code Glow}. {@code ""} empties the slot; an unknown material is reported and skipped,
	 * and so is a colour on a piece that is not leather.
	 */
	private static @Nullable Gear readGear(@Nullable NodeReader gear, String slot, ConfigReport report) {
		if (gear == null || !gear.has(slot)) return null;

		NodeReader.NodeAccess access = gear.get(slot);
		String                material;
		Color                 color   = null;
		SourceLocation        colorAt = null;
		boolean               glow    = false;
		if (access.node() instanceof MappingNode mapping) {
			NodeReader piece = NodeReader.of(mapping, report);
			material = piece.get("Material").asString().required().orNull();
			glow     = piece.get("Glow").asBool().orDefault(false);
			String rawColor = piece.get("Leather_Color").asString().orNull();
			colorAt = locationOf(piece.get("Leather_Color"), piece);
			if (rawColor != null && (color = parseColor(rawColor)) == null)
				report.add(Severity.WARNING, colorAt, slot + ".Leather_Color",
				           "Leather_Color '" + rawColor + "' is not #RRGGBB or R, G, B, ignored", "config.type");
		} else {
			material = access.asString().orNull();
		}

		if (material == null) return null;
		if (material.isBlank()) return Gear.NONE;
		Material type = Material.matchMaterial(material.trim());
		if (type == null) {
			report.add(Severity.WARNING, locationOf(access, gear), slot,
			           "unknown material '" + material + "', slot left to the tier", "config.enum");
			return null;
		}
		if (color != null && !type.name().startsWith("LEATHER_")) {
			report.add(Severity.WARNING, colorAt, slot + ".Leather_Color",
			           "Leather_Color only applies to LEATHER_* gear, not " + type.name() + ", ignored", "config.type");
			color = null;
		}
		return new Gear(type, color, glow);
	}

	/** {@code #RRGGBB}, {@code RRGGBB} or {@code R, G, B}; {@code null} when it is none of those. */
	static @Nullable Color parseColor(String raw) {
		String text = raw.trim();
		try {
			if (text.contains(",")) {
				String[] parts = text.split(",");
				if (parts.length != 3) return null;
				return Color.fromRGB(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
				                     Integer.parseInt(parts[2].trim()));
			}
			return Color.fromRGB(Integer.parseInt(text.startsWith("#") ? text.substring(1) : text, 16));
		} catch (IllegalArgumentException e) { // NumberFormatException, or a channel out of range
			return null;
		}
	}

	/** A {@code Tiers} key: a number, or a cops.yml tier's {@code Display_Name} without colours ({@code Military}). */
	private @Nullable Integer tierNumber(String key) {
		try {
			return Integer.parseInt(key.trim());
		} catch (NumberFormatException ignored) {
			String wanted = key.replace('_', ' ').trim();
			for (CopTierConfig tier : tiers.values()) {
				String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', tier.displayName()));
				if (plain != null && plain.trim().equalsIgnoreCase(wanted)) return tier.tier();
			}
			return null;
		}
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

	/** {@code Cops.Names}: an absent {@code First_Names} keeps the built-in pool, {@code []} means no first name. */
	private CopNames parseNames(@Nullable NodeReader cops, ConfigReport report) {
		MappingNode namesSection = cops == null ? null : cops.get("Names").asMapping().orNull();
		if (namesSection == null) return CopNames.DEFAULT;

		NodeReader   names  = NodeReader.of(namesSection, report);
		String       format = names.get("Format").asString().orDefault(CopNames.DEFAULT.format());
		List<String> pool   = names.get("First_Names").asList().ofStrings().orNull();
		if (pool == null) return new CopNames(format, CopNames.DEFAULT.firstNames());

		return new CopNames(format, pool.stream().map(String::trim).filter(name -> !name.isEmpty()).toList());
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

	private RegroupSettings parseRegroupSettings(@Nullable NodeReader cops, ConfigReport report) {
		RegroupSettings defaults = RegroupSettings.DEFAULT;
		MappingNode     section  = cops == null ? null : cops.get("Regroup").asMapping().orNull();
		if (section == null) return defaults;

		NodeReader regroup = NodeReader.of(section, report);
		boolean    enabled = regroup.get("Enabled").asBool().orDefault(defaults.enabled());
		int        casualties = regroup.get("Casualties").asInt().min(1).orDefault(defaults.casualties());
		int windowSeconds = regroup.get("Window_Seconds").asInt().min(0).orDefault((int) (defaults.windowMs() / 1000L));
		int fallBackSeconds = regroup.get("Fall_Back_Seconds").asInt().min(0)
				.orDefault((int) (defaults.fallBackMs() / 1000L));
		int cooldownSeconds = regroup.get("Cooldown_Seconds").asInt().min(0)
				.orDefault((int) (defaults.cooldownMs() / 1000L));
		double arrival = regroup.get("Arrival_Radius").asDouble().min(0.0).orDefault(defaults.arrivalRadius());

		return new RegroupSettings(enabled, casualties, windowSeconds * 1000L, fallBackSeconds * 1000L,
		                           cooldownSeconds * 1000L, arrival);
	}

	private ShotNoiseSettings parseShotNoiseSettings(@Nullable NodeReader cops, ConfigReport report) {
		ShotNoiseSettings defaults = ShotNoiseSettings.DEFAULT;
		MappingNode       section  = cops == null ? null : cops.get("Shot_Noise").asMapping().orNull();
		if (section == null) return defaults;

		NodeReader noise   = NodeReader.of(section, report);
		boolean    enabled = noise.get("Enabled").asBool().orDefault(defaults.enabled());
		MappingNode radiusSection = noise.get("Radius").asMapping().orNull();
		if (radiusSection == null) return new ShotNoiseSettings(enabled, defaults.radius());

		NodeReader          radiusReader = NodeReader.of(radiusSection, report);
		Map<String, Double> radius       = new HashMap<>();
		for (String type : radiusReader.keys()) {
			radius.put(type.toUpperCase(Locale.ROOT), radiusReader.get(type).asDouble().min(0.0).orDefault(0.0));
		}
		return new ShotNoiseSettings(enabled, Map.copyOf(radius));
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
