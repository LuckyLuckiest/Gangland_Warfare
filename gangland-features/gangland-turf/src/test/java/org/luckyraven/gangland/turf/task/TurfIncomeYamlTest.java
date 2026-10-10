package org.luckyraven.gangland.turf.task;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.support.YamlFixture;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every turf_messages.yml key the income payout line, the turf cap line and the while-you-were-away digest read
 * exists in the shipped file (TF-37), so a server that copies the defaults shows the shipped wording.
 */
@DisplayName("Turf income and digest YAML - every key read is in the shipped turf_messages.yml (TF-37)")
class TurfIncomeYamlTest {

	private static final List<String> KEYS = List.of(
			"Income.Action_Bar", "Income.Cap_Line",
			"Digest.Income", "Digest.Captured", "Digest.Lost", "Digest.Held", "Digest.Stalled",
			"Digest.Released_Inactive", "Digest.Released_Orphaned");

	@Test
	@DisplayName("turf_messages.yml has every Income and Digest key the W2-N2 code reads")
	void incomeAndDigestKeysPresent() throws IOException {
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
				new StringReader(YamlFixture.shipped("/turf/turf_messages.yml")));

		for (String key : KEYS) {
			assertTrue(yaml.contains(key), "turf_messages.yml is missing " + key);
		}
	}
}
