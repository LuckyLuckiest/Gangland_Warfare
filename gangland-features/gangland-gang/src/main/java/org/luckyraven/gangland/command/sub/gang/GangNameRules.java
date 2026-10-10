package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.ChatColor;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.Collection;
import java.util.Optional;
import java.util.regex.Pattern;

/** Validates a gang name for create and rename. Names are the command key, so they are always unique. */
public final class GangNameRules {

	static final String INVALID = "Names.Invalid";
	static final String TAKEN   = "Names.Taken";

	private GangNameRules() {
	}

	/** A gang name as the player sees it: colour codes removed. Names are stored raw, so lookups compare this form. */
	public static String plain(String raw) {
		return ChatColor.stripColor(ChatUtil.color(raw));
	}

	/** The player-facing refusal for a key returned by {@link #check}, from gang_messages.yml. */
	public static String refusal(GangMessages messages, String key, String name) {
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

			if (plain(gang.getName()).equalsIgnoreCase(name)) return Optional.of(TAKEN);
		}

		return Optional.empty();
	}

}
