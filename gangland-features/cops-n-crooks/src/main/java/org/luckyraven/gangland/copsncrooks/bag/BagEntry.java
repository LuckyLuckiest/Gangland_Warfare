package org.luckyraven.gangland.copsncrooks.bag;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One player's bag row: the cash held, what this chase already bagged, the daily bank allowance used and the last
 * seizure. Entries are zeroed, never removed.
 *
 * @since 0.20.0
 */
public final class BagEntry {

	private final UUID player;

	private BigDecimal amount;
	private BigDecimal chaseTaken;
	private long       bankedDay;
	private BigDecimal bankedToday;
	private BigDecimal seized;

	public BagEntry(UUID player, BigDecimal amount, BigDecimal chaseTaken, long bankedDay, BigDecimal bankedToday,
	                BigDecimal seized) {
		this.player      = player;
		this.amount      = amount;
		this.chaseTaken  = chaseTaken;
		this.bankedDay   = bankedDay;
		this.bankedToday = bankedToday;
		this.seized      = seized;
	}

	public UUID player() {
		return player;
	}

	public BigDecimal amount() {
		return amount;
	}

	public BigDecimal chaseTaken() {
		return chaseTaken;
	}

	public long bankedDay() {
		return bankedDay;
	}

	public BigDecimal bankedToday() {
		return bankedToday;
	}

	public BigDecimal seized() {
		return seized;
	}

	void amount(BigDecimal value) {
		this.amount = value;
	}

	void chaseTaken(BigDecimal value) {
		this.chaseTaken = value;
	}

	void banked(long day, BigDecimal value) {
		this.bankedDay   = day;
		this.bankedToday = value;
	}

	void seized(BigDecimal value) {
		this.seized = value;
	}
}
