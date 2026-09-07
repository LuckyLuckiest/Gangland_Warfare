package org.luckyraven.gangland.data.economy;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.item.money.MoneyDropContext;

/**
 * Seam 1: cop/civilian NPC recognition for cash drops. {@link GanglandMoneyDropClassifier} is the always-present
 * holder bean; the cops-n-crooks module installs a delegate implementing this interface from its
 * {@code @PostConstruct} once it is loaded. See documentation/module-loader.md, "Core seams".
 */
public interface NpcMoneyDropSource {

	/**
	 * @return the NPC classification for {@code entity}, or {@code null} when it is not a managed NPC.
	 */
	@Nullable
	MoneyDropContext classify(LivingEntity entity);
}
