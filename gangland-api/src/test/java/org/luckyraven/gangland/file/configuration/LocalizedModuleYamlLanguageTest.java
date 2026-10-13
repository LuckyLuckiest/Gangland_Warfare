package org.luckyraven.gangland.file.configuration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.mockito.MockedStatic;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Docket GP-35 sibling: the language file for the picked language ({@code <base>_<code>.yml}, any code, not only
 * {@code es}) is read before English, and a key it lacks falls back to the English value, then to the code default.
 */
class LocalizedModuleYamlLanguageTest {

	private MockedStatic<Settings> settings;

	private static final class TestYaml extends LocalizedModuleYaml {
		TestYaml(FileManager fileManager, String baseName) {
			super(fileManager, baseName);
		}

		String read(String key, String fallback) {
			return raw(key, fallback);
		}
	}

	@AfterEach
	void tearDown() {
		if (settings != null) settings.close();
	}

	private void language(String code) {
		settings = mockStatic(Settings.class);
		settings.when(Settings::getLanguagePicked).thenReturn(code);
	}

	private FileManager files(YamlConfiguration english, YamlConfiguration localized, String code) throws Exception {
		FileManager manager = mock(FileManager.class);

		FileHandler en = mock(FileHandler.class);
		when(en.getFileConfiguration()).thenReturn(english);
		when(manager.getFile("demo")).thenReturn(en);

		if (localized == null) {
			doThrow(new IOException("missing")).when(manager).checkFileLoaded("demo_" + code);
		} else {
			FileHandler loc = mock(FileHandler.class);
			when(loc.getFileConfiguration()).thenReturn(localized);
			when(manager.getFile("demo_" + code)).thenReturn(loc);
		}

		return manager;
	}

	@Test
	@DisplayName("languageFile_picked_thenEnglishFallback")
	void languageFile_picked_thenEnglishFallback() throws Exception {
		YamlConfiguration english = new YamlConfiguration();
		english.set("Hello", "hello");
		english.set("OnlyEnglish", "only english");
		YamlConfiguration spanish = new YamlConfiguration();
		spanish.set("Hello", "hola");

		language("es");
		TestYaml yaml = new TestYaml(files(english, spanish, "es"), "demo");

		assertEquals("hola", yaml.read("Hello", "code"));
		assertEquals("only english", yaml.read("OnlyEnglish", "code"));
		assertEquals("code", yaml.read("Nowhere", "code"));
	}

	@Test
	@DisplayName("anyLanguageCode_isTried_notOnlySpanish")
	void anyLanguageCode_isTried_notOnlySpanish() throws Exception {
		YamlConfiguration english = new YamlConfiguration();
		english.set("Hello", "hello");
		YamlConfiguration german = new YamlConfiguration();
		german.set("Hello", "hallo");

		language("de");
		TestYaml yaml = new TestYaml(files(english, german, "de"), "demo");

		assertEquals("hallo", yaml.read("Hello", "code"));
	}

	@Test
	@DisplayName("missingLanguageFile_usesEnglish")
	void missingLanguageFile_usesEnglish() throws Exception {
		YamlConfiguration english = new YamlConfiguration();
		english.set("Hello", "hello");

		language("fr");
		TestYaml yaml = new TestYaml(files(english, null, "fr"), "demo");

		assertEquals("hello", yaml.read("Hello", "code"));
	}

	@Test
	@DisplayName("noEnglishFile_throws")
	void noEnglishFile_throws() throws Exception {
		language("en");
		FileManager manager = mock(FileManager.class);
		doThrow(new IOException("missing")).when(manager).checkFileLoaded("demo");

		assertThrows(PluginException.class, () -> new TestYaml(manager, "demo"));
	}
}
