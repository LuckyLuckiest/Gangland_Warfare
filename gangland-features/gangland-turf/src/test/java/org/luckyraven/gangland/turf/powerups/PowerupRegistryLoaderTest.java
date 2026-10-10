package org.luckyraven.gangland.turf.powerups;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
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
	private Level              savedLevel;
	private org.apache.logging.log4j.core.Logger logger;

	@BeforeEach
	void attach() {
		capture = new WarningCapture(warnings);
		capture.start();
		logger = (org.apache.logging.log4j.core.Logger) Logger.getLogger(PowerupRegistryLoader.class);
		savedLevel = logger.getLevel();
		logger.setLevel(Level.WARN);
		logger.addAppender(capture);
	}

	@AfterEach
	void detach() {
		logger.removeAppender(capture);
		logger.setLevel(savedLevel);
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
		load("Powerups:\n"
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
