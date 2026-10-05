package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.event.WeaponShootEvent;
import org.luckyraven.bartizan.api.weapon.Weapon;
import org.luckyraven.bartizan.api.weapon.WeaponType;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ShotNoiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
@DisplayName("ShotNoiseListener - a wanted player's gunshot tells the nearest cop where he is")
class ShotNoiseListenerTest {

	private final BukkitStatics        bukkit   = BukkitStatics.install();
	private final CopManager           manager  = mock(CopManager.class);
	private final CopLoader            loader   = mock(CopLoader.class);
	private final CopRadio             radio    = mock(CopRadio.class);
	private final UserManager<Player>  users    = mock(UserManager.class);
	private final CopConfigProvider    provider = mock(CopConfigProvider.class);
	private final CopGroup             group    = mock(CopGroup.class);
	private final NpcSquad             squad    = mock(NpcSquad.class);
	private final World                world    = mock(World.class);
	private final Player               shooter  = mock(Player.class);
	private final User<Player>         user     = mock(User.class);
	private final Wanted               wanted   = mock(Wanted.class);
	private final UUID                 id       = UUID.randomUUID();
	private final ShotNoiseListener    listener = new ShotNoiseListener(manager, loader, radio, users);

	ShotNoiseListenerTest() {
		when(loader.getLoadedProvider()).thenReturn(provider);
		when(provider.getShotNoiseSettings()).thenReturn(null); // DEFAULT: GUN 48, THROWABLE 16, MELEE 0
		when(shooter.getUniqueId()).thenReturn(id);
		when(shooter.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		when(users.getUser(shooter)).thenReturn(user);
		when(user.getWanted()).thenReturn(wanted);
		when(wanted.isWanted()).thenReturn(true);
		when(manager.groupOf(id)).thenReturn(group);
		when(group.getSquad()).thenReturn(squad);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private CopNpc cop(double x, CopState state) {
		CopNpc       cop    = mock(CopNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(new Location(world, x, 64, 0));
		when(cop.getEntity()).thenReturn(entity);
		when(cop.isValid()).thenReturn(true);
		when(cop.getCurrentState()).thenReturn(state);
		return cop;
	}

	private void cops(CopNpc... cops) {
		List<CopNpc> list = List.of(cops);
		when(group.getCops()).thenReturn(list);
	}

	private WeaponShootEvent shot(WeaponType type, org.bukkit.entity.LivingEntity who) {
		Weapon weapon = mock(Weapon.class);
		when(weapon.getCategory()).thenReturn(type);
		return new WeaponShootEvent(weapon, who);
	}

	@Test
	@DisplayName("a gunshot from a wanted shooter with a cop in range reports the sighting and radios Shots_Fired")
	void gunShot_wantedShooter_copInRange_reportsTheSighting_andRadios() {
		CopNpc cop = cop(30, CopState.PURSUING);
		cops(cop);

		listener.onWeaponShoot(shot(WeaponType.GUN, shooter));

		verify(squad).reportSighting(shooter.getLocation());
		verify(radio).sayAs(group, cop, "Shots_Fired", Map.of());
	}

	@Test
	@DisplayName("a melee swing makes no noise")
	void meleeSwing_makesNoNoise() {
		cops(cop(1, CopState.PURSUING));

		listener.onWeaponShoot(shot(WeaponType.MELEE, shooter));

		verifyNoInteractions(squad, radio);
	}

	@Test
	@DisplayName("a cop beyond the radius, or walking home, hears nothing")
	void copOutsideTheRadius_hearsNothing() {
		cops(cop(60, CopState.PURSUING), cop(5, CopState.RETURNING));

		listener.onWeaponShoot(shot(WeaponType.GUN, shooter));

		verifyNoInteractions(squad, radio);
	}

	@Test
	@DisplayName("a shooter who is not wanted is no business of the cops")
	void notWanted_nothing() {
		when(wanted.isWanted()).thenReturn(false);
		cops(cop(1, CopState.PURSUING));

		listener.onWeaponShoot(shot(WeaponType.GUN, shooter));

		verifyNoInteractions(squad, radio);
	}

	@Test
	@DisplayName("a non-player shooter is ignored")
	void npcShooter_isIgnored() {
		listener.onWeaponShoot(shot(WeaponType.GUN, mock(LivingEntity.class)));

		verifyNoInteractions(squad, radio, manager);
	}

	@Test
	@DisplayName("Shot_Noise disabled silences every weapon")
	void noiseDisabled_nothing() {
		when(provider.getShotNoiseSettings()).thenReturn(new ShotNoiseSettings(false, Map.of("GUN", 48.0)));
		cops(cop(1, CopState.PURSUING));

		listener.onWeaponShoot(shot(WeaponType.GUN, shooter));

		verifyNoInteractions(squad, radio);
	}

	@Test
	@DisplayName("a weapon type with no radius listed (a beam weapon) is silent")
	void unlistedBeamWeapon_nothing() {
		cops(cop(1, CopState.PURSUING));

		listener.onWeaponShoot(shot(WeaponType.BEAM, shooter));

		verifyNoInteractions(squad, radio);
	}

	@Test
	@DisplayName("only the nearest cop speaks")
	void onlyTheNearestCopSpeaks() {
		CopNpc far = cop(40, CopState.PURSUING), near = cop(10, CopState.PURSUING);
		cops(far, near);

		listener.onWeaponShoot(shot(WeaponType.GUN, shooter));

		verify(radio, times(1)).sayAs(any(), any(), eq("Shots_Fired"), any());
		verify(radio).sayAs(group, near, "Shots_Fired", Map.of());
	}
}
