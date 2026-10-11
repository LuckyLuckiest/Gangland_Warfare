package org.luckyraven.gangland.turf.npc.view;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupNpc;
import org.luckyraven.gangland.turf.npc.config.TurfPowerupSettings;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.flow.MenuFlow;

import java.math.BigDecimal;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** W7-B (D29=B): the owning gang places its own Quartermaster from /glw turf quartermaster. */
@DisplayName("QuartermasterPlacement - owner placement")
@SuppressWarnings({"unchecked", "rawtypes"})
class QuartermasterPlacementTest {

	private static final int TURF_ID = 7;
	private static final int OWNER   = 1;

	private TurfManager            turfs;
	private GangLookupContract     gangs;
	private UserLookupContract     users;
	private QuartermasterAccess    access;
	private TurfPowerupManager     npcs;
	private TurfMessages           messages;
	private boolean                citizens;
	private double                 cost;
	private QuartermasterPlacement placement;

	private Turf                             turf;
	private Gang                             gang;
	private EconomyHandler                   bank;
	private Player                           player;
	private Location                         location;
	private User<Player>                     user;
	private MenuFlow<TurfPowerupFlowSession> flow;
	private TurfPowerupFlowSession           session;

	@BeforeEach
	void setUp() {
		turfs    = mock(TurfManager.class);
		gangs    = mock(GangLookupContract.class);
		users    = mock(UserLookupContract.class);
		access   = mock(QuartermasterAccess.class);
		npcs     = mock(TurfPowerupManager.class);
		when(npcs.place(anyInt(), any(), any())).thenReturn(mock(TurfPowerupNpc.class));
		messages = mock(TurfMessages.class);
		when(messages.text(anyString(), anyString(), any(Object[].class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(messages.text(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
		citizens = true;
		cost     = 5000.0;

		turf = mock(Turf.class);
		when(turf.getId()).thenReturn(TURF_ID);
		when(turf.getOwnerGangId()).thenReturn(OWNER);
		when(turf.getDisplayName()).thenReturn("Docks");
		when(turfs.get(TURF_ID)).thenReturn(turf);

		bank = new EconomyHandler(null);
		bank.setAmount(BigDecimal.valueOf(10_000));
		gang = mock(Gang.class);
		when(gang.getId()).thenReturn(OWNER);
		when(gang.getName()).thenReturn("Owners");
		when(gang.getEconomy()).thenReturn(bank);
		when(gangs.findById(OWNER)).thenReturn(gang);

		player   = mock(Player.class);
		location = mock(Location.class);
		when(player.getLocation()).thenReturn(location);
		when(player.getName()).thenReturn("Pat");
		user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(OWNER);
		when(users.findByPlayer(player)).thenReturn(user);
		when(turfs.findAt(location)).thenReturn(turf);

		flow    = mock(MenuFlow.class);
		session = new TurfPowerupFlowSession(turf, gang, gang, "Quartermaster");

		Supplier<TurfPowerupSettings> settings = () -> new TurfPowerupSettings("quartermaster", 32.0, "Quartermaster",
		                                                                       cost);
		placement = new QuartermasterPlacement(mock(JavaPlugin.class), mock(InventoryService.class), turfs, gangs,
		                                       users, access, npcs, messages, settings, () -> citizens,
		                                       "STONE", "&7");
	}

	@Test
	@DisplayName("denyKey: no Citizens")
	void denyKey_noCitizens() {
		citizens = false;
		assertEquals("Quartermaster.Place_No_Citizens", placement.denyKey(player, turf));
	}

	@Test
	@DisplayName("denyKey: unowned turf")
	void denyKey_unowned() {
		when(turf.getOwnerGangId()).thenReturn(null);
		assertEquals("Quartermaster.Place_Not_Owner", placement.denyKey(player, turf));
	}

	@Test
	@DisplayName("denyKey: gangless viewer")
	void denyKey_gangless() {
		when(user.hasGang()).thenReturn(false);
		assertEquals("Quartermaster.Place_Not_Owner", placement.denyKey(player, turf));
	}

	@Test
	@DisplayName("denyKey: another (allied) gang never places (D25=B)")
	void denyKey_otherGang() {
		when(user.getGangId()).thenReturn(99);
		assertEquals("Quartermaster.Place_Not_Owner", placement.denyKey(player, turf));
	}

	@Test
	@DisplayName("denyKey: rank refusal comes from QuartermasterAccess")
	void denyKey_noRank() {
		when(access.denyKey(eq(player), any())).thenReturn(QuartermasterAccess.NO_RANK);
		assertEquals(QuartermasterAccess.NO_RANK, placement.denyKey(player, turf));
	}

	@Test
	@DisplayName("denyKey: owner member with the rank may place")
	void denyKey_ok() {
		when(access.denyKey(eq(player), any())).thenReturn(null);
		assertNull(placement.denyKey(player, turf));
	}

	@Test
	@DisplayName("confirm charges the gang bank and places where the player stands")
	void confirm_chargesAndPlaces() {
		placement.confirm(flow, player, session);

		assertEquals(0, BigDecimal.valueOf(5000).compareTo(bank.getAmount()));
		verify(npcs).place(TURF_ID, location, null);
		verify(flow).end();
	}

	@Test
	@DisplayName("confirm refunds and does not announce when place() cannot spawn the Quartermaster")
	void confirm_placeReturnsNull_refundsAndFails() {
		when(npcs.place(anyInt(), any(), any())).thenReturn(null);

		placement.confirm(flow, player, session);

		assertEquals(0, BigDecimal.valueOf(10_000).compareTo(bank.getAmount()));
		verify(player).sendMessage("Quartermaster.Place_Failed");
		verify(player, never()).sendMessage("Quartermaster.Placed");
		verify(flow).end();
	}

	@Test
	@DisplayName("confirm with an empty bank charges and places nothing")
	void confirm_cannotAfford_noChargeNoPlace() {
		bank.setAmount(BigDecimal.valueOf(100));

		placement.confirm(flow, player, session);

		assertEquals(0, BigDecimal.valueOf(100).compareTo(bank.getAmount()));
		verify(npcs, never()).place(anyInt(), any(), any());
		verify(flow).rerender();
		verify(player).sendMessage("Quartermaster.Cannot_Afford");
	}

	@Test
	@DisplayName("confirm outside the turf is refused")
	void confirm_outsideTurf_refused() {
		when(turfs.findAt(location)).thenReturn(null);

		placement.confirm(flow, player, session);

		assertEquals(0, BigDecimal.valueOf(10_000).compareTo(bank.getAmount()));
		verify(npcs, never()).place(anyInt(), any(), any());
		verify(player).sendMessage("Quartermaster.Place_Outside");
		verify(flow).end();
	}

	@Test
	@DisplayName("confirm after the rank was lost is refused")
	void confirm_rankLostSinceOpen_refused() {
		when(access.denyKey(eq(player), any())).thenReturn(QuartermasterAccess.NO_RANK);

		placement.confirm(flow, player, session);

		assertEquals(0, BigDecimal.valueOf(10_000).compareTo(bank.getAmount()));
		verify(npcs, never()).place(anyInt(), any(), any());
	}

	@Test
	@DisplayName("a zero price places for free")
	void confirm_freeWhenCostZero() {
		cost = 0.0;

		placement.confirm(flow, player, session);

		assertEquals(0, BigDecimal.valueOf(10_000).compareTo(bank.getAmount()));
		verify(npcs).place(TURF_ID, location, null);
	}
}
