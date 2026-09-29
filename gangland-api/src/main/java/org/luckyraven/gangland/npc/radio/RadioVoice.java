package org.luckyraven.gangland.npc.radio;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.npc.AbstractNpc;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.Map;

/**
 * How one squad-radio feature (cop radio, gang shouts) names and identifies its members. {@link SquadRadio} calls
 * this for every line it speaks; nothing here is cached.
 *
 * @since 1.13.0
 */
public interface RadioVoice {

	/** The member's callsign as spoken on the radio, e.g. {@code "SWAT-17"}. */
	String callsign(AbstractNpc npc);

	/** The player the squad is hunting, or {@code null} when it has none (or the target isn't a player). */
	@Nullable LivingEntity hunted(NpcSquad squad);

	/**
	 * Extra placeholders merged into every line spoken for {@code squad}, e.g. {@code %faction%}. Explicit
	 * placeholders a call supplies of its own (such as {@code %member%} on an order) win on a name clash.
	 */
	default Map<String, String> extras(NpcSquad squad) {
		return Map.of();
	}
}
