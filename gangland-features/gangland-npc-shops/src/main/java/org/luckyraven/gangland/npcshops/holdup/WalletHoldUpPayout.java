package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.npcshops.trader.economy.TraderEconomyContract;

import java.math.BigDecimal;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class WalletHoldUpPayout implements HoldUpPayout {

	private final TraderEconomyContract    economy;
	private final Supplier<HoldUpSettings> settings;
	private final DoubleSupplier           random;

	public WalletHoldUpPayout(TraderEconomyContract economy, Supplier<HoldUpSettings> settings,
	                          DoubleSupplier random) {
		this.economy  = economy;
		this.settings = settings;
		this.random   = random;
	}

	@Override
	public BigDecimal pay(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

}
