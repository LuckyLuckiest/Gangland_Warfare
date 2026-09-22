package org.luckyraven.gangland.data.gang;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Dynamic item-source rows a runtime module feeds into the core's YAML menu dialect on the core's behalf. The
 * core cannot name a module type ({@code Gang}/{@code Member}/{@code GangManager} live in the gang module), so
 * {@code GameplayConfig.inventoryRuntimeContext}'s {@code ItemSourceProvider} delegates here instead of naming
 * them directly (W54 F1, replacing the deleted {@code GangItemSourceProvider}). Modelled on
 * {@code command.extension.CommandContribution} / {@code data.placeholder.extension.PlaceholderContribution}.
 *
 * <p>Rows are returned as {@code Map<String, String>} placeholders — exactly {@code ItemSourceEntry}'s own
 * payload shape — rather than the impl-only {@code ItemSourceEntry} record itself, so the module never needs to
 * import {@code menu.multi.*}. The core wraps each returned map in {@code new ItemSourceEntry(row)}.
 */
public interface GangItemSourceContribution {

	/**
	 * @param source the item-source id from the YAML menu (e.g. {@code "gang_members"}), lower-case.
	 * @return whether this contribution answers for {@code source}.
	 */
	boolean supports(String source);

	/**
	 * @param player the viewer the menu is rendering for.
	 * @param source the item-source id, exactly as {@link #supports(String)} was asked about.
	 * @return the rows to render; empty when there is nothing to show (a real, legitimate answer, not "not
	 * mine" — {@link #supports(String)} is what decides ownership, so an empty list here is never mistaken for
	 * "ask the next contribution").
	 */
	List<Map<String, String>> entries(Player player, String source);

}
