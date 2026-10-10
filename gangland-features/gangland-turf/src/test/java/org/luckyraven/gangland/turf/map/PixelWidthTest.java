package org.luckyraven.gangland.turf.map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.support.YamlFixture;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The vanilla font advance table of {@link PixelWidth} and the shipped map lines that must fit 300 px. */
@DisplayName("PixelWidth - vanilla advance table and the shipped map lines")
class PixelWidthTest {

	private static String shipped(String key) throws Exception {
		String yaml = YamlFixture.shipped("/turf/turf_messages.yml");
		return YamlConfiguration.loadConfiguration(new StringReader(yaml)).getString(key);
	}

	@Test
	@DisplayName("the glyphs of the table measure as the vanilla font advances them")
	void table() {
		assertEquals(6, PixelWidth.of("-"));
		assertEquals(4, PixelWidth.of("I"));
		assertEquals(4, PixelWidth.of(" "));
		assertEquals(2, PixelWidth.of("i"));
		assertEquals(3, PixelWidth.of("l"));
		assertEquals(5, PixelWidth.of("("));
		assertEquals(7, PixelWidth.of("@"));
	}

	@Test
	@DisplayName("colour and format codes measure zero")
	void colourCodesAreFree() {
		assertEquals(0, PixelWidth.of("&c&l&r"));
		assertEquals(0, PixelWidth.of("§7§o"));
	}

	@Test
	@DisplayName("the shipped Legend_Key, Header and Header_Gangless fit in 300 px")
	void shippedLinesFit() throws Exception {
		String key = shipped("Map.Legend_Key");
		String header = shipped("Map.Header").replace("%owned%", "99");
		String gangless = shipped("Map.Header_Gangless");

		assertTrue(PixelWidth.of(key) <= 300, "Legend_Key " + PixelWidth.of(key));
		assertTrue(PixelWidth.of(header) <= 300, "Header " + PixelWidth.of(header));
		assertTrue(PixelWidth.of(gangless) <= 300, "Header_Gangless " + PixelWidth.of(gangless));
	}
}
