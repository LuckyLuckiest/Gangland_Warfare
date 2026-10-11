package org.luckyraven.gangland.fights.fight;

import lombok.CustomLog;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.economy.exception.EconomyException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Bets: zero-sum, bank to bank. Both banks pay at accept (a failed second withdrawal gives the first back), the winner
 * takes the pot minus {@code House_Cut_Percent}, a draw or a cancel gives each stake back, and whatever would push the
 * winner's bank above {@code Account.Maximum_Balance} goes back to the loser. Open escrows are refunded on reload and
 * shutdown. Nothing here creates money except the (optional) house cut, which it destroys.
 */
@CustomLog
public class FightSettlement implements BeanLifecycle {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final IntFunction<EconomyHandler> banks;
	private final Supplier<BigDecimal>        maxBalance;
	private final IntSupplier                 houseCutPercent;
	private final Consumer<FightResult>       onSettled;
	private final Supplier<Long>              clock;
	private final Map<Long, BigDecimal>       open = new ConcurrentHashMap<>();
	private final Map<Long, Fight>            held = new ConcurrentHashMap<>();

	/** Unwired: no bank can be reached, so a bet fails closed. */
	public FightSettlement() {
		this(gangId -> null, () -> BigDecimal.ZERO, () -> 0, result -> {
		}, System::currentTimeMillis);
	}

	public FightSettlement(IntFunction<EconomyHandler> banks, Supplier<BigDecimal> maxBalance,
	                       IntSupplier houseCutPercent, Consumer<FightResult> onSettled, Supplier<Long> clock) {
		this.banks           = banks;
		this.maxBalance      = maxBalance;
		this.houseCutPercent = houseCutPercent;
		this.onSettled       = onSettled;
		this.clock           = clock;
	}

	/** Takes the stake from both banks. Nothing is kept when either side cannot pay. */
	public EscrowResult escrow(Fight fight) {
		BigDecimal stake = BigDecimal.valueOf(fight.bet());
		if (stake.signum() <= 0) return EscrowResult.success();
		if (open.containsKey(fight.id())) return EscrowResult.success();

		EconomyHandler bankA = banks.apply(fight.gangA());
		EconomyHandler bankB = banks.apply(fight.gangB());
		if (bankA == null || bankB == null) return EscrowResult.fail("Economy.Unavailable");

		try {
			bankA.withdrawAmount(stake);
		} catch (EconomyException exception) {
			return EscrowResult.fail("Economy.Cannot_Afford_A");
		}
		try {
			bankB.withdrawAmount(stake);
		} catch (EconomyException exception) {
			bankA.depositAmount(stake);
			return EscrowResult.fail("Economy.Cannot_Afford_B");
		}

		open.put(fight.id(), stake);
		held.put(fight.id(), fight);
		return EscrowResult.success();
	}

	/** Pays out the escrow (if any) and reports the result once. */
	public void settle(Fight fight, Outcome outcome) {
		BigDecimal stake = open.remove(fight.id());
		held.remove(fight.id());

		int winner = outcome == Outcome.WIN_A ? fight.gangA() : outcome == Outcome.WIN_B ? fight.gangB() : -1;
		int loser  = outcome == Outcome.WIN_A ? fight.gangB() : outcome == Outcome.WIN_B ? fight.gangA() : -1;

		if (stake != null) {
			if (winner < 0) {
				give(fight.gangA(), stake);
				give(fight.gangB(), stake);
			} else {
				pay(winner, loser, stake);
			}
		}

		onSettled.accept(new FightResult(fight, winner, loser, clock.get()));
	}

	/** Gives both stakes back without a result (cancelled, never started, aborted). */
	public void refund(Fight fight) {
		BigDecimal stake = open.remove(fight.id());
		held.remove(fight.id());
		if (stake == null) return;

		give(fight.gangA(), stake);
		give(fight.gangB(), stake);
	}

	/** Refunds every open escrow (module disable and reload). */
	public void abortAll() {
		for (Fight fight : new ArrayList<>(held.values())) {
			refund(fight);
		}
	}

	public boolean holds(Fight fight) {
		return open.containsKey(fight.id());
	}

	@Override
	public void onClear() {
		abortAll();
	}

	@Override
	public void onShutdown() {
		abortAll();
	}

	private void pay(int winner, int loser, BigDecimal stake) {
		BigDecimal pot    = stake.multiply(BigDecimal.valueOf(2));
		BigDecimal cut    = pot.multiply(BigDecimal.valueOf(houseCutPercent.getAsInt())).divide(HUNDRED, 2, RoundingMode.DOWN);
		BigDecimal payout = pot.subtract(cut);

		EconomyHandler winnerBank = banks.apply(winner);
		if (winnerBank == null) {
			// the winner is gone (gang deleted): the loser keeps nothing it can lose twice, so give the loser its own stake
			log.warn("Fight winner gang {} has no bank; refunding the loser's stake", winner);
			give(loser, stake);
			return;
		}

		BigDecimal room = maxBalance.get().subtract(winnerBank.getAmount()).max(BigDecimal.ZERO);
		BigDecimal paid = payout.min(room);
		if (paid.signum() > 0) winnerBank.depositAmount(paid);

		BigDecimal overflow = payout.subtract(paid);
		if (overflow.signum() > 0) give(loser, overflow);
	}

	private void give(int gangId, BigDecimal amount) {
		EconomyHandler bank = banks.apply(gangId);
		if (bank == null) {
			log.warn("Gang {} has no bank; {} could not be returned", gangId, amount.toPlainString());
			return;
		}
		bank.depositAmount(amount);
	}
}
