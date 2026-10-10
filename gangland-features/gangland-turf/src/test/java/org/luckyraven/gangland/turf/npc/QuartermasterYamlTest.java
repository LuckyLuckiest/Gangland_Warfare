package org.luckyraven.gangland.turf.npc;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.support.YamlFixture;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code Quartermaster.*} line the panels read, and every price, name and cap the Quartermaster takes from YAML,
 * exists in the shipped turf files, so a server that copies the defaults never falls back silently (W2-Q).
 */
@DisplayName("Quartermaster YAML - every key read is in the shipped turf files")
class QuartermasterYamlTest {

	private static final List<String> MESSAGE_KEYS = List.of(
			"Quartermaster.Title_Menu", "Quartermaster.Title_Buffs", "Quartermaster.Title_Garrison",
			"Quartermaster.Back", "Quartermaster.Close", "Quartermaster.Page_Next", "Quartermaster.Page_Previous",
			"Quartermaster.Page_Info",
			"Quartermaster.Menu_Turf", "Quartermaster.Menu_Owner", "Quartermaster.Menu_Allied",
			"Quartermaster.Menu_Stock", "Quartermaster.Menu_Active", "Quartermaster.Menu_Bank",
			"Quartermaster.Menu_Buffs_Name", "Quartermaster.Menu_Buffs_Lore_1", "Quartermaster.Menu_Buffs_Lore_2",
			"Quartermaster.Menu_Buffs_Active", "Quartermaster.Menu_Garrison_Name", "Quartermaster.Menu_Garrison_Lore_1",
			"Quartermaster.Menu_Garrison_Lore_2", "Quartermaster.Menu_Garrison_Lore_3",
			"Quartermaster.Menu_Garrison_Stock",
			"Quartermaster.Buffs_Effect", "Quartermaster.Buffs_Duration", "Quartermaster.Buffs_Cost",
			"Quartermaster.Buffs_Click", "Quartermaster.Buffs_Insufficient",
			"Quartermaster.Garrison_Name", "Quartermaster.Garrison_Stock", "Quartermaster.Garrison_Per_Guard",
			"Quartermaster.Garrison_Bank", "Quartermaster.Garrison_Buy_Name", "Quartermaster.Garrison_Buy_Cost",
			"Quartermaster.Garrison_Buy_Click", "Quartermaster.Garrison_Buy_Insufficient",
			"Quartermaster.Bought_Guard", "Quartermaster.Bought_Boost", "Quartermaster.Cannot_Afford",
			"Quartermaster.Reserve_Full", "Quartermaster.No_Rank", "Quartermaster.Allies_Look",
			"Quartermaster.Turf_Gone", "Quartermaster.Owner_Changed", "Quartermaster.Not_Member", "Quartermaster.Not_Allied",
			"Quartermaster.No_Owner", "Quartermaster.Only_Owner_Or_Allies", "Quartermaster.Gang_Missing");

	private static YamlConfiguration load(String resource) throws IOException {
		return YamlConfiguration.loadConfiguration(new StringReader(YamlFixture.shipped(resource)));
	}

	@Test
	@DisplayName("turf_messages.yml has every Quartermaster line the panels read")
	void messagesPresent() throws IOException {
		YamlConfiguration yaml = load("/turf/turf_messages.yml");

		for (String key : MESSAGE_KEYS) {
			assertTrue(yaml.isSet(key), "missing " + key);
		}
	}

	@Test
	@DisplayName("every in-code Quartermaster fallback equals the shipped turf_messages.yml line (review blocker)")
	void fallbacksMatchShippedYaml() throws IOException {
		YamlConfiguration yaml = load("/turf/turf_messages.yml");
		Path              view = Path.of("src/main/java/org/luckyraven/gangland/turf/npc/view");
		Map<String, String> fallbacks = new HashMap<>();
		Map<String, String> constants = new HashMap<>();
		Pattern text     = Pattern.compile("\"(Quartermaster\\.[A-Za-z_]+)\",\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
		Pattern refusal  = Pattern.compile("case (\\w+) -> \"((?:[^\"\\\\]|\\\\.)*)\"");
		Pattern constant = Pattern.compile("String (\\w+)\\s*= \"(Quartermaster\\.[A-Za-z_]+)\"");

		try (var files = Files.list(view)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file);
				Matcher m = text.matcher(source);
				while (m.find()) {
					fallbacks.put(m.group(1), m.group(2));
				}
				Matcher c = constant.matcher(source);
				while (c.find()) {
					constants.put(c.group(1), c.group(2));
				}
				Matcher r = refusal.matcher(source);
				while (r.find()) {
					fallbacks.put(constants.getOrDefault(r.group(1), r.group(1)), r.group(2));
				}
			}
		}

		assertTrue(fallbacks.size() > 30, "the scan must find the panel fallbacks, found " + fallbacks.size());
		for (Map.Entry<String, String> entry : fallbacks.entrySet()) {
			assertTrue(yaml.isSet(entry.getKey()), "missing " + entry.getKey());
			assertEquals(yaml.getString(entry.getKey()), entry.getValue(), entry.getKey());
		}
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
}
