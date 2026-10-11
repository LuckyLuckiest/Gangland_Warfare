package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.economy.GanglandMoneyDropClassifier;
import org.luckyraven.gangland.item.money.MoneyDropContext;
import org.luckyraven.keystone.npc.NpcSupport;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** T-700: a turf guard is a CivilianNpc, so the cops source filed it as CIVILIAN and every kill minted cash. */
@DisplayName("TurfDefenderMoneyDropSource")
class TurfDefenderMoneyDropSourceTest {

	@Test
	@DisplayName("trackedGuard_isNpc_notCivilian: through the classifier a guard is NPC though the cops slot says CIVILIAN")
	void trackedGuard_isNpc_notCivilian() {
		LivingEntity guard = mock(LivingEntity.class);
		TurfDefenderDeployer deployer = mock(TurfDefenderDeployer.class);
		when(deployer.findOwningTurfId(guard)).thenReturn(7);
		GanglandMoneyDropClassifier classifier = new GanglandMoneyDropClassifier();
		classifier.install(e -> MoneyDropContext.CIVILIAN); // CopsMoneyDropSource: every registered civilian
		classifier.installFirst(new TurfDefenderMoneyDropSource(deployer));

		try (MockedStatic<NpcSupport> npcs = mockStatic(NpcSupport.class)) {
			npcs.when(() -> NpcSupport.isNpc(any())).thenReturn(true);
			assertEquals(MoneyDropContext.NPC, classifier.classify(guard));
		}
	}

	@Test
	@DisplayName("untracked_null: anything the deployer does not track falls through to the next source")
	void untracked_null() {
		TurfDefenderDeployer deployer = mock(TurfDefenderDeployer.class);
		LivingEntity other = mock(LivingEntity.class);
		when(deployer.findOwningTurfId(other)).thenReturn(-1);

		assertNull(new TurfDefenderMoneyDropSource(deployer).classify(other));
	}
}
