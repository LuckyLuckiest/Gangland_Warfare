package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.ChatColor;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.keystone.util.ChatUtil;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Validates a gang name for create and rename. Names are the command key, so they are always unique. */
public final class GangNameRules {

	static final String INVALID = "Names.Invalid";
	static final String TAKEN   = "Names.Taken";

	static final String DISPLAY_HEX      = "Names.Display_Hex";
	static final String DISPLAY_TOO_LONG = "Names.Display_Too_Long";
	static final String DISPLAY_BLOCKED  = "Names.Display_Blocked";
	static final String DISPLAY_TAKEN    = "Names.Display_Taken";

	private GangNameRules() {
	}

	/** A gang name as the player sees it: colour codes removed. Names are stored raw, so lookups compare this form. */
	public static String plain(String raw) {
		return ChatColor.stripColor(ChatUtil.color(raw));
	}

	/** The player-facing refusal for a key returned by {@link #check}, from gang_messages.yml. */
	public static String refusal(GangMessages messages, String key, String name) {
		switch (key) {
			case DISPLAY_HEX:
				return messages.text(key, "&cHex colours are not allowed in a display name.");
			case DISPLAY_TOO_LONG:
				return messages.text(key, "&cA display name is at most %max% visible characters.", "max", name);
			case DISPLAY_BLOCKED:
				return messages.text(key, "&cThat display name contains a blocked word.");
			case DISPLAY_TAKEN:
				return messages.text(key, "&cThat display name looks the same as another gang's.");
			default:
				break;
		}
		if (TAKEN.equals(key)) return messages.text(TAKEN, "&cA gang named %gang% already exists.", "gang", name);
		return messages.text(INVALID, "&cGang names are 3 to 16 letters, digits or underscores.");
	}

	/** Validates a gang name for create and rename. Returns the gang_messages.yml key of the refusal, or empty. */
	public static Optional<String> check(String rawName, Pattern pattern, Collection<Gang> existing,
	                                     @Nullable Integer selfId) {
		String name = plain(rawName);

		if (!pattern.matcher(name).matches()) return Optional.of(INVALID);

		for (Gang gang : existing) {
			if (selfId != null && gang.getId() == selfId) continue;
			if (gang.getName() == null) continue;

			if (key(gang.getName()).equals(key(name))) return Optional.of(TAKEN);
		}

		return Optional.empty();
	}

	private static final Pattern COLOUR_CODE = Pattern.compile("(?i)[&\u00a7](#[0-9a-f]{6}|x(?:[&\u00a7][0-9a-f]){6}|[0-9a-fk-or])");
	private static final Pattern HEX_CODE    = Pattern.compile("(?i)[&\u00a7]#[0-9a-f]{6}");
	/** Characters a name must never be able to contain: they split commands, mentions and chat lines. */
	private static final String  FORBIDDEN   = " \t\u00a0@/;";
	/** Look-alikes of Latin letters in Cyrillic and Greek (already lower case), folded to the Latin letter. */
	private static final String  LOOK_ALIKE_FROM = "\u0430\u0435\u043e\u0440\u0441\u0443\u0445\u0456\u0458\u0455\u043a\u043c\u043d\u0442\u0432\u0501\u051b\u051d"
	                                               + "\u03b1\u03bf\u03bd\u03c1\u03b9\u03ba\u03c4\u03c5\u03c7\u03b5\u03b2\u03b7\u03bc";
	private static final String  LOOK_ALIKE_TO   = "aeopcyxijskmhtbdqw"
	                                               + "aovpiktuxebnu";

	/** The text without any colour code (legacy, section sign and {@code &#rrggbb}). */
	private static String stripCodes(String raw) {
		return COLOUR_CODE.matcher(raw).replaceAll("");
	}

	/**
	 * The comparison key of a name or display name: colour codes dropped, NFKC-normalised, lower-cased, and Cyrillic or
	 * Greek look-alikes of Latin letters folded to the Latin letter. Two names with the same key look the same.
	 * ponytail: only the common look-alikes are folded (no full Unicode confusables table); extend the two strings.
	 */
	public static String key(String raw) {
		String folded = Normalizer.normalize(stripCodes(raw), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(folded.length());

		for (int i = 0; i < folded.length(); i++) {
			char c     = folded.charAt(i);
			int  index = LOOK_ALIKE_FROM.indexOf(c);
			out.append(index < 0 ? c : LOOK_ALIKE_TO.charAt(index));
		}

		return out.toString();
	}

	/**
	 * True when the pattern would accept a name holding whitespace, {@code @}, {@code /} or {@code ;}: probed with each
	 * character among 0 to 8 letters on either side. ponytail: a probe, not a proof; a pattern that needs a digit
	 * prefix could slip past, tighten the probe alphabet if that ever matters.
	 */
	public static boolean allowsForbidden(Pattern pattern) {
		for (char forbidden : FORBIDDEN.toCharArray()) {
			for (int before = 0; before <= 8; before++) {
				for (int after = 0; after <= 8; after++) {
					String probe = "a".repeat(before) + forbidden + "a".repeat(after);
					if (pattern.matcher(probe).matches()) return true;
				}
			}
		}

		return false;
	}

	/** The display text stored for the gang; colour codes are dropped when Parse_Colours is off. */
	public static String storedDisplay(String raw, GangRules rules) {
		return rules.isDisplayNameParseColours() ? raw : stripCodes(raw);
	}

	/** Validates a display name. Returns the gang_messages.yml key of the refusal, or empty. */
	public static Optional<String> checkDisplay(String rawDisplay, GangRules rules, Collection<Gang> existing,
	                                            @Nullable Integer selfId) {
		if (!rules.isDisplayNameAllowHex() && HEX_CODE.matcher(rawDisplay).find()) return Optional.of(DISPLAY_HEX);

		String visible = stripCodes(rawDisplay);
		if (visible.codePointCount(0, visible.length()) > rules.getDisplayNameMaxLength()) {
			return Optional.of(DISPLAY_TOO_LONG);
		}

		String key = key(rawDisplay);
		if (key.isEmpty()) return Optional.of(DISPLAY_TOO_LONG);

		for (String word : rules.getDisplayNameBlockedWords()) {
			String blocked = key(word);
			if (!blocked.isEmpty() && key.contains(blocked)) return Optional.of(DISPLAY_BLOCKED);
		}

		for (Gang gang : existing) {
			if (selfId != null && gang.getId() == selfId) continue;

			if (gang.getName() != null && key(gang.getName()).equals(key)) return Optional.of(DISPLAY_TAKEN);
			if (gang.getDisplayName() != null && key(gang.getDisplayName()).equals(key)) {
				return Optional.of(DISPLAY_TAKEN);
			}
		}

		return Optional.empty();
	}

}
