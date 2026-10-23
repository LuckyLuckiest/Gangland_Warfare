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
	private final boolean           glow;
	private final WitnessService    owner;

	public WitnessCall(Player criminal, String crimeId, Location at, CivilianNpc caller, List<CivilianNpc> watchers,
	                   long startedAtMillis, @Nullable UUID victimId, boolean glow, WitnessService owner) {
		this.criminal        = criminal;
		this.crimeId         = crimeId;
		this.at              = at.clone();
		this.caller          = caller;
		this.watchers        = List.copyOf(watchers);
		this.startedAtMillis = startedAtMillis;
		this.victimId        = victimId;
		this.glow            = glow;
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
		return glow;
	}

	public WitnessService owner() {
		return owner;
	}
}
