package org.luckyraven.gangland.npc;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.List;

/**
 * The blood of a hurt NPC ({@code Field_Care.Hurt}): a block-crack burst of redstone-block particles at random body
 * spots, denser the more hurt the NPC is.
 *
 * @param particle the particle type name; {@code BLOCK_CRACK} (also {@code BLOCK}) is tried first with redstone-block
 *                 data, then red dust, whatever the server version.
 * @param count    particles per spot at the hurt threshold (1-50); up to double that at death's door.
 * @param spots    the spots a burst picks one or two from.
 * @since 0.13.0
 */
public record BleedSettings(String particle, int count, List<BleedSpot> spots) {

	public static final BleedSettings DEFAULT = new BleedSettings("BLOCK_CRACK", 6, List.of(BleedSpot.values()));

	/** Reads {@code Bleed_Particle}, {@code Bleed_Count} and {@code Bleed_Spots} from {@code node} over {@code defaults}. */
	public static BleedSettings read(@Nullable NodeReader node, ConfigReport report, BleedSettings defaults) {
		if (node == null) return defaults;
		List<String> names = node.get("Bleed_Spots").asList().ofStrings().orEmpty();
		return new BleedSettings(
				node.get("Bleed_Particle").asString().orDefault(defaults.particle()),
				TacticsConfig.readClampedInt(node, report, "Bleed_Count", defaults.count(), 1, 50),
				names.isEmpty() ? defaults.spots() : BleedSpot.parse(names));
	}
}
