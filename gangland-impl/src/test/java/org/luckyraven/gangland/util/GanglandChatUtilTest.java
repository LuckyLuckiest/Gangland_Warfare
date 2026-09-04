package org.luckyraven.gangland.util;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.support.SettingsFixture;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure string-shaping pin for {@link GanglandChatUtil}'s formatting helpers: {@code color} (ampersand codes plus
 * the {@code %money_symbol%} substitution), {@code commandDesign} (the {@code /glw}/{@code <}/{@code >}/{@code  - }
 * colouring plus bracket/comma stripping used by {@link org.luckyraven.gangland.command.HelpInfo}), and
 * {@code confirmCommand}/{@code setArguments}.
 *
 * <p>Every method here delegates to {@code color(...)}, which unconditionally reads
 * {@code Settings.getMoneySymbol()} (see {@link SettingsFixture}'s javadoc), so this class initializes it once.
 */
@DisplayName("GanglandChatUtil")
class GanglandChatUtilTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void initSettings() {
		SettingsFixture.initializeMinimal(tempDir);
	}

	@Test
	@DisplayName("color translates ampersand codes and substitutes %money_symbol%")
	void color_translatesAmpersandCodesAndMoneySymbol() {
		String result = GanglandChatUtil.color("&aPrice: %money_symbol%10");

		assertEquals(ChatColor.translateAlternateColorCodes('&', "&aPrice: $10"), result);
	}

	@Test
	@DisplayName("commandDesign colours /glw, angle brackets and the dash separator, and strips [ ] ,")
	void commandDesign_appliesAllFourTransformations() {
		String input           = "/glw help <page> - Shows help [aliases]";
		String expectedPreColor = "&6/glw&7 help &5<&7page&5>&7 &c-&r Shows help aliases";

		assertEquals(ChatColor.translateAlternateColorCodes('&', expectedPreColor),
				GanglandChatUtil.commandDesign(input));
	}

	@Test
	@DisplayName("commandDesign strips every bracket and comma even without a /glw prefix")
	void commandDesign_stripsBracketsAndCommasFromPlainText() {
		String result = GanglandChatUtil.commandDesign("[a, b, c]");

		assertEquals("a b c", result); // no ampersand codes present, so color() is a no-op besides the strip
	}

	@Test
	@DisplayName("confirmCommand builds the 'type confirm again' prompt for the given arguments")
	void confirmCommand_buildsConfirmationPrompt() {
		String result = GanglandChatUtil.confirmCommand(new String[]{"gang", "remove"});

		String expected = ChatColor.translateAlternateColorCodes('&',
				"&cYou need to confirm using &e/glw gang remove confirm &cto execute the command.");
		assertEquals(expected, result);
	}

	@Test
	@DisplayName("setArguments prepends the given text (with its own %money_symbol%) before the coloured command")
	void setArguments_prependsArgumentsBeforeCommandDesign() {
		String result = GanglandChatUtil.setArguments("Cost: %money_symbol%5 ", "/glw shop buy <item>");

		assertTrue(result.contains("Cost: $5"));
		assertTrue(result.contains("shop buy"));
	}
}
