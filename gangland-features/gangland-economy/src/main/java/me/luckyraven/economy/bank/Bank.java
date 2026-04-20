package me.luckyraven.economy.bank;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class Bank {

	private final UUID           uuid;
	private final EconomyHandler economy;

	private String name;

	@Nullable
	private String tierId;

	private double  depositedToday;
	private double  withdrawnToday;
	@Nullable
	private Instant capResetAt;

	public Bank(UUID uuid, String name) {
		this.uuid    = uuid;
		this.name    = name;
		this.economy = new EconomyHandler(0D, null, false);
	}

	/**
	 * Rolling-window reset. {@link #capResetAt} stores the <em>future</em> UTC instant at which the current cap window
	 * will next roll over — i.e. if {@code capResetAt} is {@code null} or in the past, the deposit / withdraw counters
	 * are zeroed and the reset is scheduled {@code window} from {@code now}. Callers must invoke this immediately
	 * before reading or mutating the counters so cap enforcement uses fresh values.
	 */
	public void resetIfStale(Instant now, Duration window) {
		if (capResetAt != null && now.isBefore(capResetAt)) return;

		depositedToday = 0D;
		withdrawnToday = 0D;
		capResetAt     = now.plus(window);
	}

	public void recordDeposit(double amount) {
		depositedToday += amount;
	}

	public void recordWithdraw(double amount) {
		withdrawnToday += amount;
	}

	@Override
	public String toString() {
		return String.format("Bank{uuid=%s,name=%s,balance=%.2f,tier=%s,dep=%.2f,wd=%.2f,reset=%s}", uuid.toString(),
		                     name, economy.getBalance(), tierId, depositedToday, withdrawnToday, capResetAt);
	}

}
