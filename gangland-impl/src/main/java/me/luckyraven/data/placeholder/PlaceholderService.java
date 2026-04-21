package me.luckyraven.data.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import me.luckyraven.Gangland;
import me.luckyraven.core.Placeholder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregating {@link Placeholder} facade. Lives in the KERNEL phase so FILE-phase consumers can constructor-inject it
 * before any contributor exists. Holds a list of registered {@link Placeholder} resolvers; CONFIG-phase beans (such as
 * {@code GanglandPlaceholder}) self-register into this list from their constructors via
 * {@link #register(Placeholder)}.
 *
 * <p>During the FILE phase the resolver list is empty, so {@link #convert(Player, String)} returns the input text
 * unchanged — exactly matching the legacy null-delegate behavior. Once the CONFIG phase finishes, the registered
 * resolvers are applied in registration order.
 *
 * <p>If PlaceholderAPI is loaded, its expansion is preferred and the internal registry is bypassed entirely.
 */
public class PlaceholderService implements Placeholder {

	private final Gangland          gangland;
	private final List<Placeholder> resolvers = new ArrayList<>();

	public PlaceholderService(Gangland gangland) {
		this.gangland = gangland;
	}

	/**
	 * Registers a {@link Placeholder} resolver. Called by CONFIG-phase contributors from their own constructors so the
	 * registry never holds a compile-time reference to any concrete resolver type.
	 */
	public void register(Placeholder resolver) {
		resolvers.add(resolver);
	}

	/**
	 * Uses PlaceholderAPI if configured to replace the text with the appropriate placeholder configured.
	 * </b>
	 * If PlaceholderAPI wasn't configured, every registered resolver is applied in registration order.
	 *
	 * @param player the player object
	 * @param text the string that contains the placeholder(s)
	 *
	 * @return the replaced placeholder text with the appropriate placeholder
	 */
	@Override
	public String convert(Player player, String text) {
		if (gangland.getPlaceholderAPIExpansion() != null) {
			if (PlaceholderAPI.containsPlaceholders(text)) {
				return PlaceholderAPI.setPlaceholders(player, text);
			}
			return text;
		}

		String result = text;
		for (Placeholder resolver : resolvers) {
			result = resolver.convert(player, result);
		}
		return result;
	}

}
