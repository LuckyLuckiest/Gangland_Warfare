package org.luckyraven.gangland.civilians.listener.civilian;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.bartizan.api.event.WeaponShootEvent;
import org.luckyraven.bartizan.api.weapon.ThrowableWeapon;
import org.luckyraven.bartizan.api.weapon.Weapon;
import org.luckyraven.bartizan.api.weapon.WeaponType;
import org.luckyraven.bartizan.api.weapon.dto.ThrowableData;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessTestKit;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("WitnessShotListener - a player's gunshot makes his next crime loud (0.21.1)")
class WitnessShotListenerTest {

	private final WitnessTestKit      kit      = new WitnessTestKit();
	private final WitnessShotListener listener = new WitnessShotListener(kit.service);
	private       MockedStatic<NpcSupport> npcSupport;

	@BeforeEach
	void setUp() {
		// run alone, no Bukkit server is set: NpcSupport must not reach for one
		npcSupport = mockStatic(NpcSupport.class);
		npcSupport.when(() -> NpcSupport.isNpc(any())).thenReturn(false);
	}

	@AfterEach
	void tearDown() {
		npcSupport.close();
	}

	@Test
	@DisplayName("a player's shot is heard: a civilian with its back turned turns to the noise and calls")
	void playerShotIsHeard() {
		CivilianNpc away  = kit.civilian(10, true, 270f);

		listener.onWeaponShoot(shot(WeaponType.GUN, kit.criminal));

		assertEquals(1, kit.witness("Car_Theft"));
		assertEquals(CivilianState.CALLING, away.getCurrentState());
	}

	@Test
	@DisplayName("an NPC's shot is not the player's: his crime stays silent")
	void npcShotIgnored() {
		CivilianNpc away  = kit.civilian(10, true, 270f);

		listener.onWeaponShoot(shot(WeaponType.GUN, mock(LivingEntity.class)));

		assertEquals(0, kit.witness("Car_Theft"));
		assertEquals(CivilianState.IDLE, away.getCurrentState());
	}

	@Test
	@DisplayName("a melee swing is not noise (not in Witness.Loud_Weapon_Types): his next crime stays silent")
	void meleeSwingIsSilent() {
		CivilianNpc away = kit.civilian(10, true, 270f);

		listener.onWeaponShoot(shot(WeaponType.MELEE, kit.criminal));

		assertEquals(0, kit.witness("Car_Theft"));
		assertEquals(CivilianState.IDLE, away.getCurrentState());
	}

	@Test
	@DisplayName("a thrown weapon is noise by default")
	void throwableIsLoud() {
		CivilianNpc away = kit.civilian(10, true, 270f);

		listener.onWeaponShoot(shot(WeaponType.THROWABLE, kit.criminal));

		assertEquals(1, kit.witness("Car_Theft"));
		assertEquals(CivilianState.CALLING, away.getCurrentState());
	}

	@Test
	@DisplayName("a grenade is heard when it blows up: a kill 3.1 s after the throw (3 s fuse) is still loud")
	void grenadeKillAfterTheFuseIsLoud() {
		CivilianNpc away = kit.civilian(10, true, 270f);

		listener.onWeaponShoot(grenade(60, kit.criminal));
		kit.advanceSeconds(3.1);

		assertEquals(1, kit.witness("Kill_Civilian"));
		assertEquals(CivilianState.CALLING, away.getCurrentState());
	}

	@Test
	@DisplayName("the grenade's window still closes Loud_Shot_Seconds after the blast")
	void grenadeWindowClosesAfterTheBlast() {
		CivilianNpc away = kit.civilian(10, true, 270f);

		listener.onWeaponShoot(grenade(60, kit.criminal));
		kit.advanceSeconds(6.1);

		assertEquals(0, kit.witness("Kill_Civilian"));
		assertEquals(CivilianState.IDLE, away.getCurrentState());
	}

	@Test
	@DisplayName("a gunshot right after the throw does not cut the pending blast's window short")
	void gunshotAfterTheThrowKeepsTheBlastWindow() {
		CivilianNpc away = kit.civilian(10, true, 270f);

		listener.onWeaponShoot(grenade(60, kit.criminal));
		kit.advanceSeconds(1);
		listener.onWeaponShoot(shot(WeaponType.GUN, kit.criminal));
		kit.advanceSeconds(4.9);

		assertEquals(1, kit.witness("Kill_Civilian"));
		assertEquals(CivilianState.CALLING, away.getCurrentState());
	}

	@Test
	@DisplayName("a grenade thrown after a gunshot stretches the window to its own blast")
	void grenadeAfterAGunshotStretchesTheWindow() {
		CivilianNpc away = kit.civilian(10, true, 270f);

		listener.onWeaponShoot(shot(WeaponType.GUN, kit.criminal));
		kit.advanceSeconds(1);
		listener.onWeaponShoot(grenade(60, kit.criminal));
		kit.advanceSeconds(5.9);

		assertEquals(1, kit.witness("Kill_Civilian"));
		assertEquals(CivilianState.CALLING, away.getCurrentState());
	}

	private static WeaponShootEvent grenade(int fuseTicks, LivingEntity shooter) {
		ThrowableData data = mock(ThrowableData.class);
		when(data.getFuseTime()).thenReturn(fuseTicks);
		ThrowableWeapon weapon = mock(ThrowableWeapon.class);
		when(weapon.getThrowableData()).thenReturn(data);
		return shot(weapon, WeaponType.THROWABLE, shooter);
	}

	private static WeaponShootEvent shot(WeaponType type, LivingEntity shooter) {
		return shot(mock(Weapon.class), type, shooter);
	}

	private static WeaponShootEvent shot(Weapon weapon, WeaponType type, LivingEntity shooter) {
		when(weapon.getCategory()).thenReturn(type);
		WeaponShootEvent event = mock(WeaponShootEvent.class);
		when(event.getWeapon()).thenReturn(weapon);
		when(event.getShooter()).thenReturn(shooter);
		return event;
	}

	@Test
	@DisplayName("registered only when Bartizan is present")
	void bartizanGated() {
		assertEquals("isBartizanAvailable", WitnessShotListener.class.getAnnotation(ListenerHandler.class).condition());
	}
}
