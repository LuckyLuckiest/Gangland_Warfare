package org.luckyraven.gangland.sign.aspect;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedSettings;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * The {@code [WANTED]} sign routes every star change through {@link WantedStars} tagged {@link WantedCause#SIGN}: an
 * INCREASE starts the decay clock (docket WB-20), REMOVE and CLEAR carry the cause, and unreadable sign content fails
 * the sign instead of throwing out of the interact handler (docket WB-21 / LS-22, the wanted half).
 */
@DisplayName("WantedAspect - the [WANTED] sign")
class WantedAspectTest {

	@TempDir
	Path dir;

	private BukkitStatics       bukkit;
	private JavaPlugin          plugin;
	private Player              player;
	private User<Player>        user;
	private Wanted              wanted;
	private WantedAspect        aspect;
	private List<Event>         events;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(dir);
		Messages.init(new FakeMessageProvider());
		bukkit.statics().when(Bukkit::isPrimaryThread).thenReturn(true);

		events = new ArrayList<>();
		doAnswer(invocation -> {
			events.add(invocation.getArgument(0));
			return null;
		}).when(bukkit.pluginManager()).callEvent(any());
		when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));

		plugin = mock(JavaPlugin.class);
		WantedSettings settings = mock(WantedSettings.class);
		when(settings.isTimerEnabled()).thenReturn(true);
		when(settings.getTimerTime()).thenReturn(120);

		player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		wanted = new Wanted(plugin, 1, 5);
		wanted.setOwner(player);

		user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		aspect = new WantedAspect(users, new WantedStars(plugin, settings));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("INCREASE raises the stars and starts the decay clock, which stayed null before")
	void increase_startsADecayClock() {
		AspectResult result = aspect.execute(player, sign("increase", 2));

		assertTrue(result.isSuccess());
		assertEquals(2, wanted.getLevel());
		assertNotNull(wanted.getRepeatingTimer(), "a sign-raised level must decay like any other");
		verify(bukkit.scheduler()).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
	}

	@Test
	@DisplayName("INCREASE tags the level change with the SIGN cause")
	void increase_tagsSignCause() {
		aspect.execute(player, sign("increase", 1));

		assertEquals(WantedCause.SIGN, change().getCause());
	}

	@Test
	@DisplayName("REMOVE lowers by the amount in one change tagged SIGN")
	void remove_lowersByTheAmount_withSignCause() {
		wanted.setLevel(4);
		events.clear();

		aspect.execute(player, sign("remove", 2));

		assertEquals(2, wanted.getLevel());
		assertEquals(WantedCause.SIGN, change().getCause());
	}

	@Test
	@DisplayName("CLEAR resets to zero tagged SIGN")
	void clear_resetsWithSignCause() {
		wanted.setLevel(3);
		events.clear();

		aspect.execute(player, sign("clear", 0));

		assertEquals(0, wanted.getLevel());
		assertEquals(WantedCause.SIGN, change().getCause());
	}

	@Test
	@DisplayName("sign content that is not an operation fails the sign instead of throwing")
	void unknownContent_failsInsteadOfThrowing() {
		ParsedSign garbage = sign("bogus", 1);

		AspectResult result = assertDoesNotThrow(() -> aspect.execute(player, garbage));
		assertFalse(result.isSuccess());
		assertFalse(assertDoesNotThrow(() -> aspect.canExecute(player, garbage)));
		assertEquals(0, wanted.getLevel());
	}

	private WantedLevelChangeEvent change() {
		return events.stream()
		             .filter(WantedLevelChangeEvent.class::isInstance)
		             .map(WantedLevelChangeEvent.class::cast)
		             .reduce((first, second) -> second)
		             .orElseThrow(() -> new AssertionError("no WantedLevelChangeEvent fired"));
	}

	private static ParsedSign sign(String content, int amount) {
		ParsedSign sign = mock(ParsedSign.class);
		when(sign.getContent()).thenReturn(content);
		when(sign.getAmount()).thenReturn(amount);
		return sign;
	}

}
