package org.luckyraven.gangland.civilians.witness;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;

import java.util.List;
import java.util.UUID;

/**
 * One running 911 call: who is being reported, for what, where, who is making the call and who else saw it.
 *
 * @since 0.20.0
 */
public final class WitnessCall {

	private final Player            criminal;
	private final String            crimeId;
	private final Location          at;
	private final CivilianNpc       caller;
	private final List<CivilianNpc> watchers;
	private final long              startedAtMillis;
	private final @Nullable UUID    victimId;
	private final WitnessRules      rules;
	private final WitnessService    owner;
	/** The countdown second last shown to the criminal; -1 before the first (0.21.1). */
	private int                     shownSeconds = -1;

	public WitnessCall(Player criminal, String crimeId, Location at, CivilianNpc caller, List<CivilianNpc> watchers,
	                   long startedAtMillis, @Nullable UUID victimId, WitnessRules rules, WitnessService owner) {
		this.criminal        = criminal;
		this.crimeId         = crimeId;
		this.at              = at.clone();
		this.caller          = caller;
		this.watchers        = List.copyOf(watchers);
		this.startedAtMillis = startedAtMillis;
		this.victimId        = victimId;
		this.rules           = rules;
		this.owner           = owner;
	}

	public Player criminal() {
		return criminal;
	}

	public String crimeId() {
		return crimeId;
	}

	public Location at() {
		return at.clone();
	}

	public CivilianNpc caller() {
		return caller;
	}

	public List<CivilianNpc> watchers() {
		return watchers;
	}

	public long startedAtMillis() {
		return startedAtMillis;
	}

	public @Nullable UUID victimId() {
		return victimId;
	}

	/** Whether the caller glows while the call runs ({@code Witness.Caller_Glow}). */
	public boolean glow() {
		return rules.callerGlow();
	}

	/** The {@code Witness} rules in force when the call started (0.21.1: notice, phone, particle). */
	public WitnessRules rules() {
		return rules;
	}

	public WitnessService owner() {
		return owner;
	}

	public int shownSeconds() {
		return shownSeconds;
	}

	public void shownSeconds(int seconds) {
		this.shownSeconds = seconds;
	}
}
