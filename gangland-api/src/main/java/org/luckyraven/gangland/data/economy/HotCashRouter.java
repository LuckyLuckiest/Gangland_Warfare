package org.luckyraven.gangland.data.economy;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Always-present holder for the installed {@link HotCash}. Nothing installed means today's behaviour.
 *
 * @since 2.7
 */
@CustomLog
public final class HotCashRouter {

	private volatile HotCash hotCash;
	private volatile boolean warned;

	public void install(@Nullable HotCash hotCash) {
		this.hotCash = hotCash;
	}

	public boolean isInstalled() {
		return hotCash != null;
	}

	public HotCash.Take take(Player player, BigDecimal amount, String source) {
		HotCash installed = hotCash;
		if (installed == null) {
			return HotCash.Take.WALLET;
		}

		try {
			HotCash.Take take = installed.take(player, amount, source);
			return take == null ? HotCash.Take.WALLET : take;
		} catch (RuntimeException e) {
			warnOnce(e);
			return HotCash.Take.WALLET;
		}
	}

	public boolean fence(Player player) {
		HotCash installed = hotCash;
		if (installed == null) {
			return false;
		}

		try {
			return installed.fence(player);
		} catch (RuntimeException e) {
			warnOnce(e);
			return false;
		}
	}

	private void warnOnce(RuntimeException e) {
		if (warned) {
			return;
		}
		warned = true;
		log.warn("Hot cash implementation failed; falling back to the wallet", e);
	}

}
