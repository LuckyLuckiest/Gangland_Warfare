package org.luckyraven.gangland.item.money;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped {@code items/money.yml}: one death costs one bill, so the PLAYER cash drop is off by default (the key
 * stays, an admin can turn it back on), while the COP drop is unchanged.
 */
@DisplayName("Bundled items/money.yml")
class BundledMoneyYmlTest {

	private static YamlConfiguration bundled() throws IOException {
		try (InputStream in = BundledMoneyYmlTest.class.getResourceAsStream("/items/money.yml")) {
			assertNotNull(in, "items/money.yml ships in the core jar");
			return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
	}

	@Test
	@DisplayName("PLAYER Enabled is false, the key still exists, COP stays enabled")
	void playerDropIsOff_copUnchanged() throws IOException {
		YamlConfiguration yaml = bundled();

		assertTrue(yaml.contains("Money.Drop_Sources.PLAYER.Enabled"), "the key is kept");
		assertFalse(yaml.getBoolean("Money.Drop_Sources.PLAYER.Enabled", true));
		assertTrue(yaml.getBoolean("Money.Drop_Sources.COP.Enabled", false));
	}

}
