package org.luckyraven.gangland.mail.command.ally;

import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.level.GangLimits;

/** D17 A: the ally cap of a gang's level ({@code Levels.Max_Allies}, 0 = unlimited), shared by request and accept. */
final class AllyCap {

	private AllyCap() {
	}

	/** The first of the gangs that already holds the allies its level allows, or {@code null} when none does. */
	static Gang firstAtCap(GangLimits limits, Gang... gangs) {
		for (Gang gang : gangs) {
			int cap = limits.maxAllies(gang.getId());
			if (cap > 0 && gang.getAllies().size() >= cap) return gang;
		}
		return null;
	}

	static String refusal(GangLimits limits, GangMessages messages, Gang full) {
		return messages.text("Levels.Ally_Cap", "&c%gang% is at its limit of &e%cap% &callies for its level.",
		                     "gang", full.getDisplayNameString(), "cap", limits.maxAllies(full.getId()));
	}
}
