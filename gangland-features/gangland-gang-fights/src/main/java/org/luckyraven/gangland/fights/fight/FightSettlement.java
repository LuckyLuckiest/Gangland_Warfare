package org.luckyraven.gangland.fights.fight;

/**
 * Bets: takes the stakes, pays the winner, refunds. Stub until the economy lane (W6-E); escrow fails closed so a
 * bet can never be accepted while nothing holds the money.
 */
public class FightSettlement {

	public EscrowResult escrow(Fight fight) {
		return EscrowResult.fail("Economy.Unavailable");
	}

	public void settle(Fight fight, Outcome outcome) {
	}

	public void refund(Fight fight) {
	}

	public void abortAll() {
	}
}
