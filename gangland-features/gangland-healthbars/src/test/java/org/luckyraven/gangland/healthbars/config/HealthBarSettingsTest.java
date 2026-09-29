package org.luckyraven.gangland.healthbars.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("HealthBarSettings - bar rendering from healthbars.yml (phase H13)")
class HealthBarSettingsTest {

	@Test
	@DisplayName("full health fills every segment in High_Color")
	void full_allHigh() {
		assertEquals(color("&a||||||||||&8"), settings(new YamlConfiguration()).render(20, 20));
	}

	@Test
	@DisplayName("45% rounds up to 5 segments in Medium_Color")
	void medium() {
		assertEquals(color("&e|||||&8|||||"), settings(new YamlConfiguration()).render(9, 20));
	}

	@Test
	@DisplayName("10% is one Low_Color segment and nine Empty_Color ones; 0 is all empty")
	void low_andEmpty() {
		HealthBarSettings settings = settings(new YamlConfiguration());
		assertEquals(color("&c|&8|||||||||"), settings.render(2, 20));
		assertEquals(color("&c&8||||||||||"), settings.render(0, 20));
	}

	@Test
	@DisplayName("health above max clamps to a full bar")
	void aboveMax_clamps() {
		assertEquals(color("&a||||||||||&8"), settings(new YamlConfiguration()).render(30, 20));
	}

	@Test
	@DisplayName("Format, Symbol, Segments and the colours come from the file")
	void configured() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("Segments", 4);
		yaml.set("Symbol", "❤");
		yaml.set("Format", "%bar% &7%health%/%max%");
		yaml.set("High_Color", "&2");
		yaml.set("Empty_Color", "&0");

		assertEquals(color("&2❤❤❤&0❤ &715.0/20.0"), settings(yaml).render(15, 20));
	}

	@Test
	@DisplayName("an empty file keeps the bars enabled")
	void defaults_enabled() {
		assertTrue(settings(new YamlConfiguration()).enabled());
	}

	private static String color(String text) {
		return ChatColor.translateAlternateColorCodes('&', text);
	}

	private static HealthBarSettings settings(YamlConfiguration yaml) {
		FileManager fileManager = mock(FileManager.class);
		FileHandler handler     = mock(FileHandler.class);
		when(fileManager.getFile("healthbars")).thenReturn(handler);
		when(handler.getFileConfiguration()).thenReturn(yaml);
		return new HealthBarSettings(fileManager);
	}
}
