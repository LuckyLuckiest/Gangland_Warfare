package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lane bag: {@code Wanted.Bag} of {@code copsncrooks/wanted.yml} - {@link BagSettings#parse}, the bundled file and the
 * loader getter; plus the bundled {@code Bag.*} / {@code Fence.*} message texts.
 */
@DisplayName("Wanted.Bag - settings, bundled files, loader")
class BagSettingsTest {

	@TempDir
	Path tempDir;

	private static final Set<String> BAG_KEYS = Set.of("Enable", "Fence_Cut", "Max_Per_Chase", "Max_Banked_Per_Day",
	                                                   "Spill_Stacks", "Spill_Variation", "Hud_Enable",
	                                                   "Hud_Interval_Ticks");

	private static NodeReader wanted(String yaml, ConfigReport report) {
		NodeReader  file    = NodeReader.of(new ConfigParser().parse(Path.of("wanted.yml"), new StringReader(yaml), report)
		                                                      .root(), report);
		MappingNode section = file.get("Wanted").asMapping().orNull();
		return section == null ? null : NodeReader.of(section, report);
	}

	private static String resource(String name) throws IOException {
		try (InputStream in = Objects.requireNonNull(BagSettingsTest.class.getClassLoader().getResourceAsStream(name),
		                                              name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	@DisplayName("no Wanted.Bag block, no Wanted section: the shipped defaults")
	void missingBlockGivesDefaults() {
		BagSettings expected = new BagSettings(true, 0.35, 5000, 15000, 4, "medium", true, 40);

		assertEquals(expected, BagSettings.DEFAULT);
		assertEquals(expected, BagSettings.parse(null));
		assertEquals(expected, BagSettings.parse(wanted("Wanted:\n   Heat:\n      Enabled: true\n", new ConfigReport())));
	}

	@Test
	@DisplayName("every key is read")
	void readsEveryKey() {
		ConfigReport report = new ConfigReport();

		BagSettings parsed = BagSettings.parse(wanted("""
				Wanted:
				   Bag:
				      Enable: false
				      Fence_Cut: 0.5
				      Max_Per_Chase: 100
				      Max_Banked_Per_Day: 200
				      Spill_Stacks: 8
				      Spill_Variation: large
				      Hud_Enable: false
				      Hud_Interval_Ticks: 10
				""", report));

		assertEquals(new BagSettings(false, 0.5, 100, 200, 8, "large", false, 10), parsed);
		assertTrue(report.issues().isEmpty(), report.issues().toString());
	}

	@Test
	@DisplayName("D-W2-06: a Fence_Cut of 0, 1, -0.2 or 1.5 falls back to 0.35 with one config.range report naming it")
	void badFenceCutFallsBack() {
		for (String bad : new String[]{"0", "1", "-0.2", "1.5"}) {
			ConfigReport report = new ConfigReport();

			BagSettings parsed = BagSettings.parse(wanted("Wanted:\n   Bag:\n      Fence_Cut: " + bad + "\n", report));

			assertEquals(0.35, parsed.fenceCut(), bad);
			long hits = report.issues().stream()
			                  .filter(i -> "config.range".equals(i.code()) && i.path() != null &&
			                               i.path().contains("Fence_Cut")).count();
			assertEquals(1, hits, () -> bad + " -> " + report.issues());
		}
	}

	@Test
	@DisplayName("Spill_Stacks is held within 1 to 16")
	void spillStacksAreClamped() {
		assertEquals(16, new BagSettings(true, 0.35, 5000, 15000, 99, "medium", true, 40).spillStacks());
		assertEquals(1, new BagSettings(true, 0.35, 5000, 15000, 0, "medium", true, 40).spillStacks());
		assertEquals(16, BagSettings.parse(wanted("Wanted:\n   Bag:\n      Spill_Stacks: 40\n", new ConfigReport()))
		                            .spillStacks());
	}

	@Test
	@DisplayName("the bundled wanted.yml has the eight Wanted.Bag keys and parses equal to the defaults")
	void bundledFileMatchesDefaults() throws IOException {
		ConfigReport report = new ConfigReport();
		NodeReader   wanted = wanted(resource("copsncrooks/wanted.yml"), report);
		assertNotNull(wanted);

		NodeReader bag = wanted.get("Bag").asMapping().reader();
		assertNotNull(bag, "Wanted.Bag block present");
		Set<String> keys = new LinkedHashSet<>();
		bag.keys().forEach(keys::add);

		assertEquals(BAG_KEYS, keys);
		assertEquals(BagSettings.DEFAULT, BagSettings.parse(wanted));
	}

	@Test
	@DisplayName("the bundled Wanted.Bag raises no unknown-key issue once BagSettings has read it")
	void bundledBagRaisesNoUnknownKey() throws IOException {
		ConfigReport report = new ConfigReport();
		NodeReader   wanted = wanted(resource("copsncrooks/wanted.yml"), report);

		BagSettings.parse(wanted);

		assertTrue(report.issues().stream().noneMatch(i -> i.path() != null && i.path().contains("Bag")),
		           () -> "Bag issues: " + report.issues());
	}

	@Test
	@DisplayName("the bundled wanted.yml documents every Wanted.Bag key with a 0.20.0 comment and uses block style")
	void bundledBagIsCommentedBlockStyle() throws IOException {
		String yaml  = resource("copsncrooks/wanted.yml");
		int    start = yaml.indexOf("Bag:");
		assertTrue(start > 0, "Wanted.Bag block present");

		String block = yaml.substring(start);
		assertTrue(block.contains("0.20.0"), "block comment names the release");
		for (String key : BAG_KEYS) {
			int at = block.indexOf(key + ":");
			assertTrue(at > 0, key + " present");
			assertTrue(block.substring(0, at).stripTrailing().lines().reduce((a, b) -> b).orElse("").stripLeading()
			                .startsWith("#"), key + " has a comment line above it");
		}
		assertFalse(block.contains("{"), "no inline flow maps");
	}

	@Test
	@DisplayName("the loader exposes the defaults before the first load, and reads and clears Wanted.Bag")
	void loaderReadsBag() throws IOException {
		ChaseConfigLoader before = new ChaseConfigLoader(org.mockito.Mockito.mock(JavaPlugin.class),
		                                                 org.mockito.Mockito.mock(FileManager.class));
		assertEquals(BagSettings.DEFAULT, before.getBag());

		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		Files.writeString(tempDir.resolve("wanted.yml"), """
				Wanted:
				   Bag:
				      Fence_Cut: 0.5
				      Max_Per_Chase: 100
				""", StandardCharsets.UTF_8);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve("wanted.yml").toFile()), false);
		ChaseConfigLoader loader = new ChaseConfigLoader(plugin, fileManager) {
			{
				loadData(null, fileManager);
			}
		};

		assertEquals(new BagSettings(true, 0.5, 100, 15000, 4, "medium", true, 40), loader.getBag());

		loader.clear();

		assertEquals(BagSettings.DEFAULT, loader.getBag());
		assertEquals(ChaseConfig.DEFAULT, loader.get());
	}

	@Test
	@DisplayName("the bundled wanted_messages.yml carries every Bag.* and Fence.* text, equal to the in-code fallback")
	void bundledMessagesMatchFallbacks() throws IOException {
		ConfigReport report = new ConfigReport();
		NodeReader root = NodeReader.of(new ConfigParser().parse(Path.of("wanted_messages.yml"), new StringReader(
				resource("copsncrooks/wanted_messages.yml")), report).root(), report);

		for (WantedMessages.Key key : WantedMessages.Key.values()) {
			if (!key.path.startsWith("Bag.") && !key.path.startsWith("Fence.")) continue;

			String[]   parts = key.path.split("\\.");
			NodeReader group = root.get(parts[0]).asMapping().reader();
			String     text  = group == null ? "<missing>" : group.get(parts[1]).asString().orDefault("<missing>");

			assertEquals(key.fallback, text, key.path);
		}
	}
}
