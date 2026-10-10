package org.luckyraven.gangland.file.configuration.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-263: every shipped menu carries a top-level {@code Config_Version} so Keystone replaces it from the jar when the
 * plugin version changes. Without the key a 0.16.x menu (for example gang_info's Open.Condition) never reaches a server.
 */
@DisplayName("Shipped inventory menus - top-level Config_Version (T-263)")
class ShippedInventoryConfigVersionTest {

	private static final Path RESOURCES = Path.of("src/main/resources/inventory");

	private static final List<String> MENUS = List.of(
			"alliance_stat", "gang_info", "gang_stat", "phone", "phone_banking", "phone_bounty", "phone_contacts",
			"phone_gang", "phone_gang_search", "user_stat"
	);

	@Test
	@DisplayName("each of the 10 shipped menus has a top-level Config_Version key")
	void everyShippedMenu_hasTopLevelConfigVersion() throws IOException {
		for (String menu : MENUS) {
			List<String> lines = Files.readAllLines(RESOURCES.resolve(menu + ".yml"));
			assertTrue(lines.stream().anyMatch(line -> line.startsWith("Config_Version:")),
					menu + ".yml must declare a top-level Config_Version");
		}
	}
}
