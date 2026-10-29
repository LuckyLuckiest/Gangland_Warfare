package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Lane f2-config: the 0.20.0 {@code Wanted.Witnesses} and {@code Wanted.Trespass} blocks are parsed beside
 * {@link ChaseConfig} (which gains no component, so 0.21.0 merges cleanly), and {@code Evading_Police} weighs 100.
 */
@DisplayName("ChaseConfig - 0.20.0 witnesses, trespass and Evading_Police")
class ChaseConfig020Test {

	private static NodeReader wanted(String yaml, ConfigReport report) {
		NodeReader  file    = NodeReader.of(new ConfigParser().parse(Path.of("wanted.yml"), new StringReader(yaml), report)
		                                                      .root(), report);
		MappingNode section = file.get("Wanted").asMapping().orNull();
		return section == null ? null : NodeReader.of(section, report);
	}

	@Test
	@DisplayName("no Witnesses block, or no Wanted section: enabled, crimes [Car_Theft, Assault_Civilian, Kill_Civilian] "
	             + "(0.21.1 Q1), 0.21.2 S4: and Assault_Cop, Kill_Cop; Defer_Minutes 10, the shipped Cops block")
	void witnesses_default() {
		WitnessSettings expected = new WitnessSettings(true, List.of("Car_Theft", "Assault_Civilian", "Kill_Civilian",
		                                                             "Assault_Cop", "Kill_Cop"), 10,
		                                               new WitnessSettings.Cops(3, 180.0, 6.0, List.of(), true));

		assertEquals(expected, WitnessSettings.parse(null));
		assertEquals(expected, WitnessSettings.parse(wanted("Wanted:\n   Heat:\n      Enabled: true\n",
		                                                    new ConfigReport())));
		assertEquals(expected, WitnessSettings.DEFAULT);
		// ChaseConfig keeps its five components: the new blocks are read beside it (merge safety with 0.21.0)
		assertEquals(5, ChaseConfig.class.getRecordComponents().length);
	}

	@Test
	@DisplayName("0.21.2 S4: Witnesses reads Defer_Minutes and the Cops block; a missing Cops key is its default")
	void witnesses_copsBlockAndDeferMinutes() {
		WitnessSettings parsed = WitnessSettings.parse(wanted(
				"Wanted:\n   Witnesses:\n      Defer_Minutes: 4\n      Crimes:\n         - Kill_Cop\n"
				+ "      Cops:\n         Report_Seconds: 5\n         View_Angle: 360.0\n"
				+ "         Loud_Crimes:\n            - Kill_Cop\n         Reporter_Glow: false\n", new ConfigReport()));

		assertEquals(4, parsed.deferMinutes());
		assertEquals(List.of("Kill_Cop"), parsed.crimes());
		assertEquals(new WitnessSettings.Cops(5, 360.0, 6.0, List.of("Kill_Cop"), false), parsed.cops());
	}

	@Test
	@DisplayName("Witnesses reads Enable and Crimes; an empty Crimes list is honoured (nothing gated)")
	void witnesses_values() {
		ConfigReport report = new ConfigReport();

		assertEquals(new WitnessSettings(false, List.of("Store_Robbery")),
		             WitnessSettings.parse(wanted("Wanted:\n   Witnesses:\n      Enable: false\n      Crimes:\n"
		                                          + "         - Store_Robbery\n", report)));
		assertEquals(new WitnessSettings(true, List.of()),
		             WitnessSettings.parse(wanted("Wanted:\n   Witnesses:\n      Crimes: []\n", report)));
		assertTrue(report.issues().isEmpty(), () -> "unexpected issues: " + report.issues());
	}

	@Test
	@DisplayName("Witnesses: a bad Enable keeps the default and warns")
	void witnesses_badValueWarns() {
		ConfigReport report = new ConfigReport();

		WitnessSettings parsed = WitnessSettings.parse(wanted("Wanted:\n   Witnesses:\n      Enable: perhaps\n", report));

		assertTrue(parsed.enabled());
		assertTrue(report.issues().stream().anyMatch(i -> i.path() != null && i.path().contains("Enable")),
		           () -> "expected an issue at Enable, got " + report.issues());
	}

	@Test
	@DisplayName("Trespass: default enabled, 60 s; values read; a repeat below 0 is clamped to 0")
	void trespass_defaultAndValues() {
		ConfigReport report = new ConfigReport();

		assertEquals(new TrespassSettings(true, 60), TrespassSettings.parse(null));
		assertEquals(new TrespassSettings(true, 60), TrespassSettings.DEFAULT);
		assertEquals(new TrespassSettings(false, 30),
		             TrespassSettings.parse(wanted("Wanted:\n   Trespass:\n      Enable: false\n"
		                                           + "      Repeat_Seconds: 30\n", report)));
		assertEquals(0, TrespassSettings.parse(wanted("Wanted:\n   Trespass:\n      Repeat_Seconds: -5\n", report))
		                                .repeatSeconds());
	}

	@Test
	@DisplayName("Trespass: a negative Repeat_Seconds is reported as a range issue at the key")
	void trespass_negativeRepeatWarns() {
		ConfigReport report = new ConfigReport();

		TrespassSettings.parse(wanted("Wanted:\n   Trespass:\n      Repeat_Seconds: -5\n", report));

		assertTrue(report.issues().stream().anyMatch(i -> i.path() != null && i.path().contains("Repeat_Seconds")),
		           () -> "expected a range issue at Repeat_Seconds, got " + report.issues());
	}

	@Test
	@DisplayName("the loader exposes the blocks and they are the defaults before the first load")
	void loader_exposesDefaultsBeforeLoad() {
		ChaseConfigLoader loader = new ChaseConfigLoader(mock(JavaPlugin.class), mock(FileManager.class));

		assertEquals(WitnessSettings.parse(null), loader.getWitnesses());
		assertEquals(TrespassSettings.parse(null), loader.getTrespass());
	}

	@Test
	@DisplayName("C2b: loadData reads Witnesses and Trespass from wanted.yml; clear() restores the defaults")
	void loader_readsBlocksFromFile(@TempDir Path tempDir) throws IOException {
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		Files.writeString(tempDir.resolve("wanted.yml"), """
				Wanted:
				   Witnesses:
				      Enable: false
				      Crimes:
				         - Store_Robbery
				   Trespass:
				      Repeat_Seconds: 30
				""", StandardCharsets.UTF_8);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("wanted.yml").toFile()), false);
		ChaseConfigLoader loader = new ChaseConfigLoader(plugin, fileManager);

		loader.loadData(null, fileManager);

		assertEquals(new WitnessSettings(false, List.of("Store_Robbery")), loader.getWitnesses());
		assertEquals(new TrespassSettings(true, 30), loader.getTrespass());

		loader.clear();

		assertEquals(WitnessSettings.DEFAULT, loader.getWitnesses());
		assertEquals(TrespassSettings.DEFAULT, loader.getTrespass());
	}

	@Test
	@DisplayName("Evading_Police weighs 100 in the code default, so one star when seen (100 x 1.5 = 150 >= 100)")
	void evadingPolice_weight100() {
		assertEquals(100, HeatSettings.DEFAULT.weightOf("Evading_Police"));
		assertEquals(100, ChaseConfig.DEFAULT.heat().weightOf("Evading_Police"));
		assertNotNull(ChaseConfig.DEFAULT.heat().crimeWeights().get("Evading_Police"));
	}
}
