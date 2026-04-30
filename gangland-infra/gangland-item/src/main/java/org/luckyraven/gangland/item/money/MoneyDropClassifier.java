package org.luckyraven.gangland.item.money;

import org.bukkit.entity.LivingEntity;

/**
 * Contract used by {@code MoneyDropListener} to classify the source of a death event without importing cops-n-crooks
 * classes (which gangland-item cannot see). The implementation in gangland-impl uses {@code CopService} and
 * {@code CivilianService} to recognise NPC entities.
 */
public interface MoneyDropClassifier {

	/**
	 * Classifies the dead entity into a {@link MoneyDropContext}. Implementations should return
	 * {@link MoneyDropContext#PLAYER} for real players, {@link MoneyDropContext#COP} or
	 * {@link MoneyDropContext#CIVILIAN} for the corresponding NPC types, and {@link MoneyDropContext#MOB} for
	 * everything else.
	 */
	MoneyDropContext classify(LivingEntity entity);

}
