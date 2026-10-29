package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.List;

/**
 * {@code Wanted.Witnesses} of {@code copsncrooks/wanted.yml}: which crimes wait for a witness, and (0.21.2) how a cop
 * who saw one reports it.
 *
 * @param enabled      {@code false} counts every crime at once (shipped 0.15 behaviour).
 * @param crimes       crime ids gated on a witness.
 * @param deferMinutes a report whose witness is alive when the criminal logs out lands at his next join within this
 *                     many minutes (0.21.2).
 * @param cops         {@code Witnesses.Cops}: the cop eyewitness rules (0.21.2).
 * @since 0.20.0
 */
public record WitnessSettings(boolean enabled, List<String> crimes, int deferMinutes, Cops cops) {

	/**
	 * {@code Wanted.Witnesses.Cops} (0.21.2).
	 *
	 * @param reportSeconds seconds a cop eyewitness takes to radio a crime in.
	 * @param viewAngle     full width of a cop's view cone in degrees; 360 or more sees all round.
	 * @param witnessFloor  a cop this close with line of sight sees the criminal whatever the light or sneaking.
	 * @param loudCrimes    crimes that turn every cop in range toward the noise.
	 * @param reporterGlow  whether the reporting cop glows while he radios it in.
	 */
	public record Cops(int reportSeconds, double viewAngle, double witnessFloor, List<String> loudCrimes,
	                   boolean reporterGlow) {

		/** The shipped {@code Witnesses.Cops}. */
		public static final Cops DEFAULT = new Cops(3, 180.0, 6.0, List.of(), true);

		public Cops {
			loudCrimes = loudCrimes == null ? List.of() : List.copyOf(loudCrimes);
		}

		private static Cops parse(@Nullable NodeReader block) {
			if (block == null) return DEFAULT;

			List<String> loud = block.get("Loud_Crimes").asList().ofStrings().orNull();
			return new Cops(block.get("Report_Seconds").asInt().min(0).orDefault(DEFAULT.reportSeconds()),
			                block.get("View_Angle").asDouble().min(0).orDefault(DEFAULT.viewAngle()),
			                block.get("Witness_Floor").asDouble().min(0).orDefault(DEFAULT.witnessFloor()),
			                loud == null ? DEFAULT.loudCrimes() : loud,
			                block.get("Reporter_Glow").asBool().orDefault(DEFAULT.reporterGlow()));
		}
	}

	/**
	 * The shipped {@code Wanted.Witnesses}: enabled, gating Car_Theft, Assault_Civilian, (0.21.1) Kill_Civilian and
	 * (0.21.2) Assault_Cop and Kill_Cop.
	 */
	public static final WitnessSettings DEFAULT = new WitnessSettings(true, List.of("Car_Theft", "Assault_Civilian",
	                                                                                "Kill_Civilian", "Assault_Cop",
	                                                                                "Kill_Cop"), 10, Cops.DEFAULT);

	public WitnessSettings {
		crimes = crimes == null ? List.of() : List.copyOf(crimes);
		cops   = cops == null ? Cops.DEFAULT : cops;
	}

	/** {@code enabled} and {@code crimes} with the shipped Defer_Minutes and Cops block. */
	public WitnessSettings(boolean enabled, List<String> crimes) {
		this(enabled, crimes, DEFAULT.deferMinutes(), Cops.DEFAULT);
	}

	/** Reads the {@code Wanted} section; a null section, or a missing block or key, is its default. */
	public static WitnessSettings parse(@Nullable NodeReader wanted) {
		if (wanted == null) return DEFAULT;

		NodeReader block = block(wanted, "Witnesses");
		if (block == null) return DEFAULT;

		List<String> crimes = block.get("Crimes").asList().ofStrings().orNull();

		return new WitnessSettings(block.get("Enable").asBool().orDefault(DEFAULT.enabled()),
		                           crimes == null ? DEFAULT.crimes() : crimes,
		                           block.get("Defer_Minutes").asInt().min(0).orDefault(DEFAULT.deferMinutes()),
		                           Cops.parse(block(block, "Cops")));
	}

	private static @Nullable NodeReader block(NodeReader parent, String key) {
		return parent.get(key).asMapping().reader();
	}
}
