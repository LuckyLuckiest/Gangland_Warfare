package org.luckyraven.gangland.copsncrooks.wanted.hud;

import org.bukkit.entity.Player;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.Map;

/**
 * One wanted-level title event: its title and subtitle, and the fades in ticks. Every wanted title goes through here.
 *
 * @since 0.16.1
 */
public record TitleCue(boolean enabled, String title, String subtitle, int fadeIn, int stay, int fadeOut) {

	/** Gained and lost: the star row, then the card. Blank Star_Card leaves the row alone, so a star is still named. */
	public static final TitleCue DEFAULT = new TitleCue(true, "", "%stars% %card%", 5, 20, 5);

	/** Escaped: the level is 0 by now, so the star row would be empty; the card alone. */
	public static final TitleCue ESCAPED = new TitleCue(true, "", "%card%", 5, 20, 5);

	/** Fills the placeholders, then sends unless disabled or both lines are blank. Returns whether it sent. */
	public boolean send(Player player, Map<String, String> placeholders) {
		if (!enabled) return false;

		String resolvedTitle    = fill(title, placeholders);
		String resolvedSubtitle = fill(subtitle, placeholders);
		if (resolvedTitle.isBlank() && resolvedSubtitle.isBlank()) return false;

		ChatUtil.sendTitle(player, resolvedTitle, resolvedSubtitle, fadeIn, stay, fadeOut);
		return true;
	}

	private static String fill(String text, Map<String, String> values) {
		String out = text == null ? "" : text;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			out = out.replace("%" + entry.getKey() + "%", entry.getValue());
		}
		return out.strip();
	}
}
