package org.luckyraven.gangland.turf.npc.defender;

import java.util.Locale;

/**
 * Gang-tagged guard callsign: {@code [VIP] Guard 3}. The tag is the first three letters or digits of the gang's
 * colour-stripped display name (padded from the gang name when that is short), so it is never empty. The Citizens NPC
 * name is the same text cut to 16 characters; it starts with {@code [}, so it can never read as a {@code CIT-} name.
 */
public final class GuardCallsign {

	private static final int    MAX_NPC_NAME = 16;
	private static final String CIT          = "CIT";
	private static final String FALLBACK_TAG = "GNG";

	private GuardCallsign() {
	}

	/** Three letters or digits (four when three would read {@code CIT}); {@code GNG} when the names hold none. */
	public static String tag(String displayName, String gangName) {
		String display  = alnum(displayName);
		String letters  = display.length() >= 3 ? display : display + alnum(gangName);
		String combined = letters + alnum(gangName);
		if (letters.isEmpty()) {
			return FALLBACK_TAG;
		}
		String tag = letters.substring(0, Math.min(3, letters.length()));
		if (tag.equals(CIT)) {
			tag = combined.length() > 3 ? combined.substring(0, 4) : CIT + "X";
		}
		return tag;
	}

	/** The callsign shown above the guard: {@code [TAG] Guard n}. */
	public static String format(String tag, int n) {
		return "[" + tag + "] Guard " + n;
	}

	/** The Citizens NPC name: the callsign, plain text, at most 16 characters. */
	public static String npcName(String tag, int n) {
		String name = format(tag, n);
		return name.length() <= MAX_NPC_NAME ? name : name.substring(0, MAX_NPC_NAME);
	}

	/** Upper-case letters and digits left once colour codes (ampersand or section sign plus code) and symbols are dropped. */
	private static String alnum(String name) {
		if (name == null) {
			return "";
		}
		String plain = name.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
		return plain.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
	}
}
