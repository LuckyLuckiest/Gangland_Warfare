package org.luckyraven.gangland.data.economy;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.item.money.MoneyDropContext;
import org.luckyraven.keystone.npc.NpcSupport;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * B1 (phase H13): every Citizens PLAYER-type NPC death is a {@code PlayerDeathEvent} whose entity is a
 * {@link Player}. Classifying on {@code instanceof Player} first sent cops and civilians down the player path (debit
 * a User they do not have, drop nothing). The module source is consulted first, PLAYER is kept for real players, and
 * an NPC nobody recognises gets {@link MoneyDropContext#NPC}, which drops nothing by default.
 */
@DisplayName("GanglandMoneyDropClassifier - NPC bodies are never players")
class GanglandMoneyDropClassifierTest {

	private MockedStatic<NpcSupport>   npcs;
	private GanglandMoneyDropClassifier classifier;

	@BeforeEach
	void setUp() {
		npcs       = mockStatic(NpcSupport.class);
		classifier = new GanglandMoneyDropClassifier();
	}

	@AfterEach
	void tearDown() {
		npcs.close();
	}

	private Player npcPlayer() {
		Player body = mock(Player.class);
		npcs.when(() -> NpcSupport.isNpc(body)).thenReturn(true);
		return body;
	}

	@Test
	@DisplayName("a PLAYER-type NPC the module recognises gets the module's context")
	void recognisedPlayerTypeNpc_moduleContext() {
		Player cop = npcPlayer();
		classifier.install(entity -> entity == cop ? MoneyDropContext.COP : null);

		assertEquals(MoneyDropContext.COP, classifier.classify(cop));
	}

	@Test
	@DisplayName("a VILLAGER civilian the module recognises still gets CIVILIAN")
	void recognisedVillager_civilian() {
		Villager civilian = mock(Villager.class);
		npcs.when(() -> NpcSupport.isNpc(civilian)).thenReturn(true);
		classifier.install(entity -> entity == civilian ? MoneyDropContext.CIVILIAN : null);

		assertEquals(MoneyDropContext.CIVILIAN, classifier.classify(civilian));
	}

	@Test
	@DisplayName("an unrecognised PLAYER-type NPC (trader, banker, foreign plugin) is NPC, not PLAYER")
	void unrecognisedPlayerTypeNpc_npc() {
		classifier.install(entity -> null);

		assertEquals(MoneyDropContext.NPC, classifier.classify(npcPlayer()));
	}

	@Test
	@DisplayName("with no module installed an NPC is still not a player")
	void noModule_npc() {
		assertEquals(MoneyDropContext.NPC, classifier.classify(npcPlayer()));
	}

	@Test
	@DisplayName("a real player is PLAYER")
	void realPlayer_player() {
		classifier.install(entity -> null);

		assertEquals(MoneyDropContext.PLAYER, classifier.classify(mock(Player.class)));
	}

	@Test
	@DisplayName("a vanilla mob is MOB")
	void vanillaMob_mob() {
		assertEquals(MoneyDropContext.MOB, classifier.classify(mock(LivingEntity.class)));
	}
}
