package org.luckyraven.gangland.listener.gang;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.events.gangs.GangLevelReachedEvent;
import org.luckyraven.gangland.events.gangs.GangMemberJoinedEvent;
import org.luckyraven.gangland.events.gangs.GangMemberLeftEvent;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GP-36: key gang moments show a title. */
@DisplayName("GangNotifier - titles")
class GangNotifierTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private MockedStatic<Bukkit>   bukkit;
	private GangManager            gangManager;
	private GangNotifier           notifier;

	@BeforeEach
	void setUp() throws IOException {
		settings = mockStatic(Settings.class);
		settings.when(Settings::getLanguagePicked).thenReturn("en");
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		bukkit      = mockStatic(Bukkit.class);
		gangManager = mock(GangManager.class);
		notifier    = new GangNotifier(GangUxTestMessages.shipped(tempDir), gangManager);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	private Player online(UUID uuid) {
		Player player = mock(Player.class);
		bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
		return player;
	}

	private Gang rats() {
		Gang gang = mock(Gang.class);
		when(gang.getName()).thenReturn("Rats");
		when(gang.getDisplayNameString()).thenReturn("Rats");
		when(gangManager.getGang(7)).thenReturn(gang);
		return gang;
	}

	@Test
	@DisplayName("levelUp_title: every online member gets the level in the title")
	void levelUp_title() {
		UUID   a  = UUID.randomUUID();
		UUID   b  = UUID.randomUUID();
		Player pa = online(a);
		Gang   gang = rats();
		when(gang.getMembers()).thenReturn(List.of(new Member(a), new Member(b)));

		notifier.onLevel(new GangLevelReachedEvent(7, 3));

		verify(pa).sendTitle(contains("3"), contains("Rats"), anyInt(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("levelUp_title_defaultsWhenKeysMissing: an upgraded gang_messages.yml without Titles still shows one")
	void levelUp_title_defaultsWhenKeysMissing() throws IOException {
		notifier = new GangNotifier(GangUxTestMessages.empty(tempDir.resolve("old")), gangManager);
		UUID   a  = UUID.randomUUID();
		Player pa = online(a);
		Gang   gang = rats();
		when(gang.getMembers()).thenReturn(List.of(new Member(a)));

		notifier.onLevel(new GangLevelReachedEvent(7, 3));

		verify(pa).sendTitle(contains("3"), contains("Rats"), anyInt(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("joined_title: the joining player gets the gang name")
	void joined_title() {
		UUID   a  = UUID.randomUUID();
		Player pa = online(a);
		rats();

		notifier.onJoined(new GangMemberJoinedEvent(7, a));

		verify(pa).sendTitle(anyString(), contains("Rats"), anyInt(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("disband_title: a disbanded member gets a title, a member who left does not")
	void disband_title() {
		UUID   a  = UUID.randomUUID();
		UUID   b  = UUID.randomUUID();
		Player pa = online(a);
		Player pb = online(b);
		rats();

		notifier.onLeft(new GangMemberLeftEvent(7, a, GangMemberLeftEvent.Reason.DISBANDED));
		notifier.onLeft(new GangMemberLeftEvent(7, b, GangMemberLeftEvent.Reason.LEFT));

		verify(pa).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
		verify(pb, never()).sendTitle(anyString(), anyString(), anyInt(), anyInt(), anyInt());
	}

}
