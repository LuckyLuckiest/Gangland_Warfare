package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterisation of the two places that clear a player from the cops still targeting him: a plain stand-down
 * ({@code onWantedEnd}, any cause but an escape, death or arrest) clears the cops of his own group; {@code
 * removeCopAttacker} clears them in every group and only then tells the release hooks. A cop aimed at someone else,
 * or at nobody, is left as it was (target and combat-forced flag).
 */
@DisplayName("CopManager - clearing the cops that target a player")
class CopManagerClearTargetTest {

	private CopManagerFixture fx;
	private CopManager        manager;
	private Player            player;
	private UUID              playerId;
	private Player            other;
	private UUID              otherId;
	private Wanted            wanted;

	@BeforeEach
	void setUp() {
		fx = new CopManagerFixture();
		manager  = fx.manager;
		player   = fx.player(10, 10);
		playerId = player.getUniqueId();
		other    = fx.player(80, 80);
		otherId  = other.getUniqueId();
		wanted   = CopManagerFixture.wanted(2);
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	private CopNpc cop(CopGroup group, UUID target, boolean forced) {
		CopNpc cop = fx.cop(CopState.PURSUING, 0, 0);
		cop.setTargetPlayerId(target);
		cop.setCombatForced(forced);
		group.add(cop);
		return cop;
	}

	@Test
	@DisplayName("a stand-down clears the group's cops aimed at the player, and only them")
	void standDown_clearsOnlyTheCopsAimedAtThePlayer() {
		manager.onWantedStart(player, wanted);
		CopGroup group  = manager.groupFor(playerId);
		CopNpc   aimed  = cop(group, playerId, true);
		CopNpc   other1 = cop(group, otherId, true);
		CopNpc   idle   = cop(group, null, true);

		manager.onWantedEnd(player, WantedCause.UNKNOWN);

		assertNull(aimed.getTargetPlayerId());
		assertFalse(aimed.isCombatForced());
		assertEquals(otherId, other1.getTargetPlayerId());
		assertTrue(other1.isCombatForced());
		assertNull(idle.getTargetPlayerId());
		assertTrue(idle.isCombatForced());
	}

	@Test
	@DisplayName("removing a cop attacker clears the cops of every group aimed at him, then runs the release hooks")
	void removeCopAttacker_clearsEveryGroup_thenTheHooks() {
		manager.onWantedStart(player, wanted);
		manager.onWantedStart(other, wanted);
		CopGroup mine   = manager.groupFor(playerId);
		CopGroup theirs = manager.groupFor(otherId);
		CopNpc   mineAimed    = cop(mine, playerId, true);
		CopNpc   mineOther    = cop(mine, otherId, true);
		CopNpc   theirsAimed  = cop(theirs, playerId, true);
		CopNpc   theirsIdle   = cop(theirs, null, true);

		List<String> seenByHook = new ArrayList<>();
		manager.addCopAttackerReleaseHook(id -> seenByHook.add(id + ":" + mineAimed.getTargetPlayerId() + ":" +
		                                                       theirsAimed.getTargetPlayerId()));

		manager.removeCopAttacker(playerId);

		assertNull(mineAimed.getTargetPlayerId());
		assertFalse(mineAimed.isCombatForced());
		assertNull(theirsAimed.getTargetPlayerId());
		assertFalse(theirsAimed.isCombatForced());
		assertEquals(otherId, mineOther.getTargetPlayerId());
		assertTrue(mineOther.isCombatForced());
		assertNull(theirsIdle.getTargetPlayerId());
		assertTrue(theirsIdle.isCombatForced());
		assertEquals(List.of(playerId + ":null:null"), seenByHook, "the hooks run once, after the cops were cleared");
	}
}
