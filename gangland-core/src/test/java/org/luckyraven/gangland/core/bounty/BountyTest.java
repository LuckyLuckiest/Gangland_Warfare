package org.luckyraven.gangland.core.bounty;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.economy.Currency;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
	@DisplayName("WB-01: the paid ledger records what the contributor was CHARGED, not the level-scaled figure "
	             + "posted on the target's head")
	void getPaidAmount_recordsTheChargedAmount_notTheScaledFigure() {
		Bounty        bounty = bounty(100, 2.0);
		CommandSender sender = mock(CommandSender.class);

		// level 5, multiplier 2.0 -> factor 2.0: the sender pays 100, the target carries 200.
		bounty.addBounty(sender, Currency.of(100), 5);

		assertEquals(Currency.of(200), bounty.getSetAmount(sender), "the posted figure is level-scaled");
		assertEquals(Currency.of(100), bounty.getPaidAmount(sender),
		             "Observation #1 (wanted-bounty-combat.md): /glw bounty clear refunds this figure. Refunding "
		             + "the posted one paid back double what BountySetCommand withdrew - free money on every "
		             + "set-then-clear round trip.");
	}

	@Test
	@DisplayName("WB-01: paid contributions accumulate independently of the scaled total")
	void getPaidAmount_accumulatesAcrossContributions() {
		Bounty        bounty = bounty(100, 2.0);
		CommandSender sender = mock(CommandSender.class);

		bounty.addBounty(sender, Currency.of(100), 5);
		bounty.addBounty(sender, Currency.of(50), 5);

		assertEquals(Currency.of(300), bounty.getSetAmount(sender));
		assertEquals(Currency.of(150), bounty.getPaidAmount(sender));
	}

	@Test
	@DisplayName("WB-01: the two-argument addBounty charges exactly what it posts")
	void getPaidAmount_twoArgOverload_paidEqualsPosted() {
		Bounty        bounty = bounty(100, 2.0);
		CommandSender sender = mock(CommandSender.class);

		bounty.addBounty(sender, Currency.of(75));

		assertEquals(Currency.of(75), bounty.getSetAmount(sender));
		assertEquals(Currency.of(75), bounty.getPaidAmount(sender));
	}

	@Test
	@DisplayName("WB-01: removeBounty and resetBounty drop the paid entry along with the posted one")
	void paidLedger_isClearedAlongsideThePostedLedger() {
		Bounty        bounty = bounty(100, 2.0);
		CommandSender sender = mock(CommandSender.class);

		bounty.addBounty(sender, Currency.of(100), 5);
		bounty.removeBounty(sender);

		assertNull(bounty.getPaidAmount(sender), "a removed contributor must have no refundable amount left");

		bounty.addBounty(sender, Currency.of(100), 5);
		bounty.resetBounty();

		assertNull(bounty.getPaidAmount(sender));
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
	@DisplayName("WB-13: notoriety moves the total, never the posted ledger")
	void setAmount_movesTheTotal_neverThePostedLedger() {
		Bounty bounty = bounty(100, 2.0);
		CommandSender sender = named("poster");
		bounty.addBounty(sender, Currency.of(10), 0);

		bounty.setAmount(Currency.of(9999));

		assertEquals(Currency.of(9999), bounty.getAmount());
		assertEquals(1, bounty.size());
		assertEquals(Currency.of(10), bounty.getPostedAmount(), "the escrow is only what was posted");
		assertEquals(Currency.of(9989), bounty.getNotoriety());
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
		CommandSender contributor = named("contributor");
		CommandSender stranger = named("stranger");
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

	@Test
	@DisplayName("WB-12: the ledger is keyed by poster id, so a relogged Player object still finds its entry")
	void ledger_isKeyedByPosterId_aRelogStillFindsThePoster() {
		Bounty bounty = bounty(0, 0.0);
		UUID id = UUID.randomUUID();
		Player before = player(id);
		Player after = player(id);
		bounty.addBounty(before, Currency.of(30), 0);

		assertTrue(bounty.containsBounty(after));
		assertEquals(Currency.of(30), bounty.getPaidAmount(after));

		bounty.removeBounty(after);

		assertEquals(Currency.ZERO, bounty.getAmount());
	}

	@Test
	@DisplayName("postedAmount sums the PAID figures, not the level-scaled ones")
	void postedAmount_sumsThePaidFigures() {
		Bounty bounty = bounty(0, 2.0);
		bounty.addBounty(named("a"), Currency.of(100), 5);   // posted 200, paid 100
		bounty.addBounty(named("b"), Currency.of(50), 0);

		assertEquals(Currency.of(150), bounty.getPostedAmount());
	}

	@Test
	@DisplayName("notoriety is the server-made remainder of the total")
	void notoriety_isTheServerMadeRemainder() {
		Bounty bounty = bounty(0, 0.0);
		bounty.addBounty(named("a"), Currency.of(100), 0);
		bounty.addNotoriety(Currency.of(40));
		bounty.addNotoriety(Currency.of(-5));

		assertEquals(Currency.of(140), bounty.getAmount());
		assertEquals(Currency.of(40), bounty.getNotoriety());
	}

	@Test
	@DisplayName("claimPosted pays the posted escrow, keeps the notoriety and clears the ledger")
	void claimPosted_paysPosted_keepsNotoriety() {
		Bounty bounty = bounty(0, 0.0);
		bounty.addBounty(named("a"), Currency.of(100), 0);
		bounty.addNotoriety(Currency.of(40));

		assertEquals(Currency.of(100), bounty.claimPosted());

		assertEquals(Currency.of(40), bounty.getAmount());
		assertEquals(Currency.of(40), bounty.getNotoriety());
		assertEquals(Currency.ZERO, bounty.getPostedAmount());
		assertEquals(0, bounty.size());
	}

	@Test
	@DisplayName("serialize then restore round-trips posters and the paid refund")
	void serializeRestore_roundTrips() {
		Bounty bounty = bounty(0, 2.0);
		bounty.addBounty(named("a"), Currency.of(100), 5);
		bounty.addBounty(named("b"), Currency.of(50), 0);
		bounty.addNotoriety(Currency.of(7));
		String saved = bounty.serializeLedger();

		Bounty loaded = bounty(0, 2.0);
		loaded.setAmount(bounty.getAmount());
		loaded.restoreLedger(saved);

		assertEquals(saved, loaded.serializeLedger());
		assertEquals(bounty.getPostedAmount(), loaded.getPostedAmount());
		assertEquals(Currency.of(100), loaded.getPaidAmount(named("a")));
		assertEquals(Currency.of(200), loaded.getSetAmount(named("a")));
		assertEquals(bounty.getNotoriety(), loaded.getNotoriety());
	}

	@Test
	@DisplayName("a null column is a pre-0.15 row: the whole bounty counts as posted, once")
	void restoreNull_legacyBountyCountsAsPostedOnce() {
		Bounty bounty = bounty(0, 0.0);
		bounty.setAmount(Currency.of(300));

		bounty.restoreLedger(null);

		assertEquals(Currency.of(300), bounty.getPostedAmount());
		assertEquals(Currency.ZERO, bounty.getNotoriety());
		assertEquals(Currency.of(300), bounty.claimPosted());
		assertEquals(Currency.ZERO, bounty.claimPosted(), "paid in full once, never again");
	}

	@Test
	@DisplayName("an empty column is an empty ledger: all notoriety")
	void restoreEmpty_isEmpty() {
		Bounty bounty = bounty(0, 0.0);
		bounty.setAmount(Currency.of(300));

		bounty.restoreLedger("");

		assertEquals(0, bounty.size());
		assertEquals(Currency.of(300), bounty.getNotoriety());
		assertEquals("", bounty.serializeLedger());
	}

	@Test
	@DisplayName("damaged entries are skipped, good ones kept")
	void badLedgerEntries_areSkipped() {
		Bounty bounty = bounty(0, 0.0);
		bounty.setAmount(Currency.of(50));

		bounty.restoreLedger("junk;x=1;y=abc:2;=1:1;good=20:10;");

		assertEquals(1, bounty.size());
		assertEquals(Currency.of(10), bounty.getPostedAmount());
	}

	private static CommandSender named(String name) {
		CommandSender sender = mock(CommandSender.class);
		when(sender.getName()).thenReturn(name);
		return sender;
	}

	private static Player player(UUID id) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		return player;
	}

	private static Bounty bounty(double baseAmount, double levelMultiplier) {
		return new Bounty(Currency.of(baseAmount), levelMultiplier);
	}

}
