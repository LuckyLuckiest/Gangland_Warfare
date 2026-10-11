package org.luckyraven.gangland.turf.npc;

import org.bukkit.ChatColor;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * P5.3 gang short code: 1-4 characters, upper case, never contains {@code '-'}. Shared by the Quartermaster nameplate
 * line and (Gang &amp; Turf II, W3-B) the guard callsign, so there is one formatter.
 */
public final class GangTag {

	private GangTag() {
	}

	public static String of(@Nullable String name, int gangId) {
		String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name == null ? "" : name));
		List<String> words = Arrays.stream(plain.split("[^A-Za-z0-9]+")).filter(word -> !word.isEmpty()).toList();

		String tag;
		if (words.size() >= 3) {
			StringBuilder initials = new StringBuilder();
			for (int i = 0; i < 4 && i < words.size(); i++) initials.append(words.get(i).charAt(0));
			tag = initials.toString();
		} else {
			String joined = String.join("", words);
			tag = joined.substring(0, Math.min(3, joined.length()));
		}
		tag = tag.toUpperCase(Locale.ROOT);
		if (tag.isEmpty()) {
			tag = ("#" + gangId);
			tag = tag.substring(0, Math.min(4, tag.length()));
		}
		return tag;
	}
}
