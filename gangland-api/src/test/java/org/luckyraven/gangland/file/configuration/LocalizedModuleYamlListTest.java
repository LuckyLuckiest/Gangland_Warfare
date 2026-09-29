package org.luckyraven.gangland.file.configuration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link LocalizedModuleYaml#list} is the shape {@code RadioLines} implementations read radio/shout message lines
 * through: a real list, a scalar wrapped as one, an explicit empty list kept as silence, and a missing key falling
 * back.
 */
@DisplayName("LocalizedModuleYaml.list - list/scalar/missing/explicit-empty")
class LocalizedModuleYamlListTest {

	private YamlConfiguration config;
	private TestYaml          yaml;

	// LocalizedModuleYaml is abstract; a bare concrete subclass is enough since this test lives in the same
	// package and so can call the protected list(...) directly.
	private static final class TestYaml extends LocalizedModuleYaml {
		TestYaml(FileManager fileManager, String baseName) {
			super(fileManager, baseName);
		}
	}

	@BeforeEach
	void setUp() throws Exception {
		config = new YamlConfiguration();

		FileHandler fileHandler = mock(FileHandler.class);
		when(fileHandler.getFileConfiguration()).thenReturn(config);

		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("radio_messages")).thenReturn(fileHandler);

		yaml = new TestYaml(fileManager, "radio_messages");
	}

	@Test
	@DisplayName("a real list is returned as-is")
	void realList_returnedAsIs() {
		config.set("Compass", List.of("north", "east", "south", "west"));

		assertEquals(List.of("north", "east", "south", "west"), yaml.list("Compass", List.of("fallback")));
	}

	@Test
	@DisplayName("a scalar value is wrapped in a single-element list")
	void scalar_wrappedAsSingleElementList() {
		config.set("Format", "&9[RADIO] %line%");

		assertEquals(List.of("&9[RADIO] %line%"), yaml.list("Format", List.of("fallback")));
	}

	@Test
	@DisplayName("a missing key returns the fallback")
	void missingKey_returnsFallback() {
		assertEquals(List.of("fallback"), yaml.list("Nowhere", List.of("fallback")));
	}

	@Test
	@DisplayName("an explicit empty list means silent, not the fallback")
	void explicitEmptyList_isSilent_notFallback() {
		config.set("Reposition", List.of());

		List<String> result = yaml.list("Reposition", List.of("should-not-appear"));

		assertTrue(result.isEmpty());
	}
}
