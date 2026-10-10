package org.luckyraven.gangland.turf.command;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.log.TurfEventBuffer;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pins the {@code /glw turf} gang summary (TF-37): the cap line, the last three event lines and the map link, shown
 * even when the gang holds no turfs (the gang that just lost its last one must still see its events).
 */
@DisplayName("TurfCommand gang summary - cap line, last three events, map link (TF-37)")
class TurfCommandSummaryTest {

	private static final long HOUR_MS = 3_600_000L;
	private static final long NOW     = 1_000L * HOUR_MS;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private TurfMessages           text;
	private TurfRules              rules;
	private TurfEventBuffer        events;
	private CommandSender          sender;
	private CommandSender.Spigot   spigot;
	private MockedStatic<Settings> settings;

	@BeforeEach
	void setUp() throws IOException {
		text     = TurfTextFixture.messages(tempDir, mock(TurfMessageContract.class));
		rules    = TurfTextFixture.rules(tempDir, "Income:\n   Idle_Hours: 24\n");
		events   = new TurfEventBuffer();
		sender   = mock(CommandSender.class);
		spigot   = mock(CommandSender.Spigot.class);
		when(sender.spigot()).thenReturn(spigot);
		settings = TurfTextFixture.stubMoneySymbol();
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	/** A gang at level 0 (cap 2) whose last member online was {@code lastOnline}. */
	private static Gang gang(long lastOnline) {
		Gang gang = CaptureFixtures.gang(1);
		Level level = new Level(20, 200);
		level.setLevelValue(0);
		when(gang.getLevel()).thenReturn(level);
		when(gang.getLastMemberOnlineAt()).thenReturn(lastOnline);
		return gang;
	}

	private void summary(Gang gang, int owned) {
		TurfCommand.sendGangSummary(sender, text, rules, events, gang, 1, owned, NOW);
	}

	@Test
	@DisplayName("zeroTurfs_stillShowsCapLineThenEventsThenMapLink: 0/2 and 0 paying, then both lines, then the map link")
	void zeroTurfs_stillShowsCapLineThenEventsThenMapLink() {
		events.record(1, 1L, "Lost Docks to Rats");
		events.record(1, 2L, "Held Docks against Vipers");

		summary(gang(NOW - HOUR_MS), 0);

		assertEquals(List.of("Turfs 0/2 - 0 paying", "Lost Docks to Rats", "Held Docks against Vipers"), sent());
		verify(spigot, times(1)).sendMessage(any(BaseComponent[].class));
	}

	@Test
	@DisplayName("overCap_showsThreeOfTwoPaying: an active gang with 3 turfs at cap 2 reads 3/2 and 2 paying")
	void overCap_showsThreeOfTwoPaying() {
		summary(gang(NOW - HOUR_MS), 3);

		assertEquals("Turfs 3/2 - 2 paying", sent().get(0));
	}

	@Test
	@DisplayName("dormant_showsZeroPaying: a gang past the idle window reads 0 paying even with turfs")
	void dormant_showsZeroPaying() {
		summary(gang(NOW - 25 * HOUR_MS), 2);

		assertEquals("Turfs 2/2 - 0 paying", sent().get(0));
	}

	/** Every plain-text line passed to {@code sendMessage(String)}, in order. */
	private List<String> sent() {
		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(sender, atLeast(1)).sendMessage(lines.capture());
		return lines.getAllValues().stream().map(net.md_5.bungee.api.ChatColor::stripColor).toList();
	}
}
