package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.data.economy.NpcMoneyDropSource;
import org.luckyraven.gangland.item.money.MoneyDropContext;

/**
 * T-700: a turf guard is a civilian NPC, so cops' source filed it as CIVILIAN and every kill minted
 * {@code money.yml} civilian cash. Installed with {@code GanglandMoneyDropClassifier.installFirst}, this source
 * answers {@link MoneyDropContext#NPC} for a tracked guard ({@code money.yml} ships no NPC drop, so nothing is minted)
 * and stays silent for everything else. The rival bounty is paid by {@code TurfDefenderDeathListener} out of what the
 * owner already spent.
 */
public final class TurfDefenderMoneyDropSource implements NpcMoneyDropSource {

	private final TurfDefenderDeployer defenders;

	public TurfDefenderMoneyDropSource(TurfDefenderDeployer defenders) {
		this.defenders = defenders;
	}

	@Override
	@Nullable
	public MoneyDropContext classify(LivingEntity entity) {
		return defenders.findOwningTurfId(entity) >= 0 ? MoneyDropContext.NPC : null;
	}
}
