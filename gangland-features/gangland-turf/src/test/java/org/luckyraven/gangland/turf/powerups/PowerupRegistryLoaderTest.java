package org.luckyraven.gangland.turf.powerups;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.logging.Logger;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the guard price read from {@code Garrison.Cost_Per_Guard} and the one-line load warnings (T-276). The loader is
 * built over a mocked {@link FileManager} with the YAML parsed in memory, as {@code PowerupRegistryTest} does. Warnings
 * are captured from the loader's own log4j logger.
 */
@DisplayName("PowerupRegistryLoader - guard cost and discount warnings (T-276)")
class PowerupRegistryLoaderTest {

	private static final String BOOST = "Powerups:\n"
	                                    + "   good_boost:\n      Cost: 300\n      Effect_Type: INCOME_MULTIPLIER\n"
	                                    + "      Magnitude: 1.1\n      Duration_Seconds: 60\n";

	private final List<String> warnings = new ArrayList<>();
	private WarningCapture     capture;
	private String             loggerName;
	private Level              savedLevel;
	private org.apache.logging.log4j.core.Logger logger;

	@BeforeEach
	void attach() {
		capture = new WarningCapture(warnings);
		capture.start();
		loggerName = Logger.getLogger(PowerupRegistryLoader.class).getName();
		logger = (org.apache.logging.log4j.core.Logger) Logger.getLogger(PowerupRegistryLoader.class);
		savedLevel = logger.getLevel();
		Configurator.setLevel(loggerName, Level.WARN);
		logger.addAppender(capture);
	}

	@AfterEach
	void detach() {
		logger.removeAppender(capture);
		Configurator.setLevel(loggerName, savedLevel);
		capture.stop();
	}

	private static PowerupRegistry load(String yaml) throws Exception {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(
				new ConfigParser().parse(Path.of("turf_powerups.yml"), new StringReader(yaml), new ConfigReport()));
		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("turf_powerups")).thenReturn(handler);

		PowerupRegistry registry = new PowerupRegistry();
		new PowerupRegistryLoader(registry, fileManager).onInitialize(true);
		return registry;
	}

	private static String garrison(String cost) {
		return "Garrison:\n   Cost_Per_Guard: " + cost + "\n" + BOOST;
	}

	@Test
	@DisplayName("Cost_Per_Guard 0 falls back to 1000 with one warning")
	void zeroCost_fallsBackTo1000_oneWarning() throws Exception {
		PowerupRegistry registry = load(garrison("0"));

		assertEquals(0, new BigDecimal("1000").compareTo(registry.garrisonCost()));
		assertEquals(1, warnings.size(), warnings.toString());
	}

	@Test
	@DisplayName("a negative Cost_Per_Guard falls back to 1000 with one warning")
	void negativeCost_fallsBackTo1000_oneWarning() throws Exception {
		PowerupRegistry registry = load(garrison("-5"));

		assertEquals(0, new BigDecimal("1000").compareTo(registry.garrisonCost()));
		assertEquals(1, warnings.size(), warnings.toString());
	}

	@Test
	@DisplayName("a positive Cost_Per_Guard is read as written and does not warn")
	void positiveCost_readAsWritten_noWarning() throws Exception {
		PowerupRegistry registry = load(garrison("250"));

		assertEquals(0, new BigDecimal("250").compareTo(registry.garrisonCost()));
		assertEquals(0, warnings.size(), warnings.toString());
	}

	@Test
	@DisplayName("a GARRISON_DISCOUNT Magnitude above 1 loads and logs one warning (T-275)")
	void discountAboveOne_oneWarning() throws Exception {
		PowerupRegistry registry = load("Powerups:\n"
		     + "   guard_surcharge:\n      Cost: 100\n      Effect_Type: GARRISON_DISCOUNT\n"
		     + "      Magnitude: 1.5\n      Duration_Seconds: 60\n");

		assertEquals(1, warnings.size(), warnings.toString());
		assertTrue(warnings.get(0).contains("guard_surcharge"), warnings.get(0));
	}

	@Test
	@DisplayName("a GARRISON_DISCOUNT Magnitude inside (0, 1] does not warn (T-275)")
	void discountInRange_noWarning() throws Exception {
		load("Powerups:\n"
		     + "   guard_discount:\n      Cost: 100\n      Effect_Type: GARRISON_DISCOUNT\n"
		     + "      Magnitude: 0.8\n      Duration_Seconds: 60\n");

		assertEquals(0, warnings.size(), warnings.toString());
	}

	private static String entry(String id, String type, String magnitude, String extra) {
		return "   " + id + ":\n      Cost: 100\n      Effect_Type: " + type + "\n      Magnitude: " + magnitude
		       + "\n      Duration_Seconds: 60\n" + extra;
	}

	private PowerupRegistry loadWith(String entry) throws Exception {
		return load(BOOST + entry);
	}

	@Test
	void toughnessAndWaves_belowOneNanOrInfinite_skippedWithWarning() throws Exception {
		for (String type : new String[] {"GUARD_TOUGHNESS", "BIGGER_GUARD_WAVES"}) {
			for (String bad : new String[] {"0.9", "-3"}) {
				warnings.clear();
				PowerupRegistry registry = loadWith(entry("x", type, bad, ""));
				assertTrue(!registry.exists("x"), type + " " + bad);
				assertEquals(1, warnings.size(), type + " " + bad + " " + warnings);
			}
		}
	}

	@Test
	void reinforcements_outsideZeroOneNanOrInfinite_skippedWithWarning() throws Exception {
		for (String bad : new String[] {"0", "-1", "1.5"}) {
			warnings.clear();
			PowerupRegistry registry = loadWith(entry("x", "FASTER_REINFORCEMENTS", bad, ""));
			assertTrue(!registry.exists("x"), bad);
			assertEquals(1, warnings.size(), bad + " " + warnings);
		}
	}

	@Test
	void inRangeAboveLimit_keptWithWarning() throws Exception {
		String[][] cases = {{"GUARD_TOUGHNESS", "4.0"}, {"BIGGER_GUARD_WAVES", "2.5"}, {"FASTER_REINFORCEMENTS", "0.1"}};
		for (String[] c : cases) {
			warnings.clear();
			PowerupRegistry registry = loadWith(entry("x", c[0], c[1], ""));
			assertTrue(registry.exists("x"), c[0]);
			assertEquals(1, warnings.size(), c[0] + " " + warnings);
		}
	}

	@Test
	void icon_missing_usesPerEffectDefault() throws Exception {
		PowerupRegistry registry = loadWith(entry("t", "GUARD_TOUGHNESS", "1.5", "")
		                                    + entry("w", "BIGGER_GUARD_WAVES", "1.5", "")
		                                    + entry("f", "FASTER_REINFORCEMENTS", "0.5", "")
		                                    + entry("i", "INCOME_MULTIPLIER", "1.5", "")
		                                    + entry("d", "GARRISON_DISCOUNT", "0.8", ""));
		assertEquals(org.bukkit.Material.IRON_BLOCK, registry.get("t").iconMaterial());
		assertEquals(org.bukkit.Material.BELL, registry.get("w").iconMaterial());
		assertEquals(org.bukkit.Material.CLOCK, registry.get("f").iconMaterial());
		assertEquals(org.bukkit.Material.GOLD_INGOT, registry.get("i").iconMaterial());
		assertEquals(org.bukkit.Material.EMERALD, registry.get("d").iconMaterial());
		assertEquals(0, warnings.size(), warnings.toString());
	}

	@Test
	void icon_valid_isUsed() throws Exception {
		PowerupRegistry registry = loadWith(entry("t", "GUARD_TOUGHNESS", "1.5", "      Icon: DIAMOND\n"));
		assertEquals(org.bukkit.Material.DIAMOND, registry.get("t").iconMaterial());
		assertEquals(0, warnings.size(), warnings.toString());
	}

	@Test
	void icon_unknownOrNotItem_warnsAndDefaults() throws Exception {
		for (String bad : new String[] {"NOT_A_THING", "WATER"}) {
			warnings.clear();
			PowerupRegistry registry = loadWith(entry("t", "GUARD_TOUGHNESS", "1.5", "      Icon: " + bad + "\n"));
			assertEquals(org.bukkit.Material.IRON_BLOCK, registry.get("t").iconMaterial(), bad);
			assertEquals(1, warnings.size(), bad + " " + warnings);
		}
	}

	@Test
	void description_missingIsEmpty_presentIsRead() throws Exception {
		PowerupRegistry registry = loadWith(entry("a", "GUARD_TOUGHNESS", "1.5", "")
		                                    + entry("b", "GUARD_TOUGHNESS", "1.5",
		                                            "      Description:\n         - \"&7one\"\n         - \"&7two\"\n"));
		assertTrue(registry.get("a").description().isEmpty());
		assertEquals(List.of("&7one", "&7two"), registry.get("b").description());
	}

	@Test
	void limits_badKey_warnsAndDefaults() throws Exception {
		PowerupRegistry registry = load(BOOST + "Limits:\n   Max_Guard_Toughness: 0.5\n   Max_Guard_Waves: 4\n"
		                                + "   Min_Reinforcement_Wait: 2\n");
		assertEquals(3.0, registry.guardLimits().maxToughness());
		assertEquals(4.0, registry.guardLimits().maxWaves());
		assertEquals(0.25, registry.guardLimits().minWait());
		assertEquals(2, warnings.size(), warnings.toString());
	}

	@Test
	void limits_defaultWhenMissing() throws Exception {
		PowerupRegistry registry = load(BOOST);
		assertEquals(PowerupRegistry.GuardLimits.DEFAULT, registry.guardLimits());
		assertEquals(new PowerupRegistry.GuardLimits(3.0, 2.0, 0.25), PowerupRegistry.GuardLimits.DEFAULT);
	}

	@Test
	void shippedYaml_hasAllSixWithIcons() throws Exception {
		String yaml;
		try (java.io.InputStream in = PowerupRegistryLoaderTest.class.getResourceAsStream("/turf/turf_powerups.yml")) {
			yaml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
		PowerupRegistry registry = load(yaml);
		assertEquals(6, registry.all().size());
		for (PowerupDefinition def : registry.all()) {
			assertTrue(def.icon() != null, def.id());
			assertTrue(!def.description().isEmpty(), def.id());
		}
		assertEquals(0, warnings.size(), warnings.toString());
		assertTrue(!yaml.contains("{"), "block style only");
	}

	/** Collects WARN lines from the loader's logger. */
	private static final class WarningCapture extends AbstractAppender {

		private final List<String> sink;

		WarningCapture(List<String> sink) {
			super("warning-capture", null, null, true, Property.EMPTY_ARRAY);
			this.sink = sink;
		}

		@Override
		public void append(LogEvent event) {
			if (event.getLevel() == Level.WARN) {
				sink.add(event.getMessage().getFormattedMessage());
			}
		}
	}
}
