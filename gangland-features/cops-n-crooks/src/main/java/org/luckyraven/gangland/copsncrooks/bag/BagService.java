package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.data.economy.HotCash;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The hot-cash bag: takes cash while wanted, and moves the bag exactly once on every ending (bank, convert, seize,
 * spill), the fence and a bribe.
 *
 * @since 0.20.0
 */
public final class BagService implements HotCash, BagFunding {

	public BagService(HotCashBags bags, Supplier<BagSettings> settings, WantedMessages messages, BagEnv env) {
	}

	@Override
	public Take take(Player player, BigDecimal amount, String source) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public boolean fence(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** A wanted state ended with {@code cause}: pays the bag once, per D-W2-07. */
	public void end(Player player, WantedCause cause) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Takes the whole bag for the police ({@code seized} on the row); returns the amount (0 when empty). */
	public double seize(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public BigDecimal available(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void debit(Player player, BigDecimal amount) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void announceBribe(Player player, BigDecimal fromBag, BigDecimal fromWallet) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The paperwork line for a seized bag, or null when nothing was seized. */
	public String paperworkLine(UUID player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Join settle (after the star restore): a saved bag with stars is restored, without stars converted. */
	public void settle(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Quit: spill / seize / keep per D-W2-08, D-W2-31 and the shutdown flag. */
	public void onQuit(Player player) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Marks the service as shutting down (quits no longer spill) and saves every row. */
	public void shutdown() {
		throw new UnsupportedOperationException("TDD stub");
	}

	public boolean isShuttingDown() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Sends the HUD to every online player holding a bag (the repeating task body). */
	public void refreshHud(Iterable<? extends Player> online) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
