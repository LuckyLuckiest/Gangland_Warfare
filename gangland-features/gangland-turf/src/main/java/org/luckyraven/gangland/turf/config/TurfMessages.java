package org.luckyraven.gangland.turf.config;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * Turf texts from the module's own turf/turf_messages.yml (0.17.0+). Older turf texts still go through
 * TurfMessageContract (the core Messages enum). Every lookup has an in-code fallback equal to the shipped line, so a
 * server whose turf_messages.yml predates a key still shows text.
 */
public final class TurfMessages extends LocalizedModuleYaml {

	private final TurfMessageContract legacy;
	private final CaptureSettings     capture;

	public TurfMessages(FileManager fileManager, TurfMessageContract legacy, CaptureSettings capture) {
		super(fileManager, "turf_messages");
		this.legacy  = legacy;
		this.capture = capture;
	}

	/**
	 * Raw value of {@code key} (or {@code fallback}), then every %name% of the (name, value, name, value...) pairs
	 * replaced, then coloured. Replacing before colouring keeps the colours of a value such as {@code &cRats}.
	 */
	public String text(String key, String fallback, Object... pairs) {
		String value = raw(key, fallback);
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			value = value.replace("%" + pairs[i] + "%", String.valueOf(pairs[i + 1]));
		}
		return GanglandChatUtil.color(value);
	}

	/** The one reason sentence. ownerName = the resolved owner name (GangDisplayNameResolver), may be "". */
	public String reason(CaptureEligibility.Verdict verdict, String ownerName, long now) {
		return switch (verdict.reason()) {
			case ATTACKABLE -> text("Reason.Attackable", "&aattackable");
			case COOLDOWN -> text("Reason.Cooldown", "&eshielded: just captured, attackable in %time%",
			                      "time", legacy.formatDuration(
							Math.max(1L, (verdict.untilMillis() - now + 999L) / 1000L)));
			case NO_OWNERS_ONLINE, WAKING_UP, OVER_CAP -> "";
			case OWNERS_ACTIVE -> text("Reason.Owners_Active", "&eshielded: %gang% were online in the last %grace%",
			                           "gang", ownerName,
			                           "grace", legacy.formatDuration(
							capture.getPostLogoffProtectionMinutes() * 60L));
		};
	}

}
