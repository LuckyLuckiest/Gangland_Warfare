package org.luckyraven.gangland.turf.npc.guard;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TurfOwnerAlerts - owner-only chat + action bar alerts with per-turf per-kind cooldown (W7-E, D28=A)")
class TurfOwnerAlertsTest {

	private static final UUID A = UUID.randomUUID();
	private static final UUID B = UUID.randomUUID();

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> staticSettings;
	private TurfMessages           text;
	private TurfManager            turfs;
	private GangLookupContract     gangs;
	private Turf                   alpha;
	private Turf                   beta;
	private Turf                   unowned;
	private int                    cooldown;
	private long                   now;
	private TurfOwnerAlerts        alerts;
	private final List<String>     sent = new ArrayList<>();

	@BeforeEach
	void setUp() throws IOException {
		staticSettings = TurfTextFixture.stubMoneySymbol();
		text           = TurfTextFixture.messages(tempDir, mock(TurfMessageContract.class));
		turfs          = mock(TurfManager.class);
		alpha          = turf(1, "Alpha", 1);
		beta           = turf(2, "Beta", 1);
		unowned        = turf(3, "Gamma", null);
		when(turfs.get(1)).thenReturn(alpha);
		when(turfs.get(2)).thenReturn(beta);
		when(turfs.get(3)).thenReturn(unowned);

		Member ma = member(A);
		Member mb = member(B);
		Member mc = member(UUID.randomUUID());
		Gang owner = mock(Gang.class);
		when(owner.getMembers()).thenReturn(List.of(ma, mb));
		Gang ally = mock(Gang.class);
		when(ally.getMembers()).thenReturn(List.of(mc));
		gangs = mock(GangLookupContract.class);
		when(gangs.findById(1)).thenReturn(owner);
		when(gangs.findById(7)).thenReturn(ally);

		cooldown = 60;
		now      = 0;
		alerts   = new TurfOwnerAlerts(turfs, gangs, text, () -> cooldown);
		alerts.setClock(() -> now);
		alerts.setSender((id, line) -> sent.add(id + "|" + line));
	}

	@AfterEach
	void tearDown() {
		staticSettings.close();
	}

	private static Turf turf(int id, String name, Integer owner) {
		Turf t = mock(Turf.class);
		when(t.getId()).thenReturn(id);
		when(t.getDisplayName()).thenReturn(name);
		when(t.getOwnerGangId()).thenReturn(owner);
		return t;
	}

	private static Member member(UUID id) {
		Member m = mock(Member.class);
		when(m.getUuid()).thenReturn(id);
		return m;
	}

	private static Player player(String name) {
		Player p = mock(Player.class);
		when(p.getName()).thenReturn(name);
		return p;
	}

	@Test
	@DisplayName("a cop hit reaches exactly the owner gang members")
	void copHit_reachesOwnerMembersOnly() {
		alerts.copHit(alpha, player("Cal"));
		assertEquals(2, sent.size());
		assertTrue(sent.stream().anyMatch(s -> s.startsWith(A + "|")));
		assertTrue(sent.stream().anyMatch(s -> s.startsWith(B + "|")));
	}

	@Test
	@DisplayName("the line is the shipped Cop_Hit text with turf and victim")
	void copHit_lineNamesTurfAndVictim() {
		alerts.copHit(alpha, player("Cal"));
		String expected = text.text("Alerts.Cop_Hit", "x", "turf", "Alpha", "player", "Cal");
		assertEquals(A + "|" + expected, sent.get(0));
		assertTrue(expected.contains("Alpha") && expected.contains("Cal"));
	}

	@Test
	@DisplayName("one line per turf per kind per cooldown")
	void cooldown_perTurfPerKind() {
		alerts.copHit(alpha, player("Cal"));
		alerts.copHit(alpha, player("Cal"));
		assertEquals(2, sent.size());
		now = 59_999;
		alerts.copHit(alpha, player("Cal"));
		assertEquals(2, sent.size());
		now = 60_000;
		alerts.copHit(alpha, player("Cal"));
		assertEquals(4, sent.size());
		now = 0;
		alerts.copHit(beta, player("Cal"));
		assertEquals(6, sent.size());
		alerts.guardDown(1, new Object());
		assertEquals(8, sent.size());
	}

	@Test
	@DisplayName("cooldown 0 sends every time")
	void cooldownZero_sendsEveryTime() {
		cooldown = 0;
		for (int i = 0; i < 3; i++) {
			alerts.copHit(alpha, player("Cal"));
		}
		assertEquals(6, sent.size());
	}

	@Test
	@DisplayName("a guard death is told once per NPC")
	void guardDown_oncePerNpc() {
		cooldown = 0;
		Object a = new Object();
		alerts.guardDown(1, a);
		alerts.guardDown(1, a);
		assertEquals(2, sent.size());
		alerts.guardDown(1, new Object());
		assertEquals(4, sent.size());
	}

	@Test
	@DisplayName("a Quartermaster death is told once per NPC")
	void quartermasterDown_oncePerNpc() {
		cooldown = 0;
		Object qm = new Object();
		alerts.quartermasterDown(1, qm);
		alerts.quartermasterDown(1, qm);
		assertEquals(2, sent.size());
	}

	@Test
	@DisplayName("unowned or unknown turfs are silent")
	void unownedOrMissingTurf_silent() {
		alerts.copHit(unowned, player("Cal"));
		alerts.quartermasterHit(3, "Rat");
		alerts.guardDown(99, new Object());
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("in-code fallbacks equal the shipped YAML lines")
	void fallbacksMatchShippedYaml() throws IOException {
		TurfMessages empty = TurfTextFixture.emptyMessages(tempDir.resolve("empty"), mock(TurfMessageContract.class));
		List<String> shipped  = new ArrayList<>();
		List<String> fallback = new ArrayList<>();
		TurfOwnerAlerts s = new TurfOwnerAlerts(turfs, gangs, text, () -> 0);
		TurfOwnerAlerts f = new TurfOwnerAlerts(turfs, gangs, empty, () -> 0);
		s.setSender((id, l) -> {
			if (A.equals(id)) shipped.add(l);
		});
		f.setSender((id, l) -> {
			if (A.equals(id)) fallback.add(l);
		});
		for (TurfOwnerAlerts x : List.of(s, f)) {
			x.copHit(alpha, player("Cal"));
			x.guardDown(1, new Object());
			x.quartermasterHit(1, "Rat");
			x.quartermasterDown(1, new Object());
		}
		assertEquals(4, shipped.size());
		assertEquals(shipped, fallback);
	}
}
