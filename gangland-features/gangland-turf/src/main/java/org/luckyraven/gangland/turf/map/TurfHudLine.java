package org.luckyraven.gangland.turf.map;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfGeometry;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.state.TurfState;

import java.util.Objects;

/**
 * The action-bar turf line and its nearest-turf twin, built from the texts in turf_messages.yml. Pure: no Bukkit
 * world, no scheduler; TurfHudTask decides who gets a line and when.
 */
public final class TurfHudLine {

	private TurfHudLine() {
	}

	/**
	 * The line for a player standing inside {@code turf}. ownerName is the owner's resolved name ("" when unclaimed),
	 * challengerName the challenger's resolved name (only read while the turf is CONTESTING). viewerGangless is true for
	 * any player without a gang, whatever the turf's owner, because a gangless player can never capture.
	 */
	public static String inside(TurfMessages text, Turf turf, TurfRelation relation, boolean viewerGangless,
	                            String ownerName,
	                            @Nullable TurfRuntimeState state, @Nullable String challengerName,
	                            CaptureEligibility.Verdict verdict, long now) {
		String owner = switch (relation) {
			case OWN -> text.text("Hud.Owner_Own", "&ayour turf");
			case ALLY -> text.text("Hud.Owner_Ally", "&b%gang% turf", "gang", ownerName);
			case RIVAL -> text.text("Hud.Owner_Rival", "&c%gang% turf", "gang", ownerName);
			case GANGLESS -> text.text("Hud.Owner_Gangless", "&e%gang% turf", "gang", ownerName);
			case UNCLAIMED -> text.text("Hud.Owner_Unclaimed", "&7unclaimed");
		};

		String reason;
		if (viewerGangless || relation == TurfRelation.GANGLESS) {
			reason = text.text("Hud.Gangless", "&ejoin a gang to capture");
		} else if (isContesting(state)) {
			reason = underAttack(text, state, challengerName);
		} else if (relation == TurfRelation.OWN || relation == TurfRelation.ALLY) {
			reason = text.text("Hud.Quiet", "&aquiet");
		} else {
			reason = text.reason(verdict, ownerName, now);
		}

		return text.text("Hud.Inside", "&f%turf% &7- %owner% &7- %reason%",
		                 "turf", turf.getDisplayName(), "owner", owner, "reason", reason);
	}

	/** The nearest-turf line; null when the world has no turf. */
	public static @Nullable String nearest(TurfMessages text, @Nullable TurfGeometry.Nearest nearest) {
		if (nearest == null) {
			return null;
		}
		return text.text("Hud.Nearest", "&7Nearest turf: &f%turf% &7%distance%m %direction%",
		                 "turf", nearest.turf().getDisplayName(),
		                 "distance", nearest.distance(),
		                 "direction", nearest.direction());
	}

	/** True while a capture is in flight on the turf (the state the HUD and the placeholders call "under attack"). */
	public static boolean isContesting(@Nullable TurfRuntimeState state) {
		return state != null && state.getState() == TurfState.CONTESTING;
	}

	private static String underAttack(TurfMessages text, TurfRuntimeState state, @Nullable String challengerName) {
		return text.text("Hud.Under_Attack", "&cunder attack by %gang%, %percent%%",
		                 "gang", Objects.requireNonNullElse(challengerName, ""),
		                 "percent", Math.round(state.getCaptureProgress()));
	}
}
