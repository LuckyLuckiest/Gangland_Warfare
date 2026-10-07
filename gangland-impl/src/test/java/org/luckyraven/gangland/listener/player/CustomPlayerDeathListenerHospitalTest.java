package org.luckyraven.gangland.listener.player;

import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.teleportation.HospitalShield;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointManager;
import org.luckyraven.gangland.data.teleportation.WaypointTeleport;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Where a respawn lands once hospitals exist: the downed path ({@code performRespawn}) teleports straight to the
 * nearest HOSPITAL waypoint, a vanilla respawn is redirected there unless it is a bed or anchor, and either way the
 * respawn shield is granted. {@code Hospital.Enable} false and a world without a hospital are exactly 0.15.
 */
@DisplayName("CustomPlayerDeathListener - hospital respawn")
class CustomPlayerDeathListenerHospitalTest {

	@TempDir
	Path tempDir;

	private BukkitStatics     bukkit;
	private WaypointManager   waypoints;
	private HospitalShield    shield;
	private Player            player;
	private UUID              uuid;
	private Location          here;
	private Location          wardLocation;
	private Waypoint          ward;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		bukkit = BukkitStatics.install();

		uuid   = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		here = mock(Location.class);
		when(player.getLocation()).thenReturn(here);
		AttributeInstance maxHealth = mock(AttributeInstance.class);
		when(maxHealth.getValue()).thenReturn(20.0);
		when(player.getAttribute(any())).thenReturn(maxHealth);

		wardLocation = mock(Location.class);
		ward         = mock(Waypoint.class);
		when(ward.getName()).thenReturn("ward");
		when(ward.getLocation()).thenReturn(wardLocation);

		waypoints = mock(WaypointManager.class);
		shield    = mock(HospitalShield.class);

		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(mock(User.class));
		new CustomPlayerDeathListener(mock(Gangland.class), users, waypoints, shield);
	}

	@AfterEach
	void tearDown() {
		DownedPlayerRegistry.remove(uuid);
		bukkit.close();
	}

	private CustomPlayerDeathListener listener() {
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(mock(User.class));
		return new CustomPlayerDeathListener(mock(Gangland.class), users, waypoints, shield);
	}

	private void settings(boolean hospital) throws IOException {
		SettingsFixture.write(tempDir, """
				Money_Symbol: '$'
				Database:
				  Auto_Save:
				    Debug: false
				User:
				  Death:
				    Respawn:
				      Teleport:
				        Enable: true
				        Waypoint: "spawn"
				    Hospital:
				      Enable: %s
				      Shield_Seconds: 5
				""".formatted(hospital));
		SettingsFixture.initialize(tempDir);
	}

	private PlayerRespawnEvent respawnEvent(boolean bed, boolean anchor) {
		PlayerRespawnEvent event = mock(PlayerRespawnEvent.class);
		when(event.getPlayer()).thenReturn(player);
		when(event.isBedSpawn()).thenReturn(bed);
		when(event.isAnchorSpawn()).thenReturn(anchor);
		return event;
	}

	private void died(CustomPlayerDeathListener listener) {
		PlayerDeathEvent death = mock(PlayerDeathEvent.class);
		when(death.getEntity()).thenReturn(player);
		listener.onPlayerDeath(death);
	}

	@Test
	@DisplayName("a downed player wakes at the nearest hospital by a direct teleport")
	void performRespawn_teleportsToTheNearestHospital() throws IOException {
		settings(true);
		when(waypoints.nearest(here, Waypoint.WaypointType.HOSPITAL)).thenReturn(ward);

		CustomPlayerDeathListener.triggerManualRespawn(player);

		verify(player).teleport(wardLocation);
		verify(waypoints, never()).get(any(String.class));
	}

	@Test
	@DisplayName("with no hospital the configured respawn waypoint is used, as in 0.15")
	void noHospital_usesTheConfiguredWaypoint() throws Exception {
		settings(true);
		Waypoint         spawn    = mock(Waypoint.class);
		WaypointTeleport teleport = mock(WaypointTeleport.class);
		when(spawn.getWaypointTeleport()).thenReturn(teleport);
		when(waypoints.get("spawn")).thenReturn(spawn);

		CustomPlayerDeathListener.triggerManualRespawn(player);

		verify(teleport).teleport(any(), any(), any());
		verify(player, never()).teleport(wardLocation);
	}

	@Test
	@DisplayName("a vanilla respawn is redirected to the nearest hospital of the world he died in")
	void vanillaRespawn_setsTheNearestHospital() throws IOException {
		settings(true);
		when(waypoints.nearest(here, Waypoint.WaypointType.HOSPITAL)).thenReturn(ward);
		CustomPlayerDeathListener listener = listener();
		died(listener);
		PlayerRespawnEvent event = respawnEvent(false, false);

		listener.onPlayerRespawn(event);

		verify(event).setRespawnLocation(wardLocation);
	}

	@Test
	@DisplayName("a bed or anchor respawn is left alone")
	void bedRespawn_isLeftAlone() throws IOException {
		settings(true);
		when(waypoints.nearest(here, Waypoint.WaypointType.HOSPITAL)).thenReturn(ward);
		CustomPlayerDeathListener listener = listener();
		died(listener);
		PlayerRespawnEvent bed = respawnEvent(true, false);

		listener.onPlayerRespawn(bed);

		verify(bed, never()).setRespawnLocation(any(Location.class));
		died(listener);
		PlayerRespawnEvent anchor = respawnEvent(false, true);

		listener.onPlayerRespawn(anchor);

		verify(anchor, never()).setRespawnLocation(any(Location.class));
		verify(shield, never()).grant(any(Player.class));
	}

	@Test
	@DisplayName("Hospital.Enable false: a vanilla respawn is untouched and the downed path uses the old waypoint")
	void hospitalDisabled_vanillaRespawnUntouched() throws IOException {
		settings(false);
		when(waypoints.nearest(any(), eq(Waypoint.WaypointType.HOSPITAL))).thenReturn(ward);
		CustomPlayerDeathListener listener = listener();
		died(listener);
		PlayerRespawnEvent event = respawnEvent(false, false);

		listener.onPlayerRespawn(event);

		verify(event, never()).setRespawnLocation(any(Location.class));
		CustomPlayerDeathListener.triggerManualRespawn(player);
		verify(player, never()).teleport(wardLocation);
		verify(shield, never()).grant(any(Player.class));
	}

	@Test
	@DisplayName("a hospital respawn on the downed path grants the shield")
	void performRespawn_atAHospital_grantsTheShield() throws IOException {
		settings(true);
		when(waypoints.nearest(here, Waypoint.WaypointType.HOSPITAL)).thenReturn(ward);

		CustomPlayerDeathListener.triggerManualRespawn(player);

		verify(shield).grant(player);
	}

	@Test
	@DisplayName("a hospital respawn on the vanilla path grants the shield")
	void vanillaHospitalRespawn_grantsTheShield() throws IOException {
		settings(true);
		when(waypoints.nearest(here, Waypoint.WaypointType.HOSPITAL)).thenReturn(ward);
		CustomPlayerDeathListener listener = listener();
		died(listener);

		listener.onPlayerRespawn(respawnEvent(false, false));

		verify(shield).grant(player);
	}

	@Test
	@DisplayName("no hospital, no shield: the configured-waypoint fallback never grants one")
	void noHospital_grantsNoShield() throws IOException {
		settings(true);
		CustomPlayerDeathListener listener = listener();
		died(listener);

		listener.onPlayerRespawn(respawnEvent(false, false));
		CustomPlayerDeathListener.triggerManualRespawn(player);

		verify(shield, never()).grant(any(Player.class));
	}

}
