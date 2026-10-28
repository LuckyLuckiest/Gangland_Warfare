package org.luckyraven.gangland.copsncrooks.wanted;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 0.21.0 fnd-origin: every new {@link WantedMessages.Key} exists with its path and its in-code fallback equal to the
 * text shipped in {@code copsncrooks/wanted_messages.yml}.
 */
@DisplayName("WantedMessages - the 0.21.0 keys")
class WantedMessagesOriginKeysTest {

	/** name, path, shipped text (= fallback) */
	private static final String[][] NEW_KEYS = {
			{"SURRENDER_ACCEPTED", "Surrender.Accepted", "&eHands where we can see them. &7The cops hold their fire, stay down."},
			{"SURRENDER_RESISTED", "Surrender.Resisted", "&cYou resisted arrest."},
			{"SURRENDER_TOO_HOT", "Surrender.Too_Hot", "&cAt this heat they are not taking you alive."},
			{"BUSTED_TITLE", "Busted.Title", "&c&lBUSTED"},
			{"BUSTED_SUBTITLE", "Busted.Subtitle", "&7A cop got the cuffs on you"},
			{"LOCKPICK_SNAPPED", "Lockpick.Snapped", "&7Your lockpick snapped."},
			{"CREW_HEAT_SHARED", "Crew_Heat.Shared", "&c%player%'s %crime% is on you too."},
			{"RESCUE_PROGRESS", "Rescue.Progress", "&eFreeing %player%... &f%seconds%s"},
			{"RESCUE_FREED", "Rescue.Freed", "&aYou broke %player% out. &cThe cops want you both."},
			{"RESCUE_FREED_BY", "Rescue.Freed_By", "&a%player% broke you out!"},
			{"REPORT_HEADER", "Pursuit_Report.Header", "&8&m-----&r &6&lPURSUIT REPORT &8&m-----"},
			{"REPORT_ENDING", "Pursuit_Report.Ending", "&7Ending: &f%ending%"},
			{"REPORT_DURATION", "Pursuit_Report.Duration", "&7Duration: &f%time%"},
			{"REPORT_PEAK", "Pursuit_Report.Peak", "&7Peak: %stars%"},
			{"REPORT_COPS_DOWN", "Pursuit_Report.Cops_Down", "&7Cops down: &f%count%"},
			{"REPORT_BUSTS", "Pursuit_Report.Busts", "&7Times cuffed: &f%count%"},
			{"REPORT_COST", "Pursuit_Report.Cost", "&7Cost to the city: &c%money_symbol%%amount%"},
			{"ENDING_ESCAPED", "Pursuit_Report.Endings.Escaped", "Escaped"},
			{"ENDING_SURRENDERED", "Pursuit_Report.Endings.Surrendered", "Surrendered"},
			{"ENDING_BUSTED", "Pursuit_Report.Endings.Busted", "Busted"},
			{"ENDING_ARRESTED", "Pursuit_Report.Endings.Arrested", "Arrested"},
			{"ENDING_WASTED", "Pursuit_Report.Endings.Wasted", "Wasted"},
			{"ENDING_PAID_OFF", "Pursuit_Report.Endings.Paid_Off", "Paid off"},
			{"ENDING_CLEARED", "Pursuit_Report.Endings.Cleared", "Cleared"},
			{"NEWS_LIVE", "Breaking_News.Live", "&4&lBREAKING &fPolice are chasing %player% across the city &c%stars%"},
			{"NEWS_END", "Breaking_News.End", "&4&lBREAKING &fThe %stars% &fchase of %player% is over: %ending%"},
	};

	/** 0.21.2 S3: the star card's cause line per ReportKind; name, path, shipped text (= fallback). */
	private static final String[][] REPORT_KEYS = {
			{"CARD_SEEN", "Hud.Card.Seen", "%crime% (seen by %count% officer(s))"},
			{"CARD_REPORTED", "Hud.Card.Reported", "%crime% (reported by %count% witness(es))"},
			{"CARD_MISSING", "Hud.Card.Missing", "%crime% (an officer stopped answering)"},
			{"CARD_SCENE", "Hud.Card.Scene", "%crime% (spotted near the scene)"},
	};

	private static YamlConfiguration shipped() {
		InputStream in = WantedMessagesOriginKeysTest.class.getClassLoader()
		                                                   .getResourceAsStream("copsncrooks/wanted_messages.yml");
		assertNotNull(in, "copsncrooks/wanted_messages.yml must ship in the module jar");
		return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
	}

	private static String[][] allKeys() {
		return Stream.concat(Arrays.stream(NEW_KEYS), Arrays.stream(REPORT_KEYS)).toArray(String[][]::new);
	}

	private static WantedMessages.Key key(String name) {
		return Arrays.stream(WantedMessages.Key.values()).filter(k -> k.name().equals(name)).findFirst()
		             .orElseThrow(() -> new AssertionError("WantedMessages.Key." + name + " is missing"));
	}

	@Test
	@DisplayName("every new key exists with the agreed path and fallback text")
	void newKeys_existWithPathAndFallback() {
		for (String[] row : allKeys()) {
			WantedMessages.Key key = key(row[0]);
			assertEquals(row[1], key.path, row[0]);
			assertEquals(row[2], key.fallback, row[0]);
		}
	}

	@Test
	@DisplayName("every new key has its line in the shipped wanted_messages.yml, equal to the in-code fallback")
	void newKeys_shippedTextEqualsFallback() {
		YamlConfiguration yaml = shipped();

		for (String[] row : allKeys()) {
			assertEquals(row[2], yaml.getString(row[1]), row[1] + " in wanted_messages.yml");
		}
	}

	@Test
	@DisplayName("no two keys share a path")
	void paths_areUnique() {
		long distinct = Arrays.stream(WantedMessages.Key.values()).map(k -> k.path).distinct().count();

		assertEquals(WantedMessages.Key.values().length, distinct);
	}
}
