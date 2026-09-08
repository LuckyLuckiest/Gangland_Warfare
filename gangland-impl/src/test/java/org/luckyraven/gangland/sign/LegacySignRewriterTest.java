package org.luckyraven.gangland.sign;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins {@link LegacySignRewriter}'s six-header mapping (gangland-0.9.0.md T-G4, A2 Q-G/R5): a sign placed with a
 * legacy weapon/ammo/wearable header must still rewrite onto the generic item-buy/item-sell pair plus the correct
 * line-3 definition prefix, and an unrecognised header must be left untouched (returns {@code null}), never guessed
 * at or defaulted. Fixture values mirror {@code settings.yml}'s {@code Signs.Legacy_Aliases} defaults exactly.
 */
@DisplayName("LegacySignRewriter — legacy weapon/ammo/wearable header mapping")
class LegacySignRewriterTest {

	private LegacySignRewriter rewriter;

	@BeforeEach
	void setUp() {
		rewriter = new LegacySignRewriter("item-buy:weapon", "item-sell:weapon", "item-buy:ammo", "item-sell:ammo",
		                                  "item-buy:wearable", "item-sell:wearable");
	}

	@Test
	@DisplayName("weapon-buy / weapon-sell rewrite onto item-buy/item-sell with the weapon: prefix")
	void weaponHeaders_rewriteToItemBuySellWithWeaponPrefix() {
		assertEquals(new LegacySignRewriter.Rewritten("item-buy", "weapon"), rewriter.rewrite("weapon-buy"));
		assertEquals(new LegacySignRewriter.Rewritten("item-sell", "weapon"), rewriter.rewrite("weapon-sell"));
	}

	@Test
	@DisplayName("ammo-buy / ammo-sell rewrite onto item-buy/item-sell with the ammo: prefix")
	void ammoHeaders_rewriteToItemBuySellWithAmmoPrefix() {
		assertEquals(new LegacySignRewriter.Rewritten("item-buy", "ammo"), rewriter.rewrite("ammo-buy"));
		assertEquals(new LegacySignRewriter.Rewritten("item-sell", "ammo"), rewriter.rewrite("ammo-sell"));
	}

	@Test
	@DisplayName("wearable-buy / wearable-sell rewrite onto item-buy/item-sell with the wearable: prefix")
	void wearableHeaders_rewriteToItemBuySellWithWearablePrefix() {
		assertEquals(new LegacySignRewriter.Rewritten("item-buy", "wearable"), rewriter.rewrite("wearable-buy"));
		assertEquals(new LegacySignRewriter.Rewritten("item-sell", "wearable"), rewriter.rewrite("wearable-sell"));
	}

	@Test
	@DisplayName("an unrecognised header is left untouched — rewrite returns null, never a guess")
	void unknownHeader_leftUntouched() {
		assertNull(rewriter.rewrite("buy"));
		assertNull(rewriter.rewrite("item-buy"));
		assertNull(rewriter.rewrite("car-buy"));
		assertNull(rewriter.rewrite(""));
	}

	@Test
	@DisplayName("a null header is left untouched — never throws (gate D-G review finding B6)")
	void nullHeader_leftUntouched() {
		assertNull(rewriter.rewrite(null));
	}

	@Test
	@DisplayName("a placed sign's upper-case header still matches — rewrite is case-insensitive (gate D-G review finding B6)")
	void upperCaseHeader_stillMatches() {
		assertEquals(new LegacySignRewriter.Rewritten("item-buy", "weapon"), rewriter.rewrite("WEAPON-BUY"));
		assertEquals(new LegacySignRewriter.Rewritten("item-sell", "wearable"), rewriter.rewrite("Wearable-Sell"));
	}

}
