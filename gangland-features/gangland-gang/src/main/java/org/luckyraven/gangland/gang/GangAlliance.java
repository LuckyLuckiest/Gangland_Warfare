package org.luckyraven.gangland.gang;

import java.util.Objects;

/**
 * One direction of an alliance: {@code gang} lists {@code ally}. Equality is the gang pair plus the date, not the
 * friendly-fire flag, so switching the flag never changes which set entry this is.
 */
public final class GangAlliance {

	private final Gang gang;
	private final Gang ally;
	private final long since;

	/** Whether this side lets the ally hurt it ({@code /glw gang ally pvp}); both sides must say yes. */
	private volatile boolean friendlyFire;

	public GangAlliance(Gang gang, Gang ally, long since) {
		this(gang, ally, since, false);
	}

	public GangAlliance(Gang gang, Gang ally, long since, boolean friendlyFire) {
		this.gang         = gang;
		this.ally         = ally;
		this.since        = since;
		this.friendlyFire = friendlyFire;
	}

	public Gang gang() {
		return gang;
	}

	public Gang ally() {
		return ally;
	}

	public long since() {
		return since;
	}

	public boolean isFriendlyFire() {
		return friendlyFire;
	}

	public void setFriendlyFire(boolean friendlyFire) {
		this.friendlyFire = friendlyFire;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof GangAlliance other)) return false;

		return since == other.since && Objects.equals(gang, other.gang) && Objects.equals(ally, other.ally);
	}

	@Override
	public int hashCode() {
		return Objects.hash(gang, ally, since);
	}

	@Override
	public String toString() {
		return "GangAlliance[gang=" + gang + ", ally=" + ally + ", since=" + since + "]";
	}
}
