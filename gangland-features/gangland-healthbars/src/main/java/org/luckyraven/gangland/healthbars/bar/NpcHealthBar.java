package org.luckyraven.gangland.healthbars.bar;

import com.cryptomorin.xseries.XAttribute;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.HologramTrait;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.healthbars.config.HealthBarSettings;

import java.util.Objects;

/**
 * Draws the bar as one Citizens {@link HologramTrait} line. A plain static utility, never scanned as a bean, so its
 * signatures may name Citizens types (same reasoning as civilians' {@code CitizensNpcs}).
 *
 * <p>Layout (Citizens 2.0.43 {@code HologramTrait.getHeight}): lines stack upward from the nameplate, index 0 lowest.
 * A cop hides its nameplate and carries its callsign as line 0, so the bar inserted at index 0 sits directly under
 * the callsign and above the head; an NPC with no lines (a civilian) gets the bar just above its own nameplate.
 * {@code insertLine} respawns every line of the NPC once (a single blink when the bar appears), so later hits only
 * {@code setLine}. After {@code removeLine(0)} the callsign drops back into place on the NPC's next move.
 */
public final class NpcHealthBar {

	/** Transient NPC data holding the bar text this module drew at line 0; absent while no bar is shown. */
	static final String BAR_KEY = "gangland-healthbar";

	private NpcHealthBar() {
	}

	/** Redraws {@code entity}'s bar, if it is a transient, unprotected, spawned Citizens NPC. */
	public static void update(Entity entity, HealthBarSettings settings) {
		NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
		if (npc == null || !npc.isSpawned() || npc.isProtected() || !isTransient(npc)) return;

		HologramTrait hologram = npc.getOrAddTrait(HologramTrait.class);
		show(hologram, npc.data(), settings.enabled() ? barFor(npc.getEntity(), settings) : null);
	}

	/**
	 * Puts {@code bar} at line 0 (inserted once, then updated in place), or removes the line this module drew when
	 * {@code bar} is null. Lines other than the module's own are never touched.
	 */
	static void show(HologramTrait hologram, MetadataStore data, @Nullable String bar) {
		boolean shown = data.has(BAR_KEY) && !hologram.getLines().isEmpty();

		if (bar == null) {
			if (shown) hologram.removeLine(0);
			data.remove(BAR_KEY);
			return;
		}

		if (!shown) {
			hologram.insertLine(0, bar);
		} else if (!Objects.equals(data.get(BAR_KEY), bar)) {
			hologram.setLine(0, bar);
		}
		data.set(BAR_KEY, bar);
	}

	/** The bar for a hurt living entity; null at full health or once dead. */
	private static @Nullable String barFor(@Nullable Entity entity, HealthBarSettings settings) {
		if (!(entity instanceof LivingEntity living) || living.isDead()) return null;

		AttributeInstance attribute = living.getAttribute(XAttribute.MAX_HEALTH.get());
		double            max       = attribute != null ? attribute.getValue() : living.getHealth();
		double            health    = living.getHealth();
		if (health <= 0 || health >= max) return null;

		return settings.render(health, max);
	}

	/** Gangland's own NPCs are never saved; saved NPCs belong to the server owner (and their /npc holograms). */
	private static boolean isTransient(NPC npc) {
		return !Boolean.parseBoolean(String.valueOf(npc.data().get(NPC.Metadata.SHOULD_SAVE, true)));
	}
}
