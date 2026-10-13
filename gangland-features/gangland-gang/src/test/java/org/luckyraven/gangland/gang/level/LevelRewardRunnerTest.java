package org.luckyraven.gangland.gang.level;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.events.gangs.GangLevelReachedEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangLevels;
import org.luckyraven.gangland.gang.file.GangRules.LevelRowValues;
import org.luckyraven.gangland.gang.member.Member;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/** Reward commands run from the console for members old enough, online or not; the api event fires once. */
@DisplayName("LevelRewardRunner - level reward commands and the api event")
class LevelRewardRunnerTest {

	private static final long HOUR = 3_600_000L;
	private static final long NOW  = 1_000_000_000_000L;

	private MockedStatic<Bukkit> bukkit;
	private PluginManager        pluginManager;
	private List<String>         console;
	private Map<UUID, String>    names;
	private Gang                 gang;
	private LevelRewardRunner    runner;

	@BeforeEach
	void setUp() {
		IdentitySettings.bind(new FakeIdentitySettingsContract());
		GangLevels.bind(20, 200);
		pluginManager = mock(PluginManager.class);
		bukkit        = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

		console = new ArrayList<>();
		names   = new HashMap<>();
		gang    = new Gang(9, new ArrayList<>(), "Vipers");

		Map<Integer, LevelRowValues> rows = Map.of(
				2, new LevelRowValues(0, 0, 0, 0, "", List.of("give %player% diamond %level%",
				                                              "say %gang% #%gang_id% reached %level%")));
		runner = new LevelRewardRunner(new GangLevelTable(() -> rows), () -> 24, console::add, names::get, () -> NOW);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private Member member(String name, long joinedAgoHours) {
		Member member = new Member(UUID.randomUUID());
		member.setGangId(9);
		member.setGangJoinDateLong(NOW - joinedAgoHours * HOUR);
		names.put(member.getUuid(), name);
		gang.addMember(member);
		return member;
	}

	@Test
	@DisplayName("offlineMember_rewarded: the name lookup never needs the player to be online")
	void offlineMember_rewarded() {
		member("Rex", 48);

		runner.onLevelReached(gang, 2);

		assertEquals(List.of("give Rex diamond 2", "say Vipers #9 reached 2"), console);
	}

	@Test
	@DisplayName("freshJoiner_notRewarded: a member younger than the minimum age gets nothing, an exact-age one does")
	void freshJoiner_notRewarded() {
		member("Old", 24);
		member("Fresh", 23);

		runner.onLevelReached(gang, 2);

		assertTrue(console.contains("give Old diamond 2"));
		assertTrue(console.stream().noneMatch(line -> line.contains("Fresh")));
	}

	@Test
	@DisplayName("nullName_skipped: a member whose name is unknown is skipped, the others are still rewarded")
	void nullName_skipped() {
		Member nameless = member("x", 48);
		names.remove(nameless.getUuid());
		member("Rex", 48);

		runner.onLevelReached(gang, 2);

		assertEquals(List.of("give Rex diamond 2", "say Vipers #9 reached 2"), console);
	}

	@Test
	@DisplayName("nonAsciiName_usesId: %gang% falls back to the id when the name is not [A-Za-z0-9_]+")
	void nonAsciiName_usesId() {
		gang = new Gang(9, new ArrayList<>(), "Vipérs; op me");
		member("Rex", 48);

		runner.onLevelReached(gang, 2);

		assertEquals(List.of("give Rex diamond 2", "say 9 #9 reached 2"), console);
	}

	@Test
	@DisplayName("the api GangLevelReachedEvent fires once, also when the level has no commands")
	void eventFiresOnce() {
		runner.onLevelReached(gang, 5);

		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(pluginManager).callEvent(captor.capture());
		GangLevelReachedEvent event = (GangLevelReachedEvent) captor.getValue();
		assertEquals(9, event.gangId());
		assertEquals(5, event.level());
		assertTrue(console.isEmpty());
	}
}
