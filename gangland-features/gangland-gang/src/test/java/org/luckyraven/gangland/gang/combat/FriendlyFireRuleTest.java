package org.luckyraven.gangland.gang.combat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.combat.FriendlyFireRule.Global;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PLAN 15.5: the seven steps, first match wins. */
@DisplayName("FriendlyFireRule - the 7-step table")
class FriendlyFireRuleTest {

	private static final Optional<Boolean> NONE = Optional.empty();

	/** Same gang (1 vs 1), different players; self damage allowed, no allied rows. */
	private static boolean same(boolean worldDisabled, Optional<Boolean> region, Optional<Boolean> world,
	                            Global global, boolean gangFf) {
		return FriendlyFireRule.blocked(1, 1, false, false, worldDisabled, region, world, global, gangFf, false, true);
	}

	private static boolean allied(Optional<Boolean> region, Optional<Boolean> world, Global global, boolean bothRows) {
		return FriendlyFireRule.blocked(1, 2, true, false, false, region, world, global, false, bothRows, true);
	}

	@Test
	@DisplayName("step 1: a gangless side or a disabled world is never blocked")
	void step1_gangsDoNotExist() {
		assertFalse(FriendlyFireRule.blocked(-1, 1, false, false, false, NONE, NONE, Global.GANG_CHOICE, false, false,
		                                     true));
		assertFalse(FriendlyFireRule.blocked(1, -1, false, false, false, NONE, NONE, Global.GANG_CHOICE, false, false,
		                                     true));
		assertFalse(same(true, NONE, NONE, Global.OFF, false), "disabled world");
	}

	@Test
	@DisplayName("step 2: own damage passes by default and is blocked only when Allow_Self_Damage is off")
	void step2_self() {
		assertFalse(FriendlyFireRule.blocked(1, 1, false, true, false, NONE, NONE, Global.GANG_CHOICE, false, false,
		                                     true));
		assertTrue(FriendlyFireRule.blocked(1, 1, false, true, false, NONE, NONE, Global.GANG_CHOICE, false, false,
		                                    false));
		assertFalse(FriendlyFireRule.blocked(1, 1, false, true, false, Optional.of(false), NONE, Global.OFF, false,
		                                     false, true), "self is decided before region, world and Global");
	}

	@Test
	@DisplayName("step 3: the region flag decides before the world and the gang")
	void step3_region() {
		assertFalse(same(false, Optional.of(true), Optional.of(false), Global.OFF, false), "ALLOW wins");
		assertTrue(same(false, Optional.of(false), Optional.of(true), Global.ON, true), "DENY wins");
	}

	@Test
	@DisplayName("step 4: the per-world rule decides before the gang switch")
	void step4_world() {
		assertFalse(same(false, NONE, Optional.of(true), Global.OFF, false));
		assertTrue(same(false, NONE, Optional.of(false), Global.ON, true));
	}

	@Test
	@DisplayName("step 5: gangmates - blocked unless GANG_CHOICE with the switch on, or Global ON; OFF always blocks")
	void step5_sameGang() {
		assertTrue(same(false, NONE, NONE, Global.GANG_CHOICE, false), "default");
		assertFalse(same(false, NONE, NONE, Global.GANG_CHOICE, true), "gang pvp on");
		assertFalse(same(false, NONE, NONE, Global.ON, false), "Global ON");
		assertTrue(same(false, NONE, NONE, Global.OFF, true), "Global OFF beats the switch");
	}

	@Test
	@DisplayName("step 6: allies - blocked unless both rows are on, and Global is not OFF")
	void step6_allied() {
		assertTrue(allied(NONE, NONE, Global.GANG_CHOICE, false), "rows not both on");
		assertFalse(allied(NONE, NONE, Global.GANG_CHOICE, true), "both rows on");
		assertTrue(allied(NONE, NONE, Global.OFF, true), "Global OFF");
		assertTrue(allied(Optional.of(false), NONE, Global.GANG_CHOICE, true), "region DENY");
	}

	@Test
	@DisplayName("step 7: rival gangs are never blocked by this rule")
	void step7_rivals() {
		assertFalse(FriendlyFireRule.blocked(1, 2, false, false, false, Optional.of(false), Optional.of(false),
		                                     Global.OFF, false, false, false));
	}

	@Test
	@DisplayName("Global parses its three words, unknown or null means GANG_CHOICE")
	void globalParse() {
		assertEquals(Global.OFF, Global.parse("off"));
		assertEquals(Global.ON, Global.parse("ON"));
		assertEquals(Global.GANG_CHOICE, Global.parse("gang_choice"));
		assertEquals(Global.GANG_CHOICE, Global.parse("nonsense"));
		assertEquals(Global.GANG_CHOICE, Global.parse(null));
	}
}
