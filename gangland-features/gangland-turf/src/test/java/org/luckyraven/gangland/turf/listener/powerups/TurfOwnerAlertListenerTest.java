package org.luckyraven.gangland.turf.listener.powerups;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupData;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupNpc;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.turf.npc.guard.TurfOwnerAlerts;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("TurfOwnerAlertListener - feeds guard/Quartermaster events into TurfOwnerAlerts (W7-E)")
class TurfOwnerAlertListenerTest {

	private TurfOwnerAlerts        alerts;
	private TurfDefenderDeployer   defenders;
	private TurfPowerupManager     powerups;
	private GangMembershipView     view;
	private TurfOwnerAlertListener listener;
	private CivilianNpc            npc;
	private LivingEntity           body;
	private LivingEntity           victim;

	@BeforeEach
	void setUp() {
		alerts    = mock(TurfOwnerAlerts.class);
		defenders = mock(TurfDefenderDeployer.class);
		powerups  = mock(TurfPowerupManager.class);
		view      = mock(GangMembershipView.class);
		GangMembership gangs = new GangMembership();
		gangs.install(view);
		TurfManager turfs = mock(TurfManager.class);
		Turf        turf  = mock(Turf.class);
		when(turf.getOwnerGangId()).thenReturn(1);
		when(turfs.get(4)).thenReturn(turf);
		listener = new TurfOwnerAlertListener(alerts, defenders, powerups, turfs, gangs);
		npc      = mock(CivilianNpc.class);
		body     = mock(LivingEntity.class);
		when(npc.getEntity()).thenReturn(null); // Citizens has despawned the body by MONITOR
		when(defenders.findOwningTurfId(any())).thenReturn(-1);
		when(defenders.turfOf(any())).thenReturn(-1);
		victim = mock(LivingEntity.class);
		TurfPowerupNpc victimQm = qm(4);
		when(powerups.getByEntity(victim)).thenReturn(victimQm);
	}

	private static TurfPowerupNpc qm(int turfId) {
		TurfPowerupNpc  qm   = mock(TurfPowerupNpc.class);
		TurfPowerupData data = mock(TurfPowerupData.class);
		when(data.getTurfId()).thenReturn(turfId);
		when(qm.getData()).thenReturn(data);
		return qm;
	}

	private Player playerIn(int gang, String name) {
		Player p  = mock(Player.class);
		UUID   id = UUID.randomUUID();
		when(p.getUniqueId()).thenReturn(id);
		when(p.getName()).thenReturn(name);
		when(view.gangIdOf(id)).thenReturn(gang);
		return p;
	}

	private EntityDamageByEntityEvent hit(Entity damager) {
		EntityDamageByEntityEvent e = mock(EntityDamageByEntityEvent.class);
		when(e.getEntity()).thenReturn(victim);
		when(e.getDamager()).thenReturn(damager);
		return e;
	}

	@Test
	@DisplayName("a guard death alerts guardDown for its turf only")
	void guardDeath_alertsGuardDown() {
		when(defenders.turfOf(npc)).thenReturn(3);
		listener.onCivilianDeath(new CivilianDeathEvent(npc, null, 0));
		verify(alerts).guardDown(3, npc);
		verify(alerts, never()).quartermasterDown(anyInt(), any());
	}

	@Test
	@DisplayName("a Quartermaster death alerts quartermasterDown")
	void quartermasterDeath_alertsQuartermasterDown() {
		TurfPowerupNpc bodyQm = qm(4);
		when(powerups.getByCivilian(npc)).thenReturn(bodyQm);
		listener.onCivilianDeath(new CivilianDeathEvent(npc, null, 0));
		verify(alerts).quartermasterDown(4, npc);
	}

	@Test
	@DisplayName("any other civilian death is silent")
	void otherCivilianDeath_silent() {
		listener.onCivilianDeath(new CivilianDeathEvent(npc, null, 0));
		verifyNoInteractions(alerts);
	}

	@Test
	@DisplayName("outsiders hitting the Quartermaster alert; owner, allies and own guards do not")
	void quartermasterHit_byRival_alerts_byFriendly_silent() {
		when(view.gangsAllied(5, 1)).thenReturn(true);
		when(view.gangsAllied(1, 5)).thenReturn(true);
		listener.onDamage(hit(playerIn(2, "Rat")));
		verify(alerts).quartermasterHit(4, "Rat");

		listener.onDamage(hit(playerIn(1, "Owner")));
		listener.onDamage(hit(playerIn(5, "Friend")));
		LivingEntity guard = mock(LivingEntity.class);
		when(defenders.findOwningTurfId(guard)).thenReturn(4);
		listener.onDamage(hit(guard));
		verify(alerts, times(1)).quartermasterHit(anyInt(), anyString());
		verify(alerts, never()).quartermasterHit(anyInt(), eq("Owner"));

		Arrow arrow = mock(Arrow.class);
		Player archer = playerIn(2, "Archer");
		when(arrow.getShooter()).thenReturn(archer);
		listener.onDamage(hit(arrow));
		verify(alerts).quartermasterHit(4, "Archer");
	}

	@Test
	@DisplayName("damage to anything but a Quartermaster is silent")
	void nonQuartermasterDamage_silent() {
		EntityDamageByEntityEvent e = mock(EntityDamageByEntityEvent.class);
		when(e.getEntity()).thenReturn(mock(LivingEntity.class));
		Player rat = playerIn(2, "Rat");
		when(e.getDamager()).thenReturn(rat);
		listener.onDamage(e);
		verifyNoInteractions(alerts);
	}
}
