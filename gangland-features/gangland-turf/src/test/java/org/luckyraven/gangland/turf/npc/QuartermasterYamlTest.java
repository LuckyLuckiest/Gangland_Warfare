package org.luckyraven.gangland.turf.npc;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.support.YamlFixture;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code Quartermaster.*} line the panels read, and every price, name and cap the Quartermaster takes from YAML,
 * exists in the shipped turf files, so a server that copies the defaults never falls back silently (W2-Q).
 */
@DisplayName("Quartermaster YAML - every key read is in the shipped turf files")
class QuartermasterYamlTest {

	private static YamlConfiguration load(String resource) throws IOException {
		return YamlConfiguration.loadConfiguration(new StringReader(YamlFixture.shipped(resource)));
	}

	@Test
	@DisplayName("turf_messages.yml has every Quartermaster line the sources read (scanned, T-276)")
	void messagesPresent() throws IOException {
		YamlConfiguration yaml = load("/turf/turf_messages.yml");
		Pattern           key  = Pattern.compile("\"(Quartermaster\\.[A-Za-z_]+)\"");
		Set<String>       keys = new TreeSet<>();

		try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				Matcher m = key.matcher(Files.readString(file));
				while (m.find()) {
					keys.add(m.group(1));
				}
			}
		}

		assertTrue(keys.size() > 40, "the scan must find the Quartermaster keys, found " + keys.size());
		for (String name : keys) {
			assertTrue(yaml.isSet(name), "missing " + name);
		}
	}

	@Test
	@DisplayName("garrisonKeys_ownedNotStockOrReserve: the Owned keys ship, the stock and reserve keys are gone")
	void garrisonKeys_ownedNotStockOrReserve() throws IOException {
		YamlConfiguration yaml = load("/turf/turf_messages.yml");

		for (String key : new String[]{"Garrison_Owned", "Garrison_In_Reserve", "Garrison_Where_One", "Garrison_Where_Two",
		                               "Bought_Guard_Post", "Bought_Guard_Reserve", "Owned_Full"}) {
			assertTrue(yaml.isSet("Quartermaster." + key), key);
		}
		for (String key : new String[]{"Garrison_Stock", "Garrison_Reserve", "Reserve_Full"}) {
			assertTrue(!yaml.isSet("Quartermaster." + key), key);
		}
	}

	@Test
	@DisplayName("every in-code Quartermaster fallback in the turf module equals the shipped turf_messages.yml line (T-801)")
	void fallbacksMatchShippedYaml() throws IOException {
		YamlConfiguration   yaml      = load("/turf/turf_messages.yml");
		Path                root      = Path.of("src/main/java/org/luckyraven/gangland/turf");
		Map<String, Set<String>> fallbacks = new HashMap<>();
		Map<String, String> constants = new HashMap<>();
		Pattern text     = Pattern.compile("\"(Quartermaster\\.[A-Za-z0-9_]+)\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
		Pattern refusal  = Pattern.compile("case (\\w+) -> \"((?:[^\"\\\\]|\\\\.)*)\"");
		Pattern constant = Pattern.compile("String (\\w+)\\s*= \"(Quartermaster\\.[A-Za-z0-9_]+)\"");

		List<String> sources = new ArrayList<>();
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				sources.add(Files.readString(file));
			}
		}
		for (String source : sources) {
			Matcher c = constant.matcher(source);
			while (c.find()) {
				constants.put(c.group(1), c.group(2));
			}
		}
		for (String source : sources) {
			Matcher m = text.matcher(source);
			while (m.find()) {
				fallbacks.computeIfAbsent(m.group(1), k -> new TreeSet<>()).add(m.group(2));
			}
			Matcher r = refusal.matcher(source);
			while (r.find()) {
				String key = constants.get(r.group(1));
				if (key != null) {
					fallbacks.computeIfAbsent(key, k -> new TreeSet<>()).add(r.group(2));
				}
			}
		}

		assertTrue(fallbacks.size() > 40, "the scan must find the Quartermaster fallbacks, found " + fallbacks.size());
		for (Map.Entry<String, Set<String>> entry : fallbacks.entrySet()) {
			assertTrue(yaml.isSet(entry.getKey()), "missing " + entry.getKey());
			for (String line : entry.getValue()) {
				assertEquals(yaml.getString(entry.getKey()), line, entry.getKey());
			}
		}
	}

	@Test
	@DisplayName("the menu pass keys and changed defaults of DESIGN 7 are shipped; Buffs_Effect is gone")
	void menuPassKeys() throws IOException {
		YamlConfiguration yaml = load("/turf/turf_messages.yml");
		Map<String, String> expected = new java.util.LinkedHashMap<>();
		expected.put("Buffs_Does", "&7Effect: %effect%");
		expected.put("Effect_Income", "&a+%percent%% &fturf income");
		expected.put("Effect_Discount", "&a%percent%% off &fnew guards");
		expected.put("Effect_Toughness", "&a+%percent%% &fhealth on your guards while it runs");
		expected.put("Effect_Waves", "&a+%percent%% &fguards fighting at once in a contest");
		expected.put("Effect_Reinforcements", "&fReplacements arrive &a%percent%% &fsooner");
		expected.put("Buffs_Active", "&aActive: &f%time% &aleft");
		expected.put("Buffs_Stack", "&7Buying again stacks, up to a limit.");
		expected.put("Title_Menu", "&5%npc%");
		expected.put("Title_Buffs", "&5Timed Buffs");
		expected.put("Title_Garrison", "&5Garrison");
		expected.put("Place_Title", "&5Place the %npc%");
		expected.put("Buffs_Duration", "&7Duration: &b%time%");

		for (Map.Entry<String, String> entry : expected.entrySet()) {
			assertEquals(entry.getValue(), yaml.getString("Quartermaster." + entry.getKey()), entry.getKey());
		}
		assertTrue(!yaml.isSet("Quartermaster.Buffs_Effect"), "Buffs_Effect is gone");
		assertTrue(!yaml.isSet("Garrison.Returned"), "Garrison.Returned is gone");
	}

	@Test
	@DisplayName("turf_npcs.yml names the Quartermaster (TF-38)")
	void powerupNameKeyPresent() throws IOException {
		assertTrue(load("/turf/turf_npcs.yml").isSet("Powerup_Npc.Display_Name"));
	}

	@Test
	@DisplayName("turf_powerups.yml has the guard price and no reinforced_defense (TF-17, D4)")
	void powerupsPricesAndCut() throws IOException {
		YamlConfiguration yaml = load("/turf/turf_powerups.yml");

		assertTrue(yaml.isSet("Garrison.Cost_Per_Guard"));
		assertTrue(!yaml.isSet("Powerups.reinforced_defense"));
	}

	@Test
	@DisplayName("turf_rules.yml has the reserve cap the Quartermaster enforces (TF-19)")
	void reserveCapPresent() throws IOException {
		assertTrue(load("/turf/turf_rules.yml").isSet("Garrison.Max_Reserve"));
	}

	@Test
	@DisplayName("the quartermaster civilian type ships Wander on with a small range (perimeter movement)")
	void quartermasterWanders() throws IOException {
		YamlConfiguration yaml = load("/npc/civilians.yml");

		assertTrue(yaml.getBoolean("Types.quartermaster.AI.Wander.Enabled"), "Wander on");
		int range = yaml.getInt("Types.quartermaster.AI.Wander.Range");
		assertTrue(range >= 2 && range <= 6, "small range, was " + range);
	}

	@Test
	@DisplayName("turf_npcs.yml ships Powerup_Npc.Respawn_Seconds equal to the in-code default (T-806)")
	void respawnSecondsShipped() throws IOException {
		assertEquals(org.luckyraven.gangland.turf.npc.config.TurfPowerupSettings.DEFAULT_RESPAWN_SECONDS,
		             load("/turf/turf_npcs.yml").getInt("Powerup_Npc.Respawn_Seconds", -1));
	}
}
