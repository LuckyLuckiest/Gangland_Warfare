package org.luckyraven.gangland.civilians.witness;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.List;

/**
 * {@code Witness} block of {@code npc/civilians.yml}: how far a civilian sees a crime and how a 911 call runs.
 *
 * @param viewAngle             full width, in degrees, of the cone a civilian sees a crime in (0.21.1); 360 = all round.
 * @param noticeSeconds         seconds the caller stares at the criminal before turning away to dial (0.21.1).
 * @param noticeParticle        particle (XParticle name) above the caller when it notices; "" = none (0.21.1).
 * @param phoneItem             item (XMaterial name) the caller holds while dialling; "" = its hand as it is (0.21.1).
 * @param intimidateRange       blocks within which the criminal's right-click stops the call; 0 = off (0.21.1).
 * @param scaredCooldownSeconds seconds a caller stopped by the criminal (hit, scared, intimidated) cannot call again (0.21.1).
 * @param loudCrimes            crime ids that make every civilian in range turn to the noise before the cone applies;
 *                              none by default, a kill is loud only through a gunshot (0.21.1).
 * @param loudShotSeconds       a crime within this many seconds of the criminal's own shot is loud too; 0 = off (0.21.1).
 * @param loudWeaponTypes       Bartizan weapon categories whose shot is noise for Loud_Shot_Seconds (0.21.1).
 * @since 0.20.0
 */
public record WitnessRules(boolean enabled, double range, int callSeconds, boolean callerGlow, double scareRange,
                           double scareAngle, boolean reportAssault, int assaultRepeatSeconds, double viewAngle,
                           int noticeSeconds, String noticeParticle, String phoneItem, double intimidateRange,
                           int scaredCooldownSeconds, List<String> loudCrimes, int loudShotSeconds,
                           List<String> loudWeaponTypes) {

	/** The shipped {@code Witness} block. */
	public static final WitnessRules DEFAULT = new WitnessRules(true, 24.0, 8, true, 16.0, 12.0, true, 10, 180.0, 1,
	                                                            "ANGRY_VILLAGER", "CLOCK", 4.0, 60,
	                                                            List.of(), 3,
	                                                            List.of("GUN", "THROWABLE"));

	public WitnessRules {
		noticeParticle = noticeParticle == null ? "" : noticeParticle;
		phoneItem      = phoneItem == null ? "" : phoneItem;
		loudCrimes      = loudCrimes == null ? List.of() : List.copyOf(loudCrimes);
		loudWeaponTypes = loudWeaponTypes == null ? List.of() : List.copyOf(loudWeaponTypes);
	}

	/** Pre-0.21.1 8-argument constructor, kept for existing callers: the 0.21.1 keys take their {@link #DEFAULT}. */
	public WitnessRules(boolean enabled, double range, int callSeconds, boolean callerGlow, double scareRange,
	                    double scareAngle, boolean reportAssault, int assaultRepeatSeconds) {
		this(enabled, range, callSeconds, callerGlow, scareRange, scareAngle, reportAssault, assaultRepeatSeconds,
		     DEFAULT.viewAngle(), DEFAULT.noticeSeconds(), DEFAULT.noticeParticle(), DEFAULT.phoneItem(),
		     DEFAULT.intimidateRange(), DEFAULT.scaredCooldownSeconds(), DEFAULT.loudCrimes(), DEFAULT.loudShotSeconds(),
		     DEFAULT.loudWeaponTypes());
	}

	/** Reads the {@code Witness} block of civilians.yml; a null reader, missing block or missing key is its default. */
	public static WitnessRules parse(@Nullable NodeReader root) {
		if (root == null) return DEFAULT;

		NodeReader block = root.get("Witness").asMapping().reader();
		if (block == null) return DEFAULT;

		WitnessRules d     = DEFAULT;
		List<String> loud  = block.get("Loud_Crimes").asList().ofStrings().orNull();
		List<String> types = block.get("Loud_Weapon_Types").asList().ofStrings().orNull();
		return new WitnessRules(
				block.get("Enable").asBool().orDefault(d.enabled()),
				block.get("Range").asDouble().min(0).orDefault(d.range()),
				block.get("Call_Seconds").asInt().min(0).orDefault(d.callSeconds()),
				block.get("Caller_Glow").asBool().orDefault(d.callerGlow()),
				block.get("Scare_Range").asDouble().min(0).orDefault(d.scareRange()),
				block.get("Scare_Angle").asDouble().min(0).orDefault(d.scareAngle()),
				block.get("Report_Assault").asBool().orDefault(d.reportAssault()),
				block.get("Assault_Repeat_Seconds").asInt().min(0).orDefault(d.assaultRepeatSeconds()),
				block.get("View_Angle").asDouble().min(0).orDefault(d.viewAngle()),
				block.get("Notice_Seconds").asInt().min(0).orDefault(d.noticeSeconds()),
				block.get("Notice_Particle").asString().orDefault(d.noticeParticle()),
				block.get("Phone_Item").asString().orDefault(d.phoneItem()),
				block.get("Intimidate_Range").asDouble().min(0).orDefault(d.intimidateRange()),
				block.get("Scared_Cooldown_Seconds").asInt().min(0).orDefault(d.scaredCooldownSeconds()),
				loud == null ? d.loudCrimes() : loud,
				block.get("Loud_Shot_Seconds").asInt().min(0).orDefault(d.loudShotSeconds()),
				types == null ? d.loudWeaponTypes() : types);
	}
}
