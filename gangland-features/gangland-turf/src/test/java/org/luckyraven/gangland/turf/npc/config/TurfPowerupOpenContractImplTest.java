package org.luckyraven.gangland.turf.npc.config;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.view.TurfPowerupFlow;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TurfPowerupOpenContractImplTest {

	private TurfPowerupFlow             flow;
	private TurfManager                 turfs;
	private GangLookupContract          gangs;
	private UserLookupContract          users;
	private TurfMessages                messages;
	private Player                      viewer;
	private Turf                        turf;
	private Gang                        owner;
	/** Each entry: key, then the flattened name/value pairs. */
	private final List<Object[]>        calls = new ArrayList<>();
	private TurfPowerupOpenContractImpl impl;

	@BeforeEach
	void setUp() {
		flow     = mock(TurfPowerupFlow.class);
		turfs    = mock(TurfManager.class);
		gangs    = mock(GangLookupContract.class);
		users    = mock(UserLookupContract.class);
		messages = mock(TurfMessages.class);
		viewer   = mock(Player.class);
		when(messages.text(anyString(), anyString(), any(Object[].class))).thenAnswer(inv -> {
			Object[] args = inv.getArguments();
			Object[] rec  = new Object[args.length - 1];
			rec[0] = args[0];
			System.arraycopy(args, 2, rec, 1, args.length - 2);
			calls.add(rec);
			return String.valueOf(args[0]);
		});
		turf = mock(Turf.class);
		when(turf.getOwnerGangId()).thenReturn(1);
		when(turfs.get(7)).thenReturn(turf);
		owner = mock(Gang.class);
		when(owner.getId()).thenReturn(1);
		when(owner.getDisplayNameString()).thenReturn("Vipers");
		when(gangs.findById(1)).thenReturn(owner);
		impl = new TurfPowerupOpenContractImpl(flow, turfs, mock(TurfPowerupManager.class), gangs, users, messages,
		                                       () -> new TurfPowerupSettings("quartermaster", 32.0));
	}

	@SuppressWarnings("unchecked")
	private void user(int gangId) {
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(gangId);
		when(users.findByPlayer(viewer)).thenReturn(user);
	}

	private Object[] call(String key) {
		return calls.stream().filter(c -> key.equals(c[0])).findFirst().orElseThrow();
	}

	private String pair(Object[] rec, String name) {
		for (int i = 1; i + 1 < rec.length; i += 2) {
			if (name.equals(rec[i])) return String.valueOf(rec[i + 1]);
		}
		return null;
	}

	@Test
	void owner_opensHub() {
		user(1);
		impl.open(viewer, 7);
		verify(flow).start(viewer, turf, owner, owner, "Quartermaster");
	}

	@Test
	void ally_opensHub() {
		user(2);
		Gang ally = mock(Gang.class);
		when(ally.isAlly(owner)).thenReturn(true);
		when(gangs.findById(2)).thenReturn(ally);
		impl.open(viewer, 7);
		verify(flow).start(viewer, turf, owner, ally, "Quartermaster");
	}

	@Test
	void stranger_toldOwnerAndAllyCommand() {
		user(2);
		when(gangs.findById(2)).thenReturn(mock(Gang.class));
		impl.open(viewer, 7);
		verify(flow, never()).start(any(), any(), any(), any(), anyString());
		Object[] c = call("Quartermaster.Refuse_Stranger");
		assertEquals("Vipers", pair(c, "owner"));
		assertEquals("1", pair(c, "owner_id"));
	}

	@Test
	void noGang_toldOwnerAndHowToJoin() {
		when(users.findByPlayer(viewer)).thenReturn(null);
		impl.open(viewer, 7);
		verify(flow, never()).start(any(), any(), any(), any(), anyString());
		Object[] c = call("Quartermaster.Refuse_No_Gang");
		assertEquals("Vipers", pair(c, "owner"));
		assertEquals("1", pair(c, "owner_id"));
	}

	@Test
	void unowned_and_gone_unchanged() {
		impl.open(viewer, 99);
		assertTrue(calls.stream().anyMatch(c -> "Quartermaster.Turf_Gone".equals(c[0])));
		when(turf.getOwnerGangId()).thenReturn(null);
		impl.open(viewer, 7);
		assertTrue(calls.stream().anyMatch(c -> "Quartermaster.No_Owner".equals(c[0])));
		when(turf.getOwnerGangId()).thenReturn(1);
		when(gangs.findById(1)).thenReturn(null);
		impl.open(viewer, 7);
		assertTrue(calls.stream().anyMatch(c -> "Quartermaster.Gang_Missing".equals(c[0])));
	}
}
