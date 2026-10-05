package org.luckyraven.gangland.data.teleportation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Docket LS-28: the waypoint shield used the entity {@code Invulnerable} flag, which vanilla writes to
 * {@code player.dat}. A quit, kick, crash, stop or reload before the clearing timer fired left the player permanently
 * invulnerable (no fall damage, no mob damage) with no in-game way to clear it.
 */
class WaypointTeleportShieldTest {

	private final List<Runnable> scheduled = new ArrayList<>();

	private MockedStatic<Bukkit> bukkit;
	private JavaPlugin           plugin;
	private Player               player;
	private User<Player>         user;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		bukkit = mockStatic(Bukkit.class);

		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(
				invocation -> {
					scheduled.add(invocation.getArgument(1));
					return mock(BukkitTask.class);
				});
		bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
		bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
		bukkit.when(() -> Bukkit.getWorld(eq("world"))).thenReturn(mock(World.class));

		plugin = mock(JavaPlugin.class);
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getLocation()).thenReturn(mock(Location.class));

		user = mock(User.class);
		when(user.getUser()).thenReturn(player);
	}

	@AfterEach
	void tearDown() {
		WaypointTeleport.removeCooldown(player);
		bukkit.close();
	}

	@Test
	@DisplayName("a shielded teleport never writes the persistent Invulnerable flag")
	void shieldDoesNotSetInvulnerableFlag() throws IllegalTeleportException {
		teleportWithShield(5);

		verify(player, never()).setInvulnerable(true);
	}

	@Test
	@DisplayName("a shielded player's fall damage is cancelled until the shield runs out")
	void shieldCancelsDamageUntilItExpires() throws IllegalTeleportException {
		teleportWithShield(2);
		Runnable shieldTimer = scheduled.remove(0);

		EntityDamageEvent during = fall();
		listener().onShieldedDamage(during);
		assertTrue(during.isCancelled());

		// CountdownTimer: two counting runs, the third fires the after-callback
		for (int i = 0; i < 3; i++) {
			shieldTimer.run();
		}

		EntityDamageEvent after = fall();
		listener().onShieldedDamage(after);
		assertFalse(after.isCancelled());
	}

	@Test
	@DisplayName("the shield lets void damage through, like vanilla invulnerability")
	void shieldIgnoresVoid() throws IllegalTeleportException {
		teleportWithShield(5);

		EntityDamageEvent event = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.VOID, 4.0);
		listener().onShieldedDamage(event);

		assertFalse(event.isCancelled());
	}

	@Test
	@DisplayName("an unshielded player's fall damage is untouched")
	void unshieldedPlayerTakesFallDamage() {
		EntityDamageEvent event = fall();
		listener().onShieldedDamage(event);

		assertFalse(event.isCancelled());
	}

	@Test
	@DisplayName("a stale Invulnerable flag left in player.dat by the old shield is cleared on join")
	void joinClearsStaleInvulnerableFlag() {
		when(player.isInvulnerable()).thenReturn(true);

		listener().onJoin(new PlayerJoinEvent(player, "joined"));

		verify(player).setInvulnerable(false);
	}

	@Test
	@DisplayName("a player joining without the Invulnerable flag is left alone")
	void joinLeavesVulnerablePlayerAlone() {
		when(player.isInvulnerable()).thenReturn(false);

		listener().onJoin(new PlayerJoinEvent(player, "joined"));

		verify(player, never()).setInvulnerable(any(Boolean.class));
	}

	@Test
	@DisplayName("the join heal runs at LOWEST so a plugin granting Invulnerable on join runs after it")
	void joinHealRunsAtLowestPriority() throws NoSuchMethodException {
		EventHandler handler = WaypointTeleport.class.getMethod("onJoin", PlayerJoinEvent.class)
		                                             .getAnnotation(EventHandler.class);

		assertEquals(EventPriority.LOWEST, handler.priority());
	}

	@Test
	@DisplayName("the shield lets /kill (KILL, Paper 1.20.4+) and void through, like the old Invulnerable flag")
	void shieldIgnoresKillAndVoid() {
		assertTrue(WaypointTeleport.shieldIgnores("KILL"));
		assertTrue(WaypointTeleport.shieldIgnores("VOID"));
	}

	@Test
	@DisplayName("the shield cancels every other damage cause")
	void shieldCancelsOtherCauses() throws IllegalTeleportException {
		teleportWithShield(5);

		for (EntityDamageEvent.DamageCause cause : new EntityDamageEvent.DamageCause[]{
				EntityDamageEvent.DamageCause.FALL, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
				EntityDamageEvent.DamageCause.LAVA, EntityDamageEvent.DamageCause.SUICIDE}) {
			EntityDamageEvent event = new EntityDamageEvent(player, cause, 4.0);
			listener().onShieldedDamage(event);

			assertTrue(event.isCancelled(), cause.name());
		}
	}

	@Test
	@DisplayName("an earlier shield's timer running out does not end a later shield")
	void earlierShieldExpiryKeepsLaterShield() throws IllegalTeleportException {
		teleportWithShield(2);
		Runnable firstShield = scheduled.remove(0);

		teleportWithShield(2);

		// the first shield's timer reaches its after-callback after the second shield started
		for (int i = 0; i < 3; i++) {
			firstShield.run();
		}

		EntityDamageEvent event = fall();
		listener().onShieldedDamage(event);
		assertTrue(event.isCancelled());
	}

	private WaypointTeleport listener() {
		return new WaypointTeleport(new Waypoint("listener", "gangland"));
	}

	private EntityDamageEvent fall() {
		return new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, 4.0);
	}

	private void teleportWithShield(int shieldSeconds) throws IllegalTeleportException {
		Waypoint waypoint = new Waypoint("shield-test", "gangland");
		waypoint.setCoordinates("world", 0, 64, 0, 0F, 0F);
		waypoint.setShield(shieldSeconds);

		waypoint.getWaypointTeleport().teleport(plugin, user, (u, t) -> { });

		// the warm-up timer (timer = 0) teleports on its first run
		scheduled.remove(0).run();
	}

}
