package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages.Key;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.data.economy.HotCash;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.luckyraven.gangland.copsncrooks.bag.BagFixture.bd;

/**
 * Lane bag: {@link BagService}. Cash taken while wanted goes into the bag; every ending moves the bag exactly once
 * (P-BAG-1, 4, 5, 6, 7, 10, 12) and the fence pays less than a getaway (P-FENCE-1, 2).
 */
@DisplayName("BagService - take, endings, fence")
class BagServiceTest {

	@TempDir
	Path tempDir;

	private BagFixture fx;
	private Player     runner;

	@BeforeEach
	void setUp() {
		fx     = new BagFixture(tempDir);
		runner = fx.player("Runner");
	}

	private void fill(String amount) {
		fx.level(runner, 1);
		assertEquals(HotCash.Take.BAGGED, fx.service.take(runner, bd(amount), "pickup"));
	}

	@Test
	@DisplayName("P-BAG-1: not wanted, the take goes to the wallet and the bag stays empty")
	void notWantedTakesWallet() {
		assertEquals(HotCash.Take.WALLET, fx.service.take(runner, bd("120"), "pickup"));
		assertEquals(0, bd("0").compareTo(fx.bag(runner)));
	}

	@Test
	@DisplayName("P-BAG-1: wanted, a pickup is bagged, the HUD refreshes and the debug line is exact")
	void wantedPickupIsBagged() {
		fx.level(runner, 1);

		assertEquals(HotCash.Take.BAGGED, fx.service.take(runner, bd("120"), "pickup"));

		assertEquals(0, bd("120").compareTo(fx.bag(runner)));
		assertTrue(fx.debug.contains("BAG Runner +120 source=pickup bag=120"), fx.debug.toString());
		assertEquals(0, bd("120").compareTo(fx.huds.get(fx.huds.size() - 1)));
	}

	@Test
	@DisplayName("P-BAG-1: the wanted level is read live, so the drop of the kill that gave the first star is bagged")
	void firstStarKillDropIsBagged() {
		assertEquals(HotCash.Take.WALLET, fx.service.take(runner, bd("10"), "pickup"));

		fx.level(runner, 1);

		assertEquals(HotCash.Take.BAGGED, fx.service.take(runner, bd("50"), "pickup"));
	}

	@Test
	@DisplayName("Enable false: wanted or not, cash goes to the wallet")
	void disabledTakesWallet() {
		fx.settings = new BagSettings(false, 0.35, 5000, 15000, 4, "medium", true, 40);
		fx.level(runner, 3);

		assertEquals(HotCash.Take.WALLET, fx.service.take(runner, bd("120"), "store"));
	}

	@Test
	@DisplayName("P-BAG-10: the chase cap refuses the overflow; nothing is added and the message names Bag.Full once per 3 s")
	void chaseCapRefusesOverflow() {
		fx.level(runner, 2);
		assertEquals(HotCash.Take.BAGGED, fx.service.take(runner, bd("4900"), "pickup"));

		assertEquals(HotCash.Take.FULL, fx.service.take(runner, bd("200"), "pickup"));
		assertEquals(HotCash.Take.FULL, fx.service.take(runner, bd("200"), "pickup"));

		assertEquals(0, bd("4900").compareTo(fx.bag(runner)));
		assertEquals(1, fx.chat.stream().filter(l -> l.equals(fx.line(Key.BAG_FULL))).count(), "throttled to one line");
		assertTrue(fx.debug.contains("BAG Runner full source=pickup bag=4900"), fx.debug.toString());

		fx.now += 3_001;
		fx.service.take(runner, bd("200"), "pickup");

		assertEquals(2, fx.chat.stream().filter(l -> l.equals(fx.line(Key.BAG_FULL))).count(), "again after 3 s");
	}

	@Test
	@DisplayName("P-BAG-10: Max_Per_Chase 0 means no cap")
	void zeroCapIsNoCap() {
		fx.settings = new BagSettings(true, 0.35, 0, 15000, 4, "medium", true, 40);
		fx.level(runner, 2);

		assertEquals(HotCash.Take.BAGGED, fx.service.take(runner, bd("900000"), "pickup"));
	}

	@Test
	@DisplayName("P-BAG-10: an ending resets the chase counter, so the next chase can bag up to the cap again")
	void endResetsChaseCap() {
		fill("5000");
		fx.service.end(runner, WantedCause.EVASION);

		assertEquals(HotCash.Take.BAGGED, fx.service.take(runner, bd("5000"), "pickup"));
	}

	@Test
	@DisplayName("P-BAG-4: a getaway banks the bag into the wallet under the daily cap")
	void escapeBanksUnderDailyCap() {
		fill("1000");

		fx.service.end(runner, WantedCause.EVASION);

		assertEquals(0, bd("1000").compareTo(fx.walletOf(runner)));
		assertEquals(0, bd("0").compareTo(fx.bag(runner)));
		assertTrue(fx.chat.contains(fx.line(Key.BAG_BANKED, "amount", "1000")), fx.chat.toString());
		assertTrue(fx.debug.contains("BAG Runner end cause=EVASION outcome=banked bag=1000 paid=1000"),
		           fx.debug.toString());
	}

	@Test
	@DisplayName("P-BAG-4: over the day's allowance the rest converts at the fence cut")
	void escapeOverCapConvertsRest() {
		fx.bags.put(new BagEntry(runner.getUniqueId(), BigDecimal.ZERO, BigDecimal.ZERO, 10L, bd("14500"),
		                         BigDecimal.ZERO));
		fill("1000");

		fx.service.end(runner, WantedCause.DECAY);

		assertEquals(0, bd("825").compareTo(fx.walletOf(runner)), "500 banked + 500 x 0.65");
		assertTrue(fx.chat.contains(fx.line(Key.BAG_BANKED, "amount", "500")), fx.chat.toString());
		assertTrue(fx.chat.contains(fx.line(Key.BAG_OVER_CAP, "cut", "175", "rest", "500")), fx.chat.toString());
		assertEquals(10L, fx.bags.entry(runner.getUniqueId()).bankedDay());
		assertEquals(0, bd("15000").compareTo(fx.bags.entry(runner.getUniqueId()).bankedToday()));
	}

	@Test
	@DisplayName("P-BAG-4: the allowance starts over on a new server day")
	void dailyAllowanceResetsOnNewDay() {
		fx.bags.put(new BagEntry(runner.getUniqueId(), BigDecimal.ZERO, BigDecimal.ZERO, 10L, bd("15000"),
		                         BigDecimal.ZERO));
		fx.day = 11L;
		fill("1000");

		fx.service.end(runner, WantedCause.EVASION);

		assertEquals(0, bd("1000").compareTo(fx.walletOf(runner)));
		assertEquals(11L, fx.bags.entry(runner.getUniqueId()).bankedDay());
		assertEquals(0, bd("1000").compareTo(fx.bags.entry(runner.getUniqueId()).bankedToday()));
	}

	@Test
	@DisplayName("P-BAG-4: Max_Banked_Per_Day 0 banks everything")
	void zeroDailyCapBanksAll() {
		fx.settings = new BagSettings(true, 0.35, 0, 0, 4, "medium", true, 40);
		fill("90000");

		fx.service.end(runner, WantedCause.EVASION);

		assertEquals(0, bd("90000").compareTo(fx.walletOf(runner)));
	}

	@ParameterizedTest
	@EnumSource(value = WantedCause.class, names = {"SIGN", "CONTACT", "BRIBE", "ADMIN", "UNKNOWN", "CRIME", "RESTORE"})
	@DisplayName("P-BAG-5: every other clear converts the bag at the fence cut (65%)")
	void otherClearsConvertAtCut(WantedCause cause) {
		fill("1000");

		fx.service.end(runner, cause);

		assertEquals(0, bd("650").compareTo(fx.walletOf(runner)));
		assertEquals(0, bd("0").compareTo(fx.bag(runner)));
		assertTrue(fx.chat.contains(fx.line(Key.BAG_CONVERTED, "bag", "1000", "amount", "650")), fx.chat.toString());
		assertTrue(fx.debug.contains("BAG Runner end cause=" + cause + " outcome=converted bag=1000 paid=650"),
		           fx.debug.toString());
	}

	@Test
	@DisplayName("P-BAG-6: ARREST seizes the bag - no wallet, no spill - and keeps the amount on the row")
	void arrestSeizes() {
		fill("1000");

		fx.service.end(runner, WantedCause.ARREST);

		assertEquals(0, bd("0").compareTo(fx.walletOf(runner)));
		assertTrue(fx.spills.isEmpty());
		assertEquals(0, bd("1000").compareTo(fx.bags.seized(runner.getUniqueId())));
		assertTrue(fx.debug.contains("BAG Runner end cause=ARREST outcome=seized bag=1000 paid=0"), fx.debug.toString());
	}

	@Test
	@DisplayName("P-BAG-7: dying spills the bag once at the player's spot; the wallet is untouched")
	void deathSpillsAtLocation() {
		fill("1000");

		fx.service.end(runner, WantedCause.DEATH);

		assertEquals(1, fx.spills.size());
		assertEquals(0, bd("1000").compareTo(fx.spills.get(0).amount()));
		assertSame(runner.getLocation(), fx.spills.get(0).at());
		assertEquals(0, bd("0").compareTo(fx.walletOf(runner)));
		assertEquals(0, bd("0").compareTo(fx.bag(runner)));
		assertTrue(fx.chat.contains(fx.line(Key.BAG_SPILLED, "amount", "1000")), fx.chat.toString());
		assertTrue(fx.debug.contains("BAG Runner end cause=DEATH outcome=spilled bag=1000 paid=0"), fx.debug.toString());
	}

	@Test
	@DisplayName("D-W2-08: death while restrained seizes instead of spilling")
	void deathWhileRestrainedSeizes() {
		fill("1000");
		fx.restrained.add(runner.getUniqueId());

		fx.service.end(runner, WantedCause.DEATH);

		assertTrue(fx.spills.isEmpty());
		assertEquals(0, bd("1000").compareTo(fx.bags.seized(runner.getUniqueId())));
	}

	@ParameterizedTest
	@EnumSource(value = WantedCause.class, names = {"EVASION", "DECAY"})
	@DisplayName("D-W2-30: a getaway cause while restrained seizes; the wallet never gets it")
	void escapeWhileRestrainedSeizes(WantedCause cause) {
		fill("1000");
		fx.restrained.add(runner.getUniqueId());

		fx.service.end(runner, cause);

		assertEquals(0, bd("0").compareTo(fx.walletOf(runner)));
		assertEquals(0, bd("1000").compareTo(fx.bags.seized(runner.getUniqueId())));
		assertEquals(0, bd("0").compareTo(fx.bag(runner)));
	}

	@Test
	@DisplayName("P-BAG-12: the bag pays exactly once - a second ending finds it empty and prints nothing")
	void paysExactlyOnce() {
		fill("1000");

		fx.service.end(runner, WantedCause.EVASION);
		int chatAfterFirst = fx.chat.size();
		fx.service.end(runner, WantedCause.SIGN);

		assertEquals(0, bd("1000").compareTo(fx.walletOf(runner)));
		assertEquals(chatAfterFirst, fx.chat.size());
	}

	@Test
	@DisplayName("an ending with an empty bag does nothing at all")
	void emptyBagEndingIsSilent() {
		fx.service.end(runner, WantedCause.DEATH);

		assertTrue(fx.chat.isEmpty());
		assertTrue(fx.spills.isEmpty());
		assertTrue(fx.debug.isEmpty());
	}

	@Test
	@DisplayName("seize returns the bag, empties it and stores it; a later empty seizure clears the stored amount")
	void seizeTakesTheBag() {
		fill("800");

		assertEquals(800.0, fx.service.seize(runner));
		assertEquals(0, bd("0").compareTo(fx.bag(runner)));
		assertEquals(0, bd("800").compareTo(fx.bags.seized(runner.getUniqueId())));

		assertEquals(0.0, fx.service.seize(runner));
		assertEquals(0, bd("0").compareTo(fx.bags.seized(runner.getUniqueId())));
	}

	@Test
	@DisplayName("payouts round DOWN to the cent: 37.50 at the 65% cut is 24.37, never 24.38")
	void fenceRoundsDownToCents() {
		fill("37.50");

		assertTrue(fx.service.fence(runner));

		assertEquals(0, bd("24.37").compareTo(fx.walletOf(runner)), fx.walletOf(runner).toPlainString());
	}

	@Test
	@DisplayName("P-FENCE-1: the fence pays bag x 0.65 now, empties the bag, leaves the stars and keeps the chase count")
	void fenceCutsAndKeepsStars() {
		fill("1000");

		assertTrue(fx.service.fence(runner));

		assertEquals(0, bd("650").compareTo(fx.walletOf(runner)));
		assertEquals(0, bd("0").compareTo(fx.bag(runner)));
		assertEquals(1, fx.wantedLevel(runner), "stars untouched");
		assertEquals(0, bd("1000").compareTo(fx.bags.chaseTaken(runner.getUniqueId())), "the chase cap still counts");
		assertTrue(fx.chat.contains(fx.line(Key.FENCE_DONE, "amount", "650", "bag", "1000")), fx.chat.toString());
		assertTrue(fx.debug.contains("FENCE Runner bag=1000 paid=650"), fx.debug.toString());
	}

	@Test
	@DisplayName("P-FENCE-2: never in cuffs, and an empty bag says so; the wallet never changes")
	void fenceRefusesCuffedAndEmpty() {
		fill("1000");
		fx.restrained.add(runner.getUniqueId());

		assertFalse(fx.service.fence(runner));
		assertTrue(fx.chat.contains(fx.line(Key.FENCE_CUFFED)), fx.chat.toString());
		assertEquals(0, bd("1000").compareTo(fx.bag(runner)));
		assertTrue(fx.debug.contains("FENCE Runner refused reason=cuffed"), fx.debug.toString());

		fx.restrained.clear();
		fx.bags.takeAll(runner.getUniqueId());

		assertFalse(fx.service.fence(runner));
		assertTrue(fx.chat.contains(fx.line(Key.FENCE_EMPTY)), fx.chat.toString());
		assertTrue(fx.debug.contains("FENCE Runner refused reason=empty"), fx.debug.toString());
		assertEquals(0, bd("0").compareTo(fx.walletOf(runner)));
	}

	@Test
	@DisplayName("money is never made: for any bag the fence pays less than a getaway, and every ending pays at most the bag")
	void fenceIsAlwaysWorseThanEscape() {
		for (String amount : List.of("1", "37.5", "1000", "4999.99", "100000")) {
			BagFixture other  = new BagFixture(tempDir);
			Player     a      = other.player("A");
			Player     b      = other.player("B");
			other.settings = new BagSettings(true, 0.35, 0, 0, 4, "medium", true, 40);
			other.level(a, 1);
			other.level(b, 1);
			other.service.take(a, bd(amount), "pickup");
			other.service.take(b, bd(amount), "pickup");

			other.service.fence(a);
			other.service.end(b, WantedCause.EVASION);

			assertTrue(other.walletOf(a).compareTo(other.walletOf(b)) < 0, amount + " fence must pay less");
			assertTrue(other.walletOf(b).compareTo(bd(amount)) <= 0, amount + " getaway pays at most the bag");
		}
	}

	@Test
	@DisplayName("BagFunding: available is the bag, debit removes it and never goes below zero")
	void fundingReadsAndDebits() {
		fill("600");

		assertEquals(0, bd("600").compareTo(fx.service.available(runner)));

		fx.service.debit(runner, bd("400"));
		assertEquals(0, bd("200").compareTo(fx.service.available(runner)));

		fx.service.debit(runner, bd("999"));
		assertEquals(0, bd("0").compareTo(fx.service.available(runner)));
	}

	@Test
	@DisplayName("P-FENCE-3: announceBribe prints Bag.Bribe with both parts and the debug line")
	void announceBribePrintsLineAndDebug() {
		fill("600");

		fx.service.announceBribe(runner, bd("600"), bd("400"));

		assertTrue(fx.chat.contains(fx.line(Key.BAG_BRIBE, "bag", "600", "wallet", "400")), fx.chat.toString());
		assertTrue(fx.debug.contains("BAG Runner bribe bag=600 wallet=400"), fx.debug.toString());
	}

	@Test
	@DisplayName("P-BAG-2: the HUD refresh sends only to players holding a bag, and not at all with Hud_Enable false")
	void hudRefreshSendsOnlyHolders() {
		Player empty = fx.player("Empty");
		fill("75");
		fx.huds.clear();

		fx.service.refreshHud(List.of(runner, empty));

		assertEquals(1, fx.huds.size());
		assertEquals(0, bd("75").compareTo(fx.huds.get(0)));

		fx.huds.clear();
		fx.settings = new BagSettings(true, 0.35, 5000, 15000, 4, "medium", false, 40);
		fx.service.refreshHud(List.of(runner));

		assertTrue(fx.huds.isEmpty());
	}

	@Test
	@DisplayName("the paperwork line is the Bag.Paperwork text while something was seized, else null")
	void paperworkLine() {
		assertEquals(null, fx.service.paperworkLine(runner.getUniqueId()));

		fill("800");
		fx.service.seize(runner);

		assertEquals(fx.line(Key.BAG_PAPERWORK, "amount", "800"),
		             org.bukkit.ChatColor.stripColor(fx.service.paperworkLine(runner.getUniqueId())));
	}
}
