package org.luckyraven.gangland.turf.listener;

import org.bukkit.ChatColor;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.gangland.gang.Gang;

/**
 * Small shared helper: picks the best available name for a gang across every turf-facing UI.
 *
 * <p>{@link Gang#getDisplayName()} is the gang's <i>optional</i> decorated alias and stays empty until an admin sets
 * one. {@link Gang#getDisplayNameString()} already handles that case — it returns the decorated alias if present, else
 * the raw gang {@code name}. We defer to it, then fall back to the raw name and finally a {@code Gang #<id>} label so
 * nothing ever renders as blank "Controlled by " / "is capturing" lines.
 */
public final class GangDisplayNameResolver {

	private GangDisplayNameResolver() {
	}

	public static String resolve(Gang gang) {
		if (gang == null) {
			return "Unknown";
		}
		String computed = safeName(gang.getDisplayNameString());
		if (computed != null) {
			return computed;
		}
		String raw = safeName(gang.getName());
		if (raw != null) {
			return raw;
		}
		return "Gang #" + gang.getId();
	}

	private static String safeName(String value) {
		if (value == null) {
			return null;
		}
		String stripped = ChatColor.stripColor(ChatUtil.color(value)).trim();
		if (stripped.isEmpty()) {
			return null;
		}
		return value;
	}
}
