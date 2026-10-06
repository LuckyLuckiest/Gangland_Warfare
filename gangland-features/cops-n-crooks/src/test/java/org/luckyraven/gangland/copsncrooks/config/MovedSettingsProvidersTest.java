package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.integration.config.GanglandCopSettings;
import org.luckyraven.gangland.copsncrooks.integration.detainment.DetainmentSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.KillComboSettings;
import org.luckyraven.gangland.file.configuration.MovedSetting;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The 0.15.1 settings ownership move of cops-n-crooks: cops.yml ({@link GanglandCopSettings}), detainment.yml
 * ({@link DetainmentSettings}) and wanted.yml's Kill_Combo ({@link KillComboSettings}). Each one (1) loads the
 * shipped defaults from the module jar, (2) keeps an owner's settings.yml tuning while the module file still holds the
 * default, and (3) prefers a value tuned in the module file.
 */
@DisplayName("cops-n-crooks settings.yml keys moved to the module's own YAML")
class MovedSettingsProvidersTest {

	private YamlConfiguration legacy;
	private FileManager       fileManager;

	private static YamlConfiguration shipped(String name) {
		InputStream in = MovedSettingsProvidersTest.class.getClassLoader()
		                                                 .getResourceAsStream("copsncrooks/" + name + ".yml");
		Objects.requireNonNull(in, name);
		return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
	}

	private static FileHandler handler(YamlConfiguration config, String name) {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(config);
		when(handler.getName()).thenReturn(name);
		when(handler.getDirectory()).thenReturn("copsncrooks/" + name);
		when(handler.getFileType()).thenReturn(".yml");
		return handler;
	}

	@BeforeEach
	void setUp() {
		legacy      = new YamlConfiguration();
		fileManager = mock(FileManager.class);

		FileHandler settings = handler(legacy, "settings");
		when(fileManager.getFile("settings")).thenReturn(settings);
	}

	private YamlConfiguration module(String name) {
		YamlConfiguration config = shipped(name);
		FileHandler       file   = handler(config, name);
		when(fileManager.getFile(name)).thenReturn(file);
		return config;
	}

	private GanglandCopSettings copSettings() {
		GanglandCopSettings settings = new GanglandCopSettings(fileManager);
		settings.initialize();
		return settings;
	}

	private DetainmentSettings detainmentSettings() {
		DetainmentSettings settings = new DetainmentSettings(fileManager);
		settings.initialize();
		return settings;
	}

	private KillComboSettings killCombo() {
		return KillComboSettings.read(MovedSetting.of(fileManager.getFile("wanted"), fileManager, "cops-n-crooks"));
	}

	// ── cops.yml ────────────────────────────────────────────────────────────

	@Test
	@DisplayName("cops.yml: the shipped file gives the old settings.yml defaults")
	void cops_shippedDefaults() {
		module("cops");
		GanglandCopSettings settings = copSettings();

		assertEquals(8, settings.getMaxCopsPerPlayer());
		assertEquals(10, settings.getAiTickRate());
		assertEquals(40.0, settings.getAlertRange());
		assertEquals(20, settings.getAttackCooldownTicks());
		assertEquals(5.0, settings.getGuardRadius());
		assertEquals(10.0, settings.getMinSpawnDistance());
		assertEquals(2, settings.getMinOpenHorizontalSides());
		assertEquals(16.0, settings.getSpawnerMaxYDiff());
		assertEquals(80.0, settings.getPursuitMaxDistance());
		assertEquals(600, settings.getMaxReturnTicks());
		assertEquals(5, settings.getStuckCheckIntervalTicks());
		assertEquals(10, settings.getNavigationRecalculationTicks());
		assertEquals(2, settings.getMinRepathAfterLossTicks());
		assertEquals(0.75, settings.getMinProgressDistance());
		assertEquals(12.0, settings.getRangedMaxDistance());
		assertEquals(2, settings.getCountForLevel(1));
		assertEquals(4, settings.getCountForLevel(3));
		assertEquals(8, settings.getCountForLevel(20));
	}

	@Test
	@DisplayName("cops.yml: a value tuned only in settings.yml (old path) still wins while cops.yml holds the default")
	void cops_legacyTunedWins() {
		module("cops");
		legacy.set("Cops.Behaviour.Max_Per_Player", 12);
		legacy.set("Cops.Count.Base", 3);
		legacy.set("NPC_Navigation.Ranged_Max_Distance", 20.0);
		legacy.set("NPC_Navigation.Recalculation_Ticks", 25);
		legacy.set("NPC_Navigation.Min_Repath_After_Loss_Ticks", 6);
		legacy.set("Detainment.Transit.Guard_Radius", 7.5);

		GanglandCopSettings settings = copSettings();

		assertEquals(25, settings.getNavigationRecalculationTicks());
		assertEquals(6, settings.getMinRepathAfterLossTicks());
		assertEquals(12, settings.getMaxCopsPerPlayer());
		assertEquals(3, settings.getCountForLevel(1));
		assertEquals(20.0, settings.getRangedMaxDistance());
		assertEquals(7.5, settings.getGuardRadius());
	}

	@Test
	@DisplayName("cops.yml: a value tuned in cops.yml wins over settings.yml")
	void cops_moduleTunedWins() {
		YamlConfiguration cops = module("cops");
		cops.set("Cops.Behaviour.Max_Per_Player", 6);
		cops.set("Cops.Navigation.Ranged_Max_Distance", 15.0);
		cops.set("Cops.Behaviour.Guard_Radius", 4.0);
		legacy.set("Cops.Behaviour.Max_Per_Player", 12);
		legacy.set("NPC_Navigation.Ranged_Max_Distance", 20.0);
		legacy.set("Detainment.Transit.Guard_Radius", 7.5);

		GanglandCopSettings settings = copSettings();

		assertEquals(6, settings.getMaxCopsPerPlayer());
		assertEquals(15.0, settings.getRangedMaxDistance());
		assertEquals(4.0, settings.getGuardRadius());
	}

	// ── detainment.yml ──────────────────────────────────────────────────────

	@Test
	@DisplayName("detainment.yml: the shipped file gives the old settings.yml defaults")
	void detainment_shippedDefaults() {
		module("detainment");
		DetainmentSettings settings = detainmentSettings();

		assertEquals(10, settings.getJailMaxCapacity());
		assertEquals(400, settings.getTransitDelayTicks());
		assertEquals(25, settings.getBreakFreeTapsRequired());
		assertEquals(40, settings.getBreakFreeResetWindowTicks());
		assertEquals(500.0, settings.getHandcuffBribeBaseCost());
		assertEquals(250.0, settings.getHandcuffBribePerLevel());
		assertEquals(2_500.0, settings.getBailBaseCost());
		assertEquals(1_000.0, settings.getBailPerLevel());
		assertEquals(1_000.0, settings.getJailBribeBaseCost());
		assertEquals(500.0, settings.getJailBribePerLevel());
		assertEquals(0.35, settings.getJailBribeSuccessChance());
		assertEquals(60, settings.getJailBribeFailPenaltySeconds());
		assertEquals(180, settings.getSentenceBaseSeconds());
		assertEquals(60, settings.getSentencePerWantedLevelSeconds());
		assertEquals("spawn", settings.getFallbackExitWaypoint());
		assertEquals("BLOCK_NOTE_BLOCK_PLING", settings.getBailSuccessSound());
		assertEquals("ENTITY_VILLAGER_YES", settings.getBribeSuccessSound());
		assertEquals("ENTITY_VILLAGER_NO", settings.getBribeFailSound());
		assertEquals("BLOCK_IRON_DOOR_CLOSE", settings.getTransitCommitSound());
		assertEquals("BLOCK_BELL_USE", settings.getSentenceCompleteSound());
	}

	@Test
	@DisplayName("detainment.yml: a value tuned only in settings.yml still wins while detainment.yml holds the default")
	void detainment_legacyTunedWins() {
		module("detainment");
		legacy.set("Detainment.Bail.Base_Cost", 3_000.0);
		legacy.set("Detainment.Jail.Max_Capacity", 20);
		legacy.set("Detainment.Sounds.Bribe_Fail", "ENTITY_VILLAGER_HURT");

		DetainmentSettings settings = detainmentSettings();

		assertEquals(3_000.0, settings.getBailBaseCost());
		assertEquals(20, settings.getJailMaxCapacity());
		assertEquals("ENTITY_VILLAGER_HURT", settings.getBribeFailSound());
	}

	@Test
	@DisplayName("detainment.yml: a value tuned in detainment.yml wins over settings.yml")
	void detainment_moduleTunedWins() {
		YamlConfiguration detainment = module("detainment");
		detainment.set("Detainment.Bail.Base_Cost", 4_000.0);
		legacy.set("Detainment.Bail.Base_Cost", 3_000.0);

		assertEquals(4_000.0, detainmentSettings().getBailBaseCost());
	}

	// ── wanted.yml Kill_Combo ───────────────────────────────────────────────

	@Test
	@DisplayName("wanted.yml: the shipped Kill_Combo is the old settings.yml one")
	void killCombo_shippedDefaults() {
		module("wanted");

		assertEquals(KillComboSettings.DEFAULT, killCombo());
		assertEquals(10, killCombo().chainSeconds());
	}

	@Test
	@DisplayName("wanted.yml: Kill_Combo tuned only in settings.yml still wins while wanted.yml holds the default")
	void killCombo_legacyTunedWins() {
		module("wanted");
		legacy.set("Wanted.Kill_Combo.Enable", false);
		legacy.set("Wanted.Kill_Combo.Reset_After", 25);
		legacy.set("Wanted.Kill_Combo.Kill_Counter", List.of(1, 3));

		KillComboSettings combo = killCombo();

		assertEquals(new KillComboSettings(false, 25, List.of(1, 3)), combo);
		assertEquals(0, combo.chainSeconds());
	}

	@Test
	@DisplayName("wanted.yml: Kill_Combo tuned in wanted.yml wins over settings.yml")
	void killCombo_moduleTunedWins() {
		YamlConfiguration wanted = module("wanted");
		wanted.set("Wanted.Kill_Combo.Reset_After", 15);
		wanted.set("Wanted.Kill_Combo.Kill_Counter", List.of(4, 8));
		legacy.set("Wanted.Kill_Combo.Reset_After", 25);
		legacy.set("Wanted.Kill_Combo.Kill_Counter", List.of(1, 3));

		KillComboSettings combo = killCombo();

		assertEquals(15, combo.resetAfterSeconds());
		assertEquals(List.of(4, 8), combo.killCounter());
	}
}
