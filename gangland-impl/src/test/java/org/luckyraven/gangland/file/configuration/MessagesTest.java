package org.luckyraven.gangland.file.configuration;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Messages#toString()} end to end against a {@link FakeMessageProvider}: each {@code Type} prefix, the
 * list-typed ({@code isList}) path, and the missing-key marker. Also {@link Messages#findMissingPaths}, the guard
 * that would have caught the 114 Spanish gaps documented in commands-messages-platform.md.
 *
 * <p>Every {@code getValue(...)} path routes through {@code GanglandChatUtil.color}, which unconditionally reads
 * {@code Settings.getMoneySymbol()} (see {@link SettingsFixture}'s javadoc for why it must be non-null even when
 * the message text has no {@code %money_symbol%} token), so this class initializes both process-wide statics it
 * needs in {@code @BeforeAll}.
 */
@DisplayName("Messages")
class MessagesTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void initStatics() {
		SettingsFixture.initializeMinimal(tempDir);
	}

	private static FakeMessageProvider fakeProvider() {
		return new FakeMessageProvider()
				.withString("Normal.Prefix", "[P] ")
				.withString("Commands.Prefix", "[C] ")
				.withString("Errors.Prefix", "[E] ")
				.withString("Information.Prefix", "[I] ")
				.withString("Safe.Robbed", "your safe was robbed")
				.withString("Commands.Economy.Balance.Player", "Your balance is %money_symbol%10")
				.withString("Errors.Player.Not_Player", "console cannot run this")
				.withString("Information.Gang.Kicked", "you were kicked")
				.withString("Waypoint.Cooldown", "wait %n%before teleporting")
				.withList("Level.Stats", List.of("line1", "line2"));
	}

	@Test
	@DisplayName("Type.PREFIX prepends Normal.Prefix and colours the result")
	void toString_prefixType() {
		Messages.init(fakeProvider());

		assertEquals("[P] your safe was robbed", Messages.SAFE_ROBBED.toString());
	}

	@Test
	@DisplayName("Type.COMMAND prepends Commands.Prefix and substitutes %money_symbol%")
	void toString_commandType_substitutesMoneySymbol() {
		Messages.init(fakeProvider());

		assertEquals("[C] Your balance is $10", Messages.BALANCE_PLAYER.toString());
	}

	@Test
	@DisplayName("Type.ERROR prepends Errors.Prefix")
	void toString_errorType() {
		Messages.init(fakeProvider());

		assertEquals("[E] console cannot run this", Messages.NOT_PLAYER.toString());
	}

	@Test
	@DisplayName("Type.INFORMATION prepends Information.Prefix")
	void toString_informationType() {
		Messages.init(fakeProvider());

		assertEquals("[I] you were kicked", Messages.KICKED_FROM_GANG.toString());
	}

	@Test
	@DisplayName("Type.OTHER has no prefix; %n% becomes a real newline")
	void toString_otherType_noPrefixAndNewlineToken() {
		Messages.init(fakeProvider());

		assertEquals("wait \nbefore teleporting", Messages.WAYPOINT_TELEPORT_COOLDOWN.toString());
	}

	@Test
	@DisplayName("a missing scalar key renders the literal <missing: path> marker")
	void toString_missingScalarKey_rendersMissingMarker() {
		Messages.init(fakeProvider());

		assertEquals("<missing: Errors.Rank.Invalid>", Messages.INVALID_RANK.toString());
	}

	@Test
	@DisplayName("a list-typed constant joins its lines with a real newline before colouring")
	void toString_listTypedConstant_joinsWithNewline() {
		Messages.init(fakeProvider());

		assertEquals("line1\nline2", Messages.LEVEL_STATS.toString());
	}

	@Test
	@DisplayName("ampersand colour codes are translated end to end through the real Type/prefix pipeline")
	void toString_translatesAmpersandColorCodes() {
		Messages.init(new FakeMessageProvider()
				.withString("Commands.Prefix", "[C] ")
				.withString("Commands.Economy.Balance.Player", "&aYour balance is %money_symbol%10"));

		String expected = ChatColor.translateAlternateColorCodes('&', "[C] &aYour balance is $10");
		assertEquals(expected, Messages.BALANCE_PLAYER.toString());
	}

	@Test
	@DisplayName("findMissingPaths reports declared paths absent from the given YAML and nothing else")
	void findMissingPaths_reportsAbsentPathsOnly() {
		YamlConfiguration yaml = new YamlConfiguration();
		yaml.set("Normal.Prefix", "[P] ");
		yaml.set("Safe.Robbed", "robbed");

		List<String> missing = Messages.findMissingPaths(yaml);

		assertFalse(missing.contains("Normal.Prefix"));
		assertFalse(missing.contains("Safe.Robbed"));
		assertTrue(missing.contains("Errors.Player.Not_Player"));
		assertTrue(missing.contains("Commands.Economy.Balance.Player"));
	}

	@Test
	@DisplayName("findMissingPaths on a completely empty YAML reports every declared path")
	void findMissingPaths_emptyYaml_reportsEveryPath() {
		List<String> missing = Messages.findMissingPaths(new YamlConfiguration());

		assertEquals(Messages.values().length, missing.size());
	}

	@Test
	@DisplayName("WS6 G3: a leftover Commands.Civilian message block logs a targeted warning naming "
			+ "npc/civilian_messages.yml")
	void init_legacyCivilianMessageBlock_logsTargetedMigrationWarning() {
		List<String> logs = captureWarnLogs(() -> Messages.init(fakeProvider()
				.withString("Commands.Civilian.List_Empty", "No civilians are currently active.")));

		assertTrue(logs.stream().anyMatch(m -> m.contains("Civilian") && m.contains("npc/civilian_messages.yml")
						&& m.contains("civilians")),
				"expected a targeted migration warning naming npc/civilian_messages.yml; got: " + logs);
	}

	@Test
	@DisplayName("WS6 G3: no leftover Commands.Civilian message block means no targeted migration warning")
	void init_noLegacyCivilianMessageBlock_logsNoMigrationWarning() {
		List<String> logs = captureWarnLogs(() -> Messages.init(fakeProvider()));

		assertTrue(logs.stream().noneMatch(m -> m.contains("npc/civilian_messages.yml")),
				"expected no targeted migration warning when the legacy block is absent; got: " + logs);
	}

	/**
	 * {@code Messages.init(...)} logs the Civilian legacy-block warning through {@link Settings}'s own
	 * {@code @CustomLog} field (the call is textually inside {@code Settings.warnIfLegacyShopBlockPresent}), so
	 * this attaches to {@link Settings}'s logger — same technique as {@code SettingsTest#captureWarnLogs}.
	 */
	private static List<String> captureWarnLogs(Runnable action) {
		Logger coreLogger    = (Logger) org.luckyraven.keystone.logging.Logger.getLogger(Settings.class);
		Level  originalLevel = coreLogger.getLevel();
		List<String> captured = new ArrayList<>();
		AbstractAppender appender = new AbstractAppender("messages-test-capture", null, null, false,
				Property.EMPTY_ARRAY) {
			@Override
			public void append(LogEvent event) {
				captured.add(event.getMessage().getFormattedMessage());
			}
		};
		appender.start();
		coreLogger.addAppender(appender);
		Configurator.setLevel(coreLogger, Level.WARN);
		try {
			action.run();
		} finally {
			Configurator.setLevel(coreLogger, originalLevel);
			coreLogger.removeAppender(appender);
			appender.stop();
		}
		return captured;
	}
}
