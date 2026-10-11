package org.luckyraven.gangland.gang.combat;

import java.util.Optional;

/** RED stub: the pure friendly-fire table (PLAN 15.5). */
public final class FriendlyFireRule {

	public enum Global {
		OFF,
		GANG_CHOICE,
		ON;

		public static Global parse(String value) {
			return GANG_CHOICE;
		}
	}

	private FriendlyFireRule() {
	}

	public static boolean blocked(int attackerGang, int victimGang, boolean allied, boolean self,
	                              boolean worldDisabled, Optional<Boolean> region, Optional<Boolean> worldRule,
	                              Global global, boolean gangFriendlyFire, boolean allyFriendlyFire,
	                              boolean allowSelfDamage) {
		return false;
	}
}
