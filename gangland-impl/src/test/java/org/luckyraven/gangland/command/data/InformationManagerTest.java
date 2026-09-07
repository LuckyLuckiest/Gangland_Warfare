package org.luckyraven.gangland.command.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InformationManager#processCommands()} against the real bundled
 * {@code gangland-impl/src/main/resources/commands.json} (read straight off the test classpath, exactly as
 * {@code Gangland.class.getResourceAsStream("/commands.json")} does at runtime).
 *
 * <p>Pins Observation #10 (commands-messages-platform.md, High confidence): 19 entries document commands that no
 * longer exist in the codebase (the {@code dealer}/{@code kit}/{@code safe_wand}/{@code spawn}/{@code warp}
 * families). This is a drift test in the spirit of TESTING.md's "existing tests" note — if these entries are ever
 * cleaned up, this test should be updated (not treated as a regression) to reflect the smaller, curated file.
 */
@DisplayName("InformationManager")
class InformationManagerTest {

	private static final List<String> DEAD_ENTRIES = List.of(
			"dealer", "dealer_create", "dealer_remove",
			"kit", "kit_create", "kit_item", "kit_list", "kit_remove",
			"safe_wand",
			"spawn", "spawn_list", "spawn_remove", "spawn_set", "spawn_tp",
			"warp", "warp_list", "warp_remove", "warp_set", "warp_tp");

	@Test
	@DisplayName("processCommands populates every entry from the bundled commands.json")
	void processCommands_populatesFromBundledJson() {
		InformationManager manager = new InformationManager();

		manager.processCommands();

		assertEquals(225, manager.getCommands().size(),
				"pins the current entry count (232 minus the 7 mail entries that moved to the mail module's own "
				+ "commands.json in 0.8.2); update this alongside any deliberate commands.json edit");
		assertTrue(manager.getCommands().containsKey("general"));
		assertTrue(manager.getCommands().containsKey("general_page"));
	}

	@Test
	@DisplayName("merge folds a module's commands.json into the index and overwrites duplicate keys")
	void merge_addsModuleEntries() {
		InformationManager manager = new InformationManager();
		manager.processCommands();
		int before = manager.getCommands().size();

		String json = "{\"gang_invite_player\": {\"usage\": \"/glw gang invite <name>\", \"description\": \"Invites.\"},"
		              + " \"general\": {\"usage\": \"/glw\", \"description\": \"overridden\"}}";
		int added = manager.merge("mail", json.getBytes(StandardCharsets.UTF_8));

		assertEquals(2, added);
		assertEquals(before + 1, manager.getCommands().size(), "one new key, one overwritten");
		assertEquals("Invites.", manager.getCommands().get("gang_invite_player").description());
		assertEquals("overridden", manager.getCommands().get("general").description());
	}

	@Test
	@DisplayName("merge skips malformed input instead of throwing")
	void merge_malformedInput_skipped() {
		InformationManager manager = new InformationManager();

		assertEquals(0, manager.merge("broken", "not json".getBytes(StandardCharsets.UTF_8)));
		assertEquals(0, manager.merge("array", "[1,2]".getBytes(StandardCharsets.UTF_8)));
		assertTrue(manager.getCommands().isEmpty());
	}

	@Test
	@DisplayName("Observation #10 (commands-messages-platform.md): 19 dead entries for removed commands are " +
			"still present in commands.json")
	void observation10_deadEntriesStillPresent() {
		InformationManager manager = new InformationManager();

		manager.processCommands();

		for (String key : DEAD_ENTRIES) {
			assertTrue(manager.getCommands().containsKey(key), () -> "expected dead entry still present: " + key);
		}
	}

	@Test
	@DisplayName("every parsed entry has a non-null usage and description")
	void processCommands_everyEntryHasUsageAndDescription() {
		InformationManager manager = new InformationManager();

		manager.processCommands();

		for (Map.Entry<String, CommandInformation> entry : manager.getCommands().entrySet()) {
			assertNotNull(entry.getValue().usage(), () -> entry.getKey() + " has a null usage");
			assertNotNull(entry.getValue().description(), () -> entry.getKey() + " has a null description");
		}
	}
}
