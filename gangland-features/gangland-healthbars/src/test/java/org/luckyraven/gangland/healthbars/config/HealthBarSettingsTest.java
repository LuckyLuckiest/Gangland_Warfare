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

	private static final String F = "&c❤", H = "&c♡", E = "&8❤";

	private static String hearts(int full, int half) {
		return color(F.repeat(full) + H.repeat(half) + E.repeat(10 - full - half));
	}

	@Test
	@DisplayName("100% is ten full hearts; 0 is ten empty ones")
	void fullAndEmpty() {
		HealthBarSettings settings = settings(new YamlConfiguration());
		assertEquals(hearts(10, 0), settings.render(20, 20));
		assertEquals(hearts(0, 0), settings.render(0, 20));
	}

	@Test
	@DisplayName("50% is five full hearts")
	void half() {
		assertEquals(hearts(5, 0), settings(new YamlConfiguration()).render(10, 20));
	}

	@Test
	@DisplayName("5% rounds up to one half heart, 95% to nine full and a half")
	void roundsUpToHalves() {
		HealthBarSettings settings = settings(new YamlConfiguration());
		assertEquals(hearts(0, 1), settings.render(1, 20));
		assertEquals(hearts(9, 1), settings.render(19, 20));
	}

	@Test
	@DisplayName("a quarter heart still shows a half; 55% shows five full and a half")
	void partialHeart() {
		HealthBarSettings settings = settings(new YamlConfiguration());
		assertEquals(hearts(0, 1), settings.render(0.2, 20));
		assertEquals(hearts(5, 1), settings.render(11, 20));
	}

	@Test
	@DisplayName("a hurt NPC never reads as full, even at 99%")
	void hurtNeverFull() {
		assertEquals(hearts(9, 1), settings(new YamlConfiguration()).render(19.8, 20));
	}

	@Test
	@DisplayName("health above max clamps to a full bar")
	void aboveMax_clamps() {
		assertEquals(hearts(10, 0), settings(new YamlConfiguration()).render(30, 20));
	}

	@Test
	@DisplayName("Hearts, the three glyphs and Format come from the file")
	void configured() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("Hearts", 4);
		yaml.set("Full_Heart", "A");
		yaml.set("Half_Heart", "B");
		yaml.set("Empty_Heart", "C");
		yaml.set("Format", "%bar% &7%health%/%max%");

		assertEquals(color("AABC &711.0/20.0"), settings(yaml).render(11, 20));
	}

	@Test
	@DisplayName("legacy Segments maps to Hearts when Hearts is absent; an explicit Hearts wins; Symbol is ignored")
	void legacyKeys() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("Segments", 3);
		yaml.set("Symbol", "|");
		assertEquals(color("&c❤&c❤&c❤"), settings(yaml).render(20, 20));

		yaml.set("Hearts", 2);
		assertEquals(color("&c❤&c❤"), settings(yaml).render(20, 20));
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
