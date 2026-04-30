package org.luckyraven.gangland.economy.bank;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.economy.Currency;
import org.luckyraven.gangland.economy.EconomyHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Bank {

	private static final double MILLIS_PER_DAY = 86_400_000D;

	private final UUID           uuid;
	private final EconomyHandler economy;

	private String name;

	@Nullable
	private String tierId;

	private double  depositedToday;
	@Nullable
	private Instant capResetAt;

	@Nullable
	private Instant lastInterestAt;

	@Nullable
	private Instant lastWeeklyLoanAt;
	@Nullable
	private Instant lastMonthlyLoanAt;

	public Bank(UUID uuid, String name) {
		this.uuid    = uuid;
		this.name    = name;
		this.economy = new EconomyHandler(Currency.ZERO, null, false);
	}

	/**
	 * Rolling-window reset. {@link #capResetAt} stores the <em>future</em> UTC instant at which the current deposit cap
	 * window will next roll over — i.e. if {@code capResetAt} is {@code null} or in the past, the deposit counter is
	 * zeroed and the reset is scheduled {@code window} from {@code now}. Callers must invoke this immediately before
	 * reading or mutating the counter so cap enforcement uses fresh values.
	 */
	public void resetIfStale(Instant now, Duration window) {
		if (capResetAt != null && now.isBefore(capResetAt)) return;

		depositedToday = 0D;
		capResetAt     = now.plus(window);
	}

	/**
	 * Accrues continuous interest since the last interaction. Interest equals
	 * {@code balance * ratePerDay * elapsedDays}, credited to the bank via the economy handler. The post-credit balance
	 * is clamped at {@code cap}, so at-cap accounts stop growing. Returns the actually accrued amount (after clamping)
	 * so callers can display / log it.
	 *
	 * <p>First call (or first call after resetcap) just anchors {@link #lastInterestAt} without crediting —
	 * there's no elapsed window to attribute interest to.
	 */
	public double accrueInterest(Instant now, double ratePerDay, double cap) {
		if (lastInterestAt == null) {
			lastInterestAt = now;
			return 0D;
		}
		if (ratePerDay <= 0D) {
			lastInterestAt = now;
			return 0D;
		}

		long elapsedMs = Duration.between(lastInterestAt, now).toMillis();
		if (elapsedMs <= 0) {
			return 0D;
		}

		double elapsedDays = elapsedMs / MILLIS_PER_DAY;
		double balance     = economy.getAmount().doubleValue();
		double interest    = balance * ratePerDay * elapsedDays;

		if (interest <= 0D) {
			lastInterestAt = now;
			return 0D;
		}

		double actual = interest;
		if (cap > 0 && balance + interest > cap) {
			actual = Math.max(0D, cap - balance);
		}

		if (actual > 0D) economy.depositAmount(Currency.of(actual));
		lastInterestAt = now;
		return actual;
	}

	public void recordDeposit(double amount) {
		depositedToday += amount;
	}

	@Override
	public String toString() {
		return String.format("Bank{uuid=%s,name=%s,balance=%s,tier=%s,dep=%.2f,reset=%s,lastInterest=%s}",
		                     uuid.toString(), name, economy.getAmount().toPlainString(), tierId, depositedToday,
		                     capResetAt, lastInterestAt);
	}

}
