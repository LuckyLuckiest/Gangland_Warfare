package org.luckyraven.gangland.civilians.witness;

import org.bukkit.entity.Player;

import java.util.function.Predicate;

/**
 * Whether a player holds a Bartizan weapon. Resolves {@code BartizanApi} fresh from the ServicesManager on every
 * call, never cached; Bartizan absent means never armed.
 *
 * @since 0.20.0
 */
public final class BartizanArmed implements Predicate<Player> {

	@Override
	public boolean test(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
