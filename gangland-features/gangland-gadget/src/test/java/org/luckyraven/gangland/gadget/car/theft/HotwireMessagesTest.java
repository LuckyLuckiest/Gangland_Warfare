package org.luckyraven.gangland.gadget.car.theft;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.FileHandler;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hotwire strings live in the gadget module own {@code gadget/gadget_messages.yml}, never in api Messages.
 */
@DisplayName("HotwireMessages - strings from gadget_messages.yml")
class HotwireMessagesTest {

	private HotwireMessages messagesFor(YamlConfiguration yaml) {
		FileHandler handler = mock(FileHandler.class);
		when(handler.getFileConfiguration()).thenReturn(yaml);
		return new HotwireMessages(handler);
	}

	private YamlConfiguration shipped() throws Exception {
		YamlConfiguration yaml = new YamlConfiguration();
		try (Reader reader = new InputStreamReader(
				getClass().getResourceAsStream("/gadget/gadget_messages.yml"), StandardCharsets.UTF_8)) {
			yaml.load(reader);
		}
		return yaml;
	}

	@Test
	@DisplayName("shippedFileHasEveryHotwireKey")
	void shippedFileHasEveryHotwireKey() throws Exception {
		YamlConfiguration yaml = shipped();

		assertTrue(yaml.isString("Hotwire_Progress"));
		assertTrue(yaml.isString("Hotwire_Done"));
		assertTrue(yaml.isString("Hotwire_Cancel"));
		assertTrue(yaml.isString("Hotwire_No_Lockpick"));
		assertTrue(yaml.getString("Hotwire_Progress").contains("%seconds%"));
	}

	@Test
	@DisplayName("progressFillsSecondsLeft: the placeholder is replaced by the whole seconds left")
	void progressFillsSecondsLeft() throws Exception {
		String line = messagesFor(shipped()).progress(4);

		assertFalse(line.isEmpty());
		assertTrue(line.contains("4"));
		assertFalse(line.contains("%seconds%"));
	}

	@Test
	@DisplayName("doneCancelNoLockpickAreNonEmpty and colour-coded with section signs, not raw ampersands")
	void doneCancelNoLockpickAreNonEmpty() throws Exception {
		HotwireMessages messages = messagesFor(shipped());

		for (String line : new String[]{messages.done(), messages.cancel(), messages.noLockpick()}) {
			assertFalse(line.isEmpty());
			assertFalse(line.contains("&"), "colour codes must be translated");
		}
	}

	@Test
	@DisplayName("missingKeysFallBackToReadableDefaults: an old messages file never yields an empty action bar")
	void missingKeysFallBackToReadableDefaults() {
		HotwireMessages messages = messagesFor(new YamlConfiguration());

		assertFalse(messages.progress(3).isEmpty());
		assertTrue(messages.progress(3).contains("3"));
		assertFalse(messages.done().isEmpty());
		assertFalse(messages.cancel().isEmpty());
		assertFalse(messages.noLockpick().isEmpty());
	}
}
