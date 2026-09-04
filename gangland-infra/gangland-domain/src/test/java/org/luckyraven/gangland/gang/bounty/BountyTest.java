package org.luckyraven.gangland.gang.bounty;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.economy.Currency;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Proves {@link Bounty}'s scaling maths and ledger bookkeeping (Test Surface, wanted-bounty-combat.md:
 * "Bounty.calculateLevelScaledBounty, getAutoBountyIncrease, addBounty/removeBounty/resetBounty/size
 * - especially the ledger-vs-total divergence after a direct setAmount (issue #13) and the round-trip
 * set-&gt;clear profit (issue #1)" and "Bounty.hasBounty() with a negative amount (issue #3)").
 *
 * <p><b>Unverified — module ownership.</b> {@code gangland-infra/gangland-domain} is owned by a
 * different agent for Maven runs in this initiative; this suite could not be compiled or executed
 * and is reported as an unverified draft.
 *
 * <p>Every test here avoids {@code createTimer}, so {@code repeatingTimer} stays {@code null} and
 * {@code resetBounty}/{@code stopTimer} exercise their null-safe no-op path without needing a
 * {@code JavaPlugin} or Bukkit scheduler.
 */
@DisplayName("Bounty - level-scaled bounty maths and ledger bookkeeping")
class BountyTest {

	@Test
	@DisplayName("a fresh Bounty starts at zero with an empty ledger")
	void constructor_startsAtZeroWithEmptyLedger() {
		Bounty bounty = bounty(100, 2.0);

		assertEquals(Currency.ZERO, bounty.getAmount());
		assertEquals(0, bounty.size());
		assertFalse(bounty.hasBounty());
	}

	@Test
	@DisplayName("calculateLevelScaledBounty applies 1 + userLevel * levelMultiplier / 10 as the scale factor")
	void calculateLevelScaledBounty_appliesLevelScaleFormula() {
		Bounty bounty = bounty(100, 2.0);

		// level 5, multiplier 2.0 -> factor = 1 + 5*2/10 = 2.0 -> 100 * 2.0 = 200.00
		BigDecimal scaled = bounty.calculateLevelScaledBounty(Currency.of(100), 5);

		assertEquals(Currency.of(200), scaled);
	}

	@Test
	@DisplayName("calculateLevelScaledBounty at level 0 is a pure pass-through (factor == 1)")
	void calculateLevelScaledBounty_levelZero_isIdentity() {
		Bounty bounty = bounty(100, 2.0);

		assertEquals(Currency.of(50), bounty.calculateLevelScaledBounty(Currency.of(50), 0));
	}

	@Test
	@DisplayName("getAutoBountyIncrease multiplies baseAmount by wantedLevel, then applies the level scale")
	void getAutoBountyIncrease_multipliesBaseByWantedLevelThenScalesByUserLevel() {
		Bounty bounty = bounty(10, 2.0);

		// baseBounty = 10 * wantedLevel(3) = 30.00; scale factor at userLevel 5, multiplier 2.0 -> 2.0 -> 60.00
		BigDecimal increase = bounty.getAutoBountyIncrease(5, 3);

		assertEquals(Currency.of(60), increase);
	}

	@Test
	@DisplayName("addBounty(sender, amount, userLevel) records the SAME scaled figure in both the ledger and the total")
	void addBounty_threeArg_recordsScaledFigureConsistentlyInLedgerAndTotal() {
		Bounty bounty = bounty(100, 2.0);
		CommandSender sender = mock(CommandSender.class);

		// level 5, multiplier 2.0 -> factor 2.0 -> stored/added figure is 100 * 2.0 = 200.00 in BOTH places.
		bounty.addBounty(sender, Currency.of(100), 5);

		assertEquals(Currency.of(200), bounty.getAmount());
		assertEquals(Currency.of(200), bounty.getSetAmount(sender),
		             "Bounty.addBounty itself keeps the ledger and total consistent - both hold the scaled figure. "
		             + "The set-then-clear profit in Observation #1 (wanted-bounty-combat.md) is a caller-level bug: "
		             + "BountySetCommand (gangland-impl) withdraws the RAW amount from the sender but calls this "
		             + "3-arg overload, which scales for storage - the mismatch is between what the command withdraws "
		             + "and what this method records, not a defect inside Bounty itself.");
	}

	@Test
	@DisplayName("addBounty accumulates across multiple contributions from the same sender")
	void addBounty_accumulatesAcrossMultipleCalls() {
		Bounty bounty = bounty(0, 0.0); // multiplier 0 -> scale factor is always 1, isolates accumulation
		CommandSender sender = mock(CommandSender.class);

		bounty.addBounty(sender, Currency.of(50), 0);
		bounty.addBounty(sender, Currency.of(25), 0);

		assertEquals(Currency.of(75), bounty.getAmount());
		assertEquals(Currency.of(75), bounty.getSetAmount(sender));
	}

	@Test
	@DisplayName("Observation #13 (wanted-bounty-combat.md): a direct setAmount (the Lombok @Data setter, used by "
	             + "EntityDamageListener/BountyExecutor) bypasses the ledger entirely - the total moves, the ledger "
	             + "does not")
	void setAmount_bypassesLedger_pinsObservation13() {
		Bounty bounty = bounty(100, 2.0);
		CommandSender sender = mock(CommandSender.class);
		bounty.addBounty(sender, Currency.of(10), 0);

		bounty.setAmount(Currency.of(9999));

		assertEquals(Currency.of(9999), bounty.getAmount());
		assertEquals(1, bounty.size(), "the ledger entry from addBounty is untouched by a direct setAmount");
		assertEquals(Currency.of(10), bounty.getSetAmount(sender));
	}

	@Test
	@DisplayName("Observation #3 (wanted-bounty-combat.md): hasBounty() is signum() != 0, so a negative amount "
	             + "still reports true")
	void hasBounty_negativeAmount_stillReportsTrue_pinsObservation3() {
		Bounty bounty = bounty(100, 2.0);

		bounty.setAmount(Currency.of(-50));

		assertTrue(bounty.hasBounty(), "signum() != 0 is true for negative values too - this is the pinned defect");
	}

	@Test
	@DisplayName("removeBounty subtracts the sender's ledgered contribution and floors the total at zero")
	void removeBounty_subtractsAndFloorsAtZero() {
		Bounty bounty = bounty(0, 0.0);
		CommandSender sender = mock(CommandSender.class);
		bounty.addBounty(sender, Currency.of(30), 0);

		bounty.removeBounty(sender);

		assertEquals(Currency.ZERO, bounty.getAmount());
		assertFalse(bounty.containsBounty(sender));
	}

	@Test
	@DisplayName("removeBounty floors at zero even when the total was reduced by other means first")
	void removeBounty_floorsAtZero_whenTotalAlreadyBelowLedgerEntry() {
		Bounty bounty = bounty(0, 0.0);
		CommandSender sender = mock(CommandSender.class);
		bounty.addBounty(sender, Currency.of(30), 0);
		bounty.setAmount(Currency.of(5)); // total dropped below the ledgered contribution

		bounty.removeBounty(sender);

		assertEquals(Currency.ZERO, bounty.getAmount(), "subtracting 30 from 5 must floor at zero, not go negative");
	}

	@Test
	@DisplayName("removeBounty for a sender with no ledger entry is a no-op")
	void removeBounty_unknownSender_isNoop() {
		Bounty bounty = bounty(0, 0.0);
		CommandSender contributor = mock(CommandSender.class);
		CommandSender stranger = mock(CommandSender.class);
		bounty.addBounty(contributor, Currency.of(30), 0);

		bounty.removeBounty(stranger);

		assertEquals(Currency.of(30), bounty.getAmount());
	}

	@Test
	@DisplayName("resetBounty zeroes the total, clears the ledger, and leaves a null timer alone (no NPE)")
	void resetBounty_zeroesTotalAndClearsLedger() {
		Bounty bounty = bounty(0, 0.0);
		CommandSender sender = mock(CommandSender.class);
		bounty.addBounty(sender, Currency.of(30), 0);

		assertDoesNotThrow(bounty::resetBounty);

		assertEquals(Currency.ZERO, bounty.getAmount());
		assertEquals(0, bounty.size());
		assertFalse(bounty.hasBounty());
	}

	private static Bounty bounty(double baseAmount, double levelMultiplier) {
		return new Bounty(Currency.of(baseAmount), levelMultiplier);
	}

}
