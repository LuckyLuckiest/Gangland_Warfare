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
import org.luckyraven.gangland.data.wanted.ContactDesk;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
	private ContactDesk         desk;
	private UUID                id;
	private final AtomicLong    now = new AtomicLong(1_000_000L);
	private List<Event>         events;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(dir);
		Messages.init(new FakeMessageProvider().withString("Wanted_Level.Contact.Seen", "SEEN")
		                                       .withString("Wanted_Level.Contact.Cooldown", "COOLDOWN %time%"));
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

		id     = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.isOnline()).thenReturn(true);
		wanted = new Wanted(plugin, 1, 5);
		wanted.setOwner(player);

		user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		desk   = new ContactDesk(now::get);
		aspect = new WantedAspect(users, new WantedStars(plugin, settings), desk);
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

	@Test
	@DisplayName("REMOVE while a cop sees the player is refused with the contact-seen text")
	void remove_whileSeen_refusesWithReason() {
		wanted.setLevel(3);
		desk.observe(id, EvasionState.SEEN);

		assertFalse(aspect.canExecute(player, sign("remove", 1, 500)));
		assertEquals(Messages.CONTACT_SEEN.toString(), aspect.failureReason(player, sign("remove", 1, 500)));
		assertFalse(aspect.canExecute(player, sign("clear", 0, 500)));
	}

	@Test
	@DisplayName("a paid REMOVE starts the shared contact cooldown")
	void paidRemove_startsTheCooldown() {
		wanted.setLevel(3);

		aspect.execute(player, sign("remove", 1, 500));

		assertTrue(desk.cooldownLeftMs(id) > 0);
	}

	@Test
	@DisplayName("a paid CLEAR inside the cooldown is refused with the cooldown text and a time")
	void paidClear_insideTheCooldown_refuses() {
		wanted.setLevel(3);
		desk.startCooldown(id);

		assertFalse(aspect.canExecute(player, sign("clear", 0, 500)));
		assertTrue(aspect.failureReason(player, sign("clear", 0, 500)).contains("10m"),
		           "the refusal names the time left");
	}

	@Test
	@DisplayName("a free (price 0) sign refuses while seen but neither checks nor starts the cooldown")
	void freeSign_whileSeen_refuses_butNeverCoolsDown() {
		wanted.setLevel(3);
		desk.startCooldown(id);

		assertTrue(aspect.canExecute(player, sign("remove", 1, 0)), "price 0 ignores the cooldown");
		aspect.execute(player, sign("remove", 1, 0));
		now.addAndGet(601_000L);
		assertEquals(0, desk.cooldownLeftMs(id));

		desk.observe(id, EvasionState.SEEN);
		assertFalse(aspect.canExecute(player, sign("remove", 1, 0)));
		assertEquals(0, desk.cooldownLeftMs(id), "a refusal never cools down");
	}

	@Test
	@DisplayName("INCREASE ignores both the sight gate and the cooldown")
	void increase_isNeverGated() {
		desk.observe(id, EvasionState.SEEN);
		desk.startCooldown(id);

		assertTrue(aspect.canExecute(player, sign("increase", 1, 500)));
		assertNull(aspect.failureReason(player, sign("increase", 1, 500)));
	}

	@Test
	@DisplayName("with Wanted.Contacts disabled the sign behaves as before")
	void contactsDisabled_signAsBefore() throws Exception {
		SettingsFixture.write(dir, "Wanted:\n  Contacts:\n    Enable: false\n");
		SettingsFixture.initialize(dir);
		wanted.setLevel(3);
		desk.observe(id, EvasionState.SEEN);
		desk.startCooldown(id);

		assertTrue(aspect.canExecute(player, sign("remove", 1, 500)));
		aspect.execute(player, sign("remove", 1, 500));
		assertEquals(2, wanted.getLevel());
	}

	private WantedLevelChangeEvent change() {
		return events.stream()
		             .filter(WantedLevelChangeEvent.class::isInstance)
		             .map(WantedLevelChangeEvent.class::cast)
		             .reduce((first, second) -> second)
		             .orElseThrow(() -> new AssertionError("no WantedLevelChangeEvent fired"));
	}

	private static ParsedSign sign(String content, int amount) {
		return sign(content, amount, 0);
	}

	private static ParsedSign sign(String content, int amount, double price) {
		ParsedSign sign = mock(ParsedSign.class);
		when(sign.getPrice()).thenReturn(price);
		when(sign.getContent()).thenReturn(content);
		when(sign.getAmount()).thenReturn(amount);
		return sign;
	}

}
