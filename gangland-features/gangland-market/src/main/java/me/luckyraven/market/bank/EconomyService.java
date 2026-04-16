package me.luckyraven.market.bank;

import me.luckyraven.market.event.events.MarketTransactionEvent;
import me.luckyraven.market.ledger.TransactionContext;
import me.luckyraven.market.ledger.TransactionLedger;
import me.luckyraven.market.ledger.TransactionRecord;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Canonical entry point for every money mutation that should be audited. Wraps {@link EconomyHandler} with a ledger
 * write and a main-thread {@link MarketTransactionEvent}. Expected to be called from the main thread (trader listeners,
 * command handlers). Runs the ledger append synchronously on the caller thread — repositories are HikariCP-backed so
 * this is cheap; if it ever becomes a hotspot the write can be moved to a queue.
 */
public final class EconomyService {

	private final JavaPlugin        plugin;
	private final TransactionLedger ledger;

	public EconomyService(JavaPlugin plugin, TransactionLedger ledger) {
		this.plugin = plugin;
		this.ledger = ledger;
	}

	/**
	 * Debits {@code amount} from the handler and records a BUY/SELL/etc. row.
	 */
	public TransactionRecord charge(EconomyHandler handler, double amount, TransactionContext ctx)
			throws EconomyException {
		handler.withdraw(amount);
		return record(ctx);
	}

	/**
	 * Credits {@code amount} to the handler and records the row.
	 */
	public TransactionRecord credit(EconomyHandler handler, double amount, TransactionContext ctx) {
		handler.deposit(amount);
		return record(ctx);
	}

	/**
	 * Records a zero-balance-impact transaction (barter in/out, tip-only side, admin audit).
	 */
	public TransactionRecord record(TransactionContext ctx) {
		TransactionRecord record = ledger.append(ctx);
		dispatchEvent(record);
		return record;
	}

	public TransactionLedger getLedger() {
		return ledger;
	}

	private void dispatchEvent(@Nullable TransactionRecord record) {
		if (record == null) {
			return;
		}

		// If we're already on the main thread, fire directly; otherwise bounce.
		if (Bukkit.isPrimaryThread()) {
			Bukkit.getPluginManager().callEvent(new MarketTransactionEvent(record));
			return;
		}

		Bukkit.getScheduler().runTask(plugin,
		                              () -> Bukkit.getPluginManager().callEvent(new MarketTransactionEvent(record)));
	}
}
