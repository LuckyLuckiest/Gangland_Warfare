package org.luckyraven.gangland.data.economy;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Always-present holder for the installed {@link HotCash}. Nothing installed means today's behaviour.
 *
 * @since 2.7
 */
public final class HotCashRouter {

	public void install(@Nullable HotCash hotCash) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public boolean isInstalled() {
		throw new UnsupportedOperationException("TDD stub");
	}

	public HotCash.Take take(Player player, BigDecimal amount, String source) {
		throw new UnsupportedOperationException("TDD stub");
	}

	public boolean fence(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

}
