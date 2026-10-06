package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;
import org.luckyraven.keystone.persistence.config.Severity;
import org.luckyraven.keystone.persistence.config.SourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything in {@code copsncrooks/wanted.yml}. {@link #DEFAULT} equals the shipped file; {@link #parse} reads a file over it
 * key by key, so a server whose file predates a key still gets the shipped value.
 *
 * @since 0.15.0
 */
public record ChaseConfig(HeatSettings heat, EvasionSettings evasion, HudSettings hud,
                          ChargeSheetSettings chargeSheet) {

	public static final ChaseConfig DEFAULT = new ChaseConfig(HeatSettings.DEFAULT, EvasionSettings.DEFAULT,
	                                                          HudSettings.DEFAULT, ChargeSheetSettings.DEFAULT);

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static ChaseConfig parse(@Nullable NodeReader wantedRoot, ConfigReport report) {
		if (wantedRoot == null) return DEFAULT;

		// Kill_Combo (0.15.1) is read by ChaseConfigLoader through the settings.yml bridge (KillComboSettings)
		wantedRoot.get("Kill_Combo");

		return new ChaseConfig(heat(block(wantedRoot, "Heat", report)),
		                       evasion(block(wantedRoot, "Evasion", report), report),
		                       hud(block(wantedRoot, "Hud", report), report),
		                       chargeSheet(block(wantedRoot, "Charge_Sheet", report)));
	}

	private static @Nullable NodeReader block(@Nullable NodeReader parent, String key, ConfigReport report) {
		if (parent == null) return null;

		MappingNode mapping = parent.get(key).asMapping().orNull();
		return mapping == null ? null : NodeReader.of(mapping, report);
	}

	private static HeatSettings heat(@Nullable NodeReader n) {
		if (n == null) return HeatSettings.DEFAULT;

		HeatSettings  d          = HeatSettings.DEFAULT;
		List<Integer> thresholds = n.get("Star_Thresholds").asList().ofInts().orEmpty();

		Map<String, Integer> crimes    = new LinkedHashMap<>(d.crimeWeights());
		NodeReader           crimeNode = n.get("Crimes").asMapping().reader();
		if (crimeNode != null) {
			for (String id : crimeNode.keys()) {
				crimes.put(id, crimeNode.get(id).asInt().min(0).orDefault(d.weightOf(id)));
			}
		}

		return new HeatSettings(n.get("Enable").asBool().orDefault(d.enabled()),
		                        thresholds.isEmpty() ? d.starThresholds() : List.copyOf(thresholds),
		                        n.get("Streak_Bonus").asDouble().min(0).orDefault(d.streakBonus()),
		                        n.get("Seen_By_Cop_Multiplier").asDouble().min(0).orDefault(d.seenByCopMultiplier()),
		                        n.get("Turf_War_Multiplier").asDouble().min(0).orDefault(d.turfWarMultiplier()),
		                        n.get("Assault_Repeat_Seconds").asInt().min(0).orDefault(d.assaultRepeatSeconds()),
		                        Map.copyOf(crimes));
	}

	private static EvasionSettings evasion(@Nullable NodeReader n, ConfigReport report) {
		if (n == null) return EvasionSettings.DEFAULT;

		EvasionSettings d      = EvasionSettings.DEFAULT;
		List<Integer>   radius = n.get("Search_Radius").asList().ofInts().orEmpty();
		List<Integer>   drops  = n.get("Seconds_To_Drop").asList().ofInts().orEmpty();

		return new EvasionSettings(n.get("Enable").asBool().orDefault(d.enabled()),
		                           n.get("Lost_Sight_Seconds").asInt().min(0).orDefault(d.lostSightSeconds()),
		                           dropMode(n, report), radius.isEmpty() ? d.searchRadius() : List.copyOf(radius),
		                           drops.isEmpty() ? d.secondsToDrop() : List.copyOf(drops),
		                           n.get("Outside_Zone_Speed").asDouble().min(0).orDefault(d.outsideZoneSpeed()));
	}

	private static DropMode dropMode(NodeReader n, ConfigReport report) {
		NodeReader.NodeAccess access = n.get("Drop_Mode");
		String                text   = access.asString().orNull();
		if (text == null) return EvasionSettings.DEFAULT.dropMode();

		for (DropMode mode : DropMode.values()) {
			if (mode.name().equalsIgnoreCase(text.trim())) return mode;
		}

		SourceLocation at = access.node() != null ? access.node().location() : SourceLocation.none();
		report.add(Severity.WARNING, at, "Wanted.Evasion.Drop_Mode",
		           "unknown Drop_Mode \"" + text + "\", using ONE_STAR (ONE_STAR or ALL_STARS)", "config.enum");
		return DropMode.ONE_STAR;
	}

	private static HudSettings hud(@Nullable NodeReader n, ConfigReport report) {
		if (n == null) return HudSettings.DEFAULT;

		HudSettings d     = HudSettings.DEFAULT;
		NodeReader  siren = block(n, "Siren", report);
		NodeReader  ring  = block(n, "Zone_Ring", report);

		return new HudSettings(enabled(block(n, "Boss_Bar", report), d.bossBar()),
		                       enabled(block(n, "Star_Card", report), d.starCard()),
		                       enabled(block(n, "Title", report), d.title()), enabled(siren, d.siren()),
		                       siren == null ? d.sirenSound() : siren.get("Sound").asString().orDefault(d.sirenSound()),
		                       siren == null ? d.sirenVolume()
		                                     : (float) siren.get("Volume").asDouble().min(0).orDefault(d.sirenVolume()),
		                       siren == null ? d.sirenPitch()
		                                     : (float) siren.get("Pitch").asDouble().min(0).orDefault(d.sirenPitch()),
		                       enabled(ring, d.zoneRing()),
		                       ring == null ? d.zoneParticle()
		                                    : ring.get("Particle").asString().orDefault(d.zoneParticle()),
		                       ring == null ? d.zonePoints()
		                                    : ring.get("Points").asInt().min(3).orDefault(d.zonePoints()),
		                       enabled(block(n, "Compass", report), d.compass()));
	}

	private static boolean enabled(@Nullable NodeReader n, boolean def) {
		return n == null ? def : n.get("Enable").asBool().orDefault(def);
	}

	private static ChargeSheetSettings chargeSheet(@Nullable NodeReader n) {
		if (n == null) return ChargeSheetSettings.DEFAULT;

		ChargeSheetSettings d = ChargeSheetSettings.DEFAULT;
		return new ChargeSheetSettings(n.get("Enable").asBool().orDefault(d.enabled()),
		                               n.get("Base").asDouble().min(0).orDefault(d.base()),
		                               n.get("Per_Wanted_Level").asDouble().min(0).orDefault(d.perWantedLevel()),
		                               n.get("Maximum").asDouble().min(0).orDefault(d.maximum()),
		                               n.get("Seconds_Per_Unpaid").asDouble().min(0).orDefault(d.secondsPerUnpaid()),
		                               n.get("Max_Extra_Seconds").asInt().min(0).orDefault(d.maxExtraSeconds()));
	}
}
