package org.luckyraven.gangland.gang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every key {@code GangRules} reads exists in the shipped gang/gang_rules.yml. */
@DisplayName("GangRules keys - present in the shipped gang_rules.yml")
class GangRulesKeyParityTest {

	private static final List<String> KEYS = List.of(
			"Name_Pattern", "Ranks.Preset", "Ranks.Preset_Permissions.member");

	@Test
	@DisplayName("every key GangRules reads is set in gang/gang_rules.yml")
	void everyKeyIsShipped() throws Exception {
		try (InputStream stream = GangRulesKeyParityTest.class.getResourceAsStream("/gang/gang_rules.yml")) {
			assertNotNull(stream);
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
					new InputStreamReader(stream, StandardCharsets.UTF_8));
			for (String key : KEYS) {
				assertTrue(yaml.isSet(key), "missing " + key);
			}
		}
	}
}
