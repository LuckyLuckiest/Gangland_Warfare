package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.data.economy.NpcMoneyDropSource;
import org.luckyraven.gangland.item.money.MoneyDropContext;

/** STUB (red phase). */
public final class TurfDefenderMoneyDropSource implements NpcMoneyDropSource {

	private final TurfDefenderDeployer defenders;

	public TurfDefenderMoneyDropSource(TurfDefenderDeployer defenders) {
		this.defenders = defenders;
	}

	@Override
	@Nullable
	public MoneyDropContext classify(LivingEntity entity) {
		return null;
	}
}
