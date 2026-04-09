package me.luckyraven.data.placeholder;

import lombok.Setter;
import me.clip.placeholderapi.PlaceholderAPI;
import me.luckyraven.Gangland;
import me.luckyraven.data.placeholder.worker.GanglandPlaceholder;
import me.luckyraven.util.Placeholder;
import org.bukkit.entity.Player;

public class PlaceholderService implements Placeholder {

	private final Gangland gangland;

	/**
	 * Set by {@code WiringConfig.ganglandPlaceholder()} after the {@link GanglandPlaceholder} bean is built. Stays
	 * {@code null} until the CONFIG phase wires it in — {@link #convert(Player, String)} null-checks before use so any
	 * FILE-phase code that runs through PlaceholderService before then just gets the unmodified text back.
	 */
	@Setter
	private GanglandPlaceholder placeholder;

	public PlaceholderService(Gangland gangland) {
		this.gangland = gangland;
	}

	/**
	 * Uses PlaceholderAPI if configured to replace the text with the appropriate placeholder configured.
	 * </b>
	 * If PlaceholderAPI wasn't configured, then it is replaced with the default placeholder handled by the plugin.
	 *
	 * @param player the player object
	 * @param text the string that contains the placeholder(s)
	 *
	 * @return the replaced placeholder text with the appropriate placeholder
	 */
	@Override
	public String convert(Player player, String text) {
		if (gangland.getPlaceholderAPIExpansion() != null) {
			if (PlaceholderAPI.containsPlaceholders(text)) return PlaceholderAPI.setPlaceholders(player, text);
		} else {
			if (placeholder == null) return text;
			if (placeholder.containsPlaceholder(text)) return placeholder.replacePlaceholder(player, text);
		}

		return text;
	}

}
