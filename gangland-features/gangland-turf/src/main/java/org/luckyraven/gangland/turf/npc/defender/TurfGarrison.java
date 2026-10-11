package org.luckyraven.gangland.turf.npc.defender;

import org.luckyraven.gangland.civilians.npc.FactionSquads;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.npc.defender.GarrisonPosts.Post;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * Per contested turf: the wave state ({@code owed}, last loss, spawn delay), the hidden posts, the callsign counter
 * and the reserve access. The reserve is read and spent through the suppliers the owner of the stock hands in, so a
 * guard is taken out of it only once it has actually spawned. Ticked by {@link TurfDefenderDeployer}.
 */
public final class TurfGarrison {

	final int                turfId;
	final CuboidRegion       region;
	final TurfDefenderConfig config;
	final String             tag;
	final IntConsumer        waveNotice;
	private final IntSupplier      stock;
	private final IntUnaryOperator consume;

	/** Guards still to spawn: the first wave, then one per guard lost. */
	int           owed;
	long          lastLossAt   = -1L;
	/** When the wave started waiting for a hidden post; -1 when it is not waiting. */
	long          delayedSince = -1L;
	int           nextNumber   = 1;
	List<Post>    posts;
	FactionSquads squads;
	boolean       standingDown;
	long          standDownAt;
	IntConsumer   onReturn;

	public TurfGarrison(int turfId, CuboidRegion region, TurfDefenderConfig config, String tag, IntSupplier stock,
	                    IntUnaryOperator consume, IntConsumer waveNotice) {
		this.turfId     = turfId;
		this.region     = region;
		this.config     = config;
		this.tag        = tag;
		this.stock      = stock;
		this.consume    = consume;
		this.waveNotice = waveNotice;
		this.owed       = config.waveSize();
	}

	/** Guards left in the owner's reserve. */
	public int reserve() {
		return stock.getAsInt();
	}

	/** Takes {@code n} guards out of the reserve (after they spawned); returns how many were taken. */
	public int spend(int n) {
		return consume.applyAsInt(n);
	}

	/**
	 * How many guards to spawn now: what is owed, at most one wave, at most the reserve, never past {@code maxAlive}
	 * standing, and nothing until the cooldown since the last loss has run out.
	 */
	public static int toSpawn(int stock, int alive, int maxAlive, int waveSize, int owed, long lastLossAt, long now,
	                          long cooldownMs) {
		if (lastLossAt >= 0 && now - lastLossAt < cooldownMs) {
			return 0;
		}
		return Math.max(0, Math.min(Math.min(owed, waveSize), Math.min(stock, maxAlive - alive)));
	}
}
