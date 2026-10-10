package org.luckyraven.gangland.gang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The shipped gang/gang_messages.yml carries every Bounty.* and Progression.* key the W2-P1 code reads. A key missing
 * from the file would silently fall back to the in-code text, so this reads the file itself.
 */
@DisplayName("gang_messages.yml - the Bounty and Progression keys ship in the file")
class GangMessagesBountyYamlTest {

	private static YamlConfiguration shipped() throws Exception {
		try (InputStream in = GangMessagesBountyYamlTest.class.getResourceAsStream("/gang/gang_messages.yml")) {
			assertNotNull(in, "missing classpath resource /gang/gang_messages.yml");
			return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	@Test
	@DisplayName("every Bounty.* key read by the gang and turf code is present in the shipped file")
	void bountyKeys_present() throws Exception {
		YamlConfiguration yaml = shipped();

		for (String key : new String[]{"Bounty.Posted", "Bounty.Own_Gang", "Bounty.Ally", "Bounty.Unknown",
		                               "Bounty.Claimed", "Bounty.Lost", "Bounty.Bank_Full"}) {
			assertNotNull(yaml.getString(key), key + " is missing from gang_messages.yml");
		}
	}

	@Test
	@DisplayName("the Progression.Fee_Refund key is present in the shipped file")
	void progressionKeys_present() throws Exception {
		assertNotNull(shipped().getString("Progression.Fee_Refund"),
		              "Progression.Fee_Refund is missing from gang_messages.yml");
	}

}
