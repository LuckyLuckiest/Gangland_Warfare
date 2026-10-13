package org.luckyraven.gangland.turf.listener.powerups;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer.GuardBounty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TurfDefenderDeathListener - reports guard deaths (T-251) and pays the rival bounty (T-700)")
class TurfDefenderDeathListenerTest {

	private static final int OWNER = 5;
	private static final int ALLY  = 6;
	private static final int RIVAL = 9;

	private TurfDefenderDeployer      deployer;
	private GangMembershipView        view;
	private CivilianNpc               npc;
	private final List<Integer>       dropped = new ArrayList<>();
	private TurfDefenderDeathListener listener;

	@BeforeEach
	void setUp() {
		deployer = mock(TurfDefenderDeployer.class);
		view     = mock(GangMembershipView.class);
		npc      = mock(CivilianNpc.class);
		LivingEntity body = mock(LivingEntity.class);
		when(body.getLocation()).thenReturn(mock(Location.class));
		when(npc.getEntity()).thenReturn(body);
		when(deployer.bountyOf(npc)).thenReturn(new GuardBounty(OWNER, 500));
		when(view.gangsAllied(OWNER, ALLY)).thenReturn(true);
		when(view.gangsAllied(ALLY, OWNER)).thenReturn(true);
		GangMembership gangs = new GangMembership();
		gangs.install(view);
		listener = new TurfDefenderDeathListener(deployer, gangs, mock(MoneyAddon.class),
		                                         mock(MoneyDepositService.class));
		listener.setDropper((at, amount) -> dropped.add(amount));
	}

	private Player killerIn(int gangId) {
		Player killer = mock(Player.class);
		UUID   id     = UUID.randomUUID();
		when(killer.getUniqueId()).thenReturn(id);
		when(view.gangIdOf(id)).thenReturn(gangId);
		return killer;
	}

	@Test
	@DisplayName("a CivilianDeathEvent for a guard marks that guard as killed")
	void civilianDeath_marksTheGuardKilled() {
		listener.onCivilianDeath(new CivilianDeathEvent(npc, null, 0.0));

		verify(deployer).markKilled(npc);
	}

	@Test
	@DisplayName("rivalKill_paysHalfThePrice: price 1000, share 0.5 -> 500 dropped once")
	void rivalKill_paysHalfThePrice() {
		listener.onCivilianDeath(new CivilianDeathEvent(npc, killerIn(RIVAL), 0.0));

		assertEquals(List.of(500), dropped);
	}

	@Test
	@DisplayName("a gangless killer is a rival too")
	void gangless_isARival() {
		listener.onCivilianDeath(new CivilianDeathEvent(npc, killerIn(-1), 0.0));

		assertEquals(List.of(500), dropped);
	}

	@Test
	@DisplayName("ownerKill_paysNothing")
	void ownerKill_paysNothing() {
		listener.onCivilianDeath(new CivilianDeathEvent(npc, killerIn(OWNER), 0.0));

		assertTrue(dropped.isEmpty());
	}

	@Test
	@DisplayName("allyKill_paysNothing")
	void allyKill_paysNothing() {
		listener.onCivilianDeath(new CivilianDeathEvent(npc, killerIn(ALLY), 0.0));

		assertTrue(dropped.isEmpty());
	}

	@Test
	@DisplayName("noKiller_nothing: a fall, a cop or the lifespan pays no one")
	void noKiller_nothing() {
		listener.onCivilianDeath(new CivilianDeathEvent(npc, null, 0.0));

		assertTrue(dropped.isEmpty());
	}

	@Test
	@DisplayName("not a tracked guard, an ownerless garrison, or a zero bounty: nothing")
	void noBounty_nothing() {
		when(deployer.bountyOf(npc)).thenReturn(null);
		listener.onCivilianDeath(new CivilianDeathEvent(npc, killerIn(RIVAL), 0.0));
		when(deployer.bountyOf(npc)).thenReturn(new GuardBounty(-1, 500));
		listener.onCivilianDeath(new CivilianDeathEvent(npc, killerIn(RIVAL), 0.0));
		when(deployer.bountyOf(npc)).thenReturn(new GuardBounty(OWNER, 0));
		listener.onCivilianDeath(new CivilianDeathEvent(npc, killerIn(RIVAL), 0.0));

		assertTrue(dropped.isEmpty());
	}
}
