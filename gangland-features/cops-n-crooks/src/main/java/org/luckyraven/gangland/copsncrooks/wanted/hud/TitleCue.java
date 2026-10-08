package org.luckyraven.gangland.copsncrooks.wanted.hud;

import org.bukkit.entity.Player;

import java.util.Map;

/**
 * One wanted-level title event: its title and subtitle, and the fades in ticks. Stub for the red stage (0.16.1): sends
 * nothing yet.
 *
 * @since 0.16.1
 */
public record TitleCue(boolean enabled, String title, String subtitle, int fadeIn, int stay, int fadeOut) {

	public static final TitleCue DEFAULT = new TitleCue(true, "", "%card%", 5, 20, 5);

	/** Fills the placeholders, then sends unless disabled or both lines are blank. Returns whether it sent. */
	public boolean send(Player player, Map<String, String> placeholders) {
		return false;
	}
}
