package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcSupport;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * 0.20 seam: {@link CopManager#addCopResolver} lets a lane (patrol) hand the manager cops that belong to no
 * {@link CopGroup}. The group scan wins; resolvers answer in registration order; a throwing resolver is skipped.
 */
@DisplayName("CopManager - cop resolvers for groupless (beat) cops")
class CopManagerResolverTest {

	private CopManagerFixture        fx;
	private MockedStatic<NpcSupport> npcs;
	private CopNpc                   beat;
	private Entity                   beatBody;

	@BeforeEach
	void setUp() {
		fx   = new CopManagerFixture();
		npcs = mockStatic(NpcSupport.class);
		npcs.when(NpcSupport::registry).thenReturn(Optional.empty());
		beat     = fx.cop(CopState.IDLE, 5, 5);
		beatBody = beat.getEntity();
	}

	@AfterEach
	void tearDown() {
		npcs.close();
		fx.close();
	}

	@Test
	@DisplayName("with no resolver a groupless cop is unknown (today's behaviour)")
	void noResolver_grouplessCopUnknown() {
		assertFalse(fx.manager.isCopNpc(beatBody));
		assertNull(fx.manager.findCopByEntity(beatBody));
		assertNull(fx.manager.findDyingCop(beatBody));
	}

	@Test
	@DisplayName("a resolver finds the groupless cop for isCopNpc, findCopByEntity and findDyingCop")
	void resolverFindsGrouplessCop() {
		fx.manager.addCopResolver(entity -> entity == beatBody ? beat : null);

		assertTrue(fx.manager.isCopNpc(beatBody));
		assertSame(beat, fx.manager.findCopByEntity(beatBody));
		assertSame(beat, fx.manager.findDyingCop(beatBody));
	}

	@Test
	@DisplayName("a resolver answering null for an entity leaves it unknown")
	void resolverNull_entityUnknown() {
		fx.manager.addCopResolver(entity -> null);

		assertFalse(fx.manager.isCopNpc(beatBody));
		assertNull(fx.manager.findCopByEntity(beatBody));
		assertNull(fx.manager.findDyingCop(beatBody));
	}

	@Test
	@DisplayName("a cop in a group wins over a resolver that would answer another cop, and the resolver is not asked")
	void groupCopWinsOverResolver() {
		Player player = fx.player(0, 0);
		fx.manager.onWantedStart(player, CopManagerFixture.wanted(2));
		CopNpc grouped = fx.cop(CopState.PURSUING, 1, 1);
		fx.manager.groupFor(player.getUniqueId()).add(grouped);
		AtomicInteger asked = new AtomicInteger();
		fx.manager.addCopResolver(entity -> {
			asked.incrementAndGet();
			return beat;
		});

		assertSame(grouped, fx.manager.findCopByEntity(grouped.getEntity()));
		assertTrue(fx.manager.isCopNpc(grouped.getEntity()));
		assertSame(grouped, fx.manager.findDyingCop(grouped.getEntity()));
		assertEquals(0, asked.get());
	}

	@Test
	@DisplayName("resolvers answer in registration order, the first non-null wins")
	void firstNonNullResolverWins() {
		CopNpc other = fx.cop(CopState.IDLE, 9, 9);
		fx.manager.addCopResolver(entity -> null);
		fx.manager.addCopResolver(entity -> beat);
		fx.manager.addCopResolver(entity -> other);

		assertSame(beat, fx.manager.findCopByEntity(beatBody));
	}

	@Test
	@DisplayName("a throwing resolver is skipped and the next one answers")
	void throwingResolverSkipped() {
		fx.manager.addCopResolver(entity -> {
			throw new IllegalStateException("boom");
		});
		fx.manager.addCopResolver(entity -> beat);

		assertTrue(fx.manager.isCopNpc(beatBody));
		assertSame(beat, fx.manager.findCopByEntity(beatBody));
		assertSame(beat, fx.manager.findDyingCop(beatBody));
	}

	@Test
	@DisplayName("a hit on a resolved beat cop reaches the attacked hooks and forces it into COMBAT (no group)")
	void resolvedBeatCopHit_reachesAttackedHooks() {
		Player attacker = fx.player(0, 0);
		AtomicReference<CopNpc> hooked = new AtomicReference<>();
		fx.manager.addCopAttackedHook((cop, who) -> hooked.set(cop));
		fx.manager.addCopResolver(entity -> entity == beatBody ? beat : null);

		CopNpc found = fx.manager.findCopByEntity(beatBody);
		fx.manager.onCopAttackedAlert(found, attacker);

		assertSame(beat, hooked.get());
		verify(beat).transitionTo(CopState.COMBAT);
	}

	@Test
	@DisplayName("a null resolver is a programming error rejected at registration")
	void nullResolverRejected() {
		assertThrows(NullPointerException.class, () -> fx.manager.addCopResolver(null));
	}
}
