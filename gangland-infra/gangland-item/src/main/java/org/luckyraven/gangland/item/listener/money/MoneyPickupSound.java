package org.luckyraven.gangland.item.listener.money;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyItem;

/**
 * The pickup sound played after a successful cash deposit, shared by {@link MoneyPickupListener},
 * {@link MoneyInteractListener} and {@link MoneyProximityPickupTask}. Package-private on purpose: the helper is not
 * part of the module API surface.
 */
final class MoneyPickupSound {

	private MoneyPickupSound() {
	}

	static void play(MoneyAddon moneyAddon, Player player, String variationId) {
		if (variationId == null) return;

		MoneyItem variation = moneyAddon.getVariation(variationId);
		if (variation == null) return;

		MoneyItem.PickupSound sound = variation.getPickupSound();
		if (sound == null) return;

		sound.soundConfig().playSound(player);
	}

}
