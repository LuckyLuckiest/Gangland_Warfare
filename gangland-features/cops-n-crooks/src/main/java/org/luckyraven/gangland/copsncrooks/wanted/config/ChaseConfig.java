package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;
import org.luckyraven.keystone.persistence.config.Severity;
import org.luckyraven.keystone.persistence.config.SourceLocation;

import java.util.ArrayList;
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
                          ChargeSheetSettings chargeSheet, BribeStarSettings bribeStars) {

	public static final ChaseConfig DEFAULT = new ChaseConfig(HeatSettings.DEFAULT, EvasionSettings.DEFAULT,
	                                                          HudSettings.DEFAULT, ChargeSheetSettings.DEFAULT,
	                                                          BribeStarSettings.DEFAULT);

	public ChaseConfig {
		if (bribeStars == null) bribeStars = BribeStarSettings.DEFAULT;
	}

	/** The 0.15.x shape, with the default bribe stars. */
	public ChaseConfig(HeatSettings heat, EvasionSettings evasion, HudSettings hud, ChargeSheetSettings chargeSheet) {
		this(heat, evasion, hud, chargeSheet, BribeStarSettings.DEFAULT);
	}

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static ChaseConfig parse(@Nullable NodeReader wantedRoot, ConfigReport report) {
		if (wantedRoot == null) return DEFAULT;

		// Kill_Combo (0.15.1) is read by ChaseConfigLoader through the settings.yml bridge (KillComboSettings)
		wantedRoot.get("Kill_Combo");

		ChaseConfig parsed = new ChaseConfig(heat(block(wantedRoot, "Heat", report)),
		                                     evasion(block(wantedRoot, "Evasion", report), report),
		                                     hud(block(wantedRoot, "Hud", report), report),
		                                     chargeSheet(block(wantedRoot, "Charge_Sheet", report)),
		                                     bribeStars(block(wantedRoot, "Bribe_Stars", report), report));

		if (parsed.evasion().dropMode() == DropMode.AUTO) {
			SourceLocation at = SourceLocation.none();
			if (!parsed.evasion().enabled()) {
				report.add(Severity.INFO, at, "Wanted.Evasion.Drop_Mode", "AUTO does nothing while evasion is off",
				           "config.note");
			}
			if (!parsed.heat().enabled()) {
				report.add(Severity.INFO, at, "Wanted.Heat.Enable",
				           "no heat ledger: every chase has 0 crimes, so no small-fry or rampage rules",
				           "config.note");
			}
		}
		return parsed;
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

		double outside  = n.get("Outside_Zone_Speed").asDouble().min(0).orDefault(d.outsideZoneSpeed());
		double maxSpeed = maxSpeed(n.get("Max_Speed"), report);
		// a 0.15.2 Outside_Zone_Speed above the 0.16 cap is silently slowed otherwise
		if (outside > maxSpeed)
			report.add(Severity.WARNING, locationOf(n.get("Max_Speed")), "Wanted.Evasion.Max_Speed",
			           "Outside_Zone_Speed " + outside + " is above Max_Speed " + maxSpeed + "; the timer is capped at " +
			           maxSpeed, "config.conflict");

		return new EvasionSettings(n.get("Enable").asBool().orDefault(d.enabled()),
		                           n.get("Lost_Sight_Seconds").asInt().min(0).orDefault(d.lostSightSeconds()),
		                           dropMode(n, report), radius.isEmpty() ? d.searchRadius() : List.copyOf(radius),
		                           drops.isEmpty() ? d.secondsToDrop() : List.copyOf(drops), outside,
		                           auto(block(n, "Auto", report), report), hideout(block(n, "Hideout", report), report),
		                           quietSpeed(block(n, "Quiet_Speed", report), report), maxSpeed);
	}

	private static HideoutSettings hideout(@Nullable NodeReader n, ConfigReport report) {
		if (n == null) return HideoutSettings.DEFAULT;

		HideoutSettings d = HideoutSettings.DEFAULT;
		return new HideoutSettings(n.get("Enable").asBool().orDefault(d.enabled()),
		                           positive(n.get("Speed"), d.speed(), "Wanted.Evasion.Hideout.Speed", report));
	}

	private static QuietSpeedSettings quietSpeed(@Nullable NodeReader n, ConfigReport report) {
		if (n == null) return QuietSpeedSettings.DEFAULT;

		QuietSpeedSettings d = QuietSpeedSettings.DEFAULT;
		return new QuietSpeedSettings(n.get("Enable").asBool().orDefault(d.enabled()),
		                              nonNegative(n.get("Per_Minute"), d.perMinute(),
		                                          "Wanted.Evasion.Quiet_Speed.Per_Minute", report),
		                              positive(n.get("Max"), d.max(), "Wanted.Evasion.Quiet_Speed.Max", report),
		                              n.get("Backup_Skip_Seconds").asInt().min(0).orDefault(d.backupSkipSeconds()));
	}

	private static double maxSpeed(NodeReader.NodeAccess access, ConfigReport report) {
		double def   = EvasionSettings.DEFAULT.maxSpeed();
		double value = access.asDouble().orDefault(def);
		if (value >= 1) return value;

		report.add(Severity.WARNING, locationOf(access), "Wanted.Evasion.Max_Speed",
		           "Max_Speed is " + value + ", below 1; using " + def, "config.range");
		return def;
	}

	private static double positive(NodeReader.NodeAccess access, double def, String path, ConfigReport report) {
		double value = access.asDouble().orDefault(def);
		if (value > 0) return value;

		report.add(Severity.WARNING, locationOf(access), path, "must be above 0, using " + def, "config.range");
		return def;
	}

	private static double nonNegative(NodeReader.NodeAccess access, double def, String path, ConfigReport report) {
		double value = access.asDouble().orDefault(def);
		if (value >= 0) return value;

		report.add(Severity.WARNING, locationOf(access), path, "must not be negative, using " + def, "config.range");
		return def;
	}

	private static BribeStarSettings bribeStars(@Nullable NodeReader n, ConfigReport report) {
		if (n == null) return BribeStarSettings.DEFAULT;

		BribeStarSettings d = BribeStarSettings.DEFAULT;
		return new BribeStarSettings(n.get("Enable").asBool().orDefault(d.enabled()),
		                             n.get("Stars").asInt().min(1).orDefault(d.stars()),
		                             n.get("Respawn_Seconds").asInt().min(0).orDefault(d.respawnSeconds()),
		                             positive(n.get("Pickup_Radius"), d.pickupRadius(),
		                                      "Wanted.Bribe_Stars.Pickup_Radius", report),
		                             n.get("Item").asString().orDefault(d.item()));
	}

	private static AutoSettings auto(@Nullable NodeReader n, ConfigReport report) {
		if (n == null) return AutoSettings.DEFAULT;

		AutoSettings d     = AutoSettings.DEFAULT;
		NodeReader   petty = block(n, "Petty", report);
		NodeReader   cold  = block(n, "Cold_Trail", report);
		NodeReader   brk   = block(n, "Clean_Break", report);
		NodeReader   mom   = block(n, "Momentum", report);
		NodeReader   learn = block(n, "Learning", report);

		AutoSettings.Petty dp          = d.petty();
		AutoSettings.Petty pettyOut    = dp;
		int                rampagePeak = n.get("Rampage_Peak_Level").asInt().min(1).orDefault(d.rampagePeakLevel());
		if (petty != null) {
			pettyOut = new AutoSettings.Petty(petty.get("Max_Crimes").asInt().min(1).orDefault(dp.maxCrimes()),
			                                  petty.get("Max_Peak_Level").asInt().min(1)
			                                       .orDefault(dp.maxPeakLevel()));
		}
		if (pettyOut.maxPeakLevel() >= rampagePeak) {
			report.add(Severity.WARNING, petty == null ? SourceLocation.none() : locationOf(petty.get("Max_Peak_Level")),
			           "Wanted.Evasion.Auto.Petty.Max_Peak_Level",
			           "PETTY and rampage overlap; the rampage lock wins", "config.conflict");
		}

		AutoSettings.ColdTrail dc      = d.coldTrail();
		AutoSettings.ColdTrail coldOut = dc;
		if (cold != null) {
			coldOut = new AutoSettings.ColdTrail(typical(cold, dc, report),
			                                     cold.get("Ratio").asDouble().min(0.01).orDefault(dc.ratio()),
			                                     cold.get("Quiet_Seconds").asInt().min(0)
			                                         .orDefault(dc.quietSeconds()));
		}

		AutoSettings.CleanBreak db     = d.cleanBreak();
		AutoSettings.CleanBreak brkOut = db;
		if (brk != null) {
			brkOut = new AutoSettings.CleanBreak(
					brk.get("Outside_Ratio").asDouble().min(0).max(1).orDefault(db.outsideRatio()),
					brk.get("Drop_Fraction").asDouble().min(0).max(1).orDefault(db.dropFraction()));
		}

		AutoSettings.Momentum dm     = d.momentum();
		AutoSettings.Momentum momOut = dm;
		if (mom != null) {
			double step   = mom.get("Step_Speed").asDouble().min(0.01).max(1).orDefault(dm.stepSpeed());
			double narrow = mom.get("Narrow_Step_Speed").asDouble().min(0.01).max(1).orDefault(dm.narrowStepSpeed());
			if (narrow > step) {
				report.add(Severity.WARNING, locationOf(mom.get("Narrow_Step_Speed")),
				           "Wanted.Evasion.Auto.Momentum.Narrow_Step_Speed",
				           "Narrow_Step_Speed is above Step_Speed, using Step_Speed", "config.conflict");
				narrow = step;
			}
			momOut = new AutoSettings.Momentum(step, narrow, mom.get("Narrow_Seen_Seconds").asInt().min(0)
			                                                    .orDefault(dm.narrowSeenSeconds()),
			                                   mom.get("Floor").asDouble().min(0.01).max(1).orDefault(dm.floor()));
		}

		return new AutoSettings(n.get("Opening_Seconds").asInt().min(0).orDefault(d.openingSeconds()),
		                        n.get("Rampage_Crimes").asInt().min(1).orDefault(d.rampageCrimes()), rampagePeak,
		                        n.get("Lock_Cool_Seconds").asInt().min(0).orDefault(d.lockCoolSeconds()),
		                        n.get("Respot_Limit").asInt().min(0).orDefault(d.respotLimit()), pettyOut, coldOut,
		                        brkOut, momOut, n.get("Repeat_Chases").asInt().min(0).max(8).orDefault(d.repeatChases()),
		                        n.get("Repeat_Window_Minutes").asInt().min(0).orDefault(d.repeatWindowMinutes()),
		                        learning(learn, d.learning(), report), rampageMinWeight(n, d, report));
	}

	private static int rampageMinWeight(NodeReader n, AutoSettings d, ConfigReport report) {
		NodeReader.NodeAccess access = n.get("Rampage_Min_Weight");
		int                   value  = access.asInt().orDefault(d.rampageMinWeight());
		if (value >= 0) return value;

		report.add(Severity.WARNING, locationOf(access), "Wanted.Evasion.Auto.Rampage_Min_Weight",
		           "is " + value + ", below 0; using " + d.rampageMinWeight(), "config.range");
		return d.rampageMinWeight();
	}

	private static List<Integer> typical(NodeReader cold, AutoSettings.ColdTrail d, ConfigReport report) {
		NodeReader.NodeAccess access = cold.get("Typical_Seconds");
		List<Integer>         raw    = access.asList().ofInts().orEmpty();
		if (raw.isEmpty()) return d.typicalSeconds();

		List<Integer> out = new ArrayList<>(raw.size());
		for (int i = 0; i < raw.size(); i++) {
			int value = raw.get(i);
			if (value < 5) {
				int fallback = i == 0 ? d.typicalSeconds().get(0) : out.get(i - 1);
				report.add(Severity.WARNING, locationOf(access), "Wanted.Evasion.Auto.Cold_Trail.Typical_Seconds",
				           "entry " + (i + 1) + " is " + value + ", below 5; using " + fallback, "config.range");
				value = fallback;
			}
			out.add(value);
		}
		return List.copyOf(out);
	}

	private static AutoSettings.Learning learning(@Nullable NodeReader n, AutoSettings.Learning d,
	                                              ConfigReport report) {
		if (n == null) return d;

		NodeReader.NodeAccess access = n.get("Escape_Rate");
		List<Double>          raw    = access.asList().ofDoubles().orEmpty();
		List<Double>          rates  = new ArrayList<>(raw.size());
		for (int i = 0; i < raw.size(); i++) {
			double value = raw.get(i);
			if (value < 0 || value > 1) {
				double clamped = Math.max(0, Math.min(1, value));
				report.add(Severity.WARNING, locationOf(access), "Wanted.Evasion.Auto.Learning.Escape_Rate",
				           "entry " + (i + 1) + " is " + value + ", outside 0 to 1; using " + clamped,
				           "config.range");
				value = clamped;
			}
			rates.add(value);
		}

		return new AutoSettings.Learning(n.get("Enable").asBool().orDefault(d.enable()),
		                                 rates.isEmpty() ? d.escapeRate() : List.copyOf(rates),
		                                 n.get("Prior_Chases").asInt().min(1).orDefault(d.priorChases()),
		                                 n.get("Decay_Per_Chase").asDouble().min(0.5).max(1)
		                                  .orDefault(d.decayPerChase()),
		                                 n.get("Habitual_Escaper_Delta").asDouble().min(0.05).max(1)
		                                  .orDefault(d.habitualEscaperDelta()),
		                                 n.get("Habit_Time_Strength").asDouble().min(0).max(2)
		                                  .orDefault(d.habitTimeStrength()),
		                                 n.get("Min_Time_Factor").asDouble().min(0.1).max(1)
		                                  .orDefault(d.minTimeFactor()),
		                                 n.get("Max_Time_Factor").asDouble().min(1).max(4)
		                                  .orDefault(d.maxTimeFactor()),
		                                 n.get("Min_Chase_Seconds").asInt().min(0).orDefault(d.minChaseSeconds()),
		                                 n.get("Min_Seconds_Between_Outcomes").asInt().min(0)
		                                  .orDefault(d.minSecondsBetweenOutcomes()),
		                                 n.get("Forget_After_Days").asInt().min(1).orDefault(d.forgetAfterDays()));
	}

	private static SourceLocation locationOf(NodeReader.NodeAccess access) {
		return access.node() != null ? access.node().location() : SourceLocation.none();
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
		           "unknown Drop_Mode \"" + text + "\", using ONE_STAR (ONE_STAR, ALL_STARS or AUTO)", "config.enum");
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
