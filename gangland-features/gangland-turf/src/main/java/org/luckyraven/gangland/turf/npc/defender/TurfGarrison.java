package org.luckyraven.gangland.turf.npc.defender;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.FactionSquads;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.npc.defender.GarrisonPosts.Post;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * One turf's guards: the stock access, the wave state (last loss, spawn delay), the posts and who pays for a guard. The
 * count is guards owned, standing or not; a guard leaves it only through {@link #spend}, which the deployer calls for a
 * contest kill. Ticked by {@link TurfDefenderDeployer}.
 */
public final class TurfGarrison {

	final int                turfId;
	final CuboidRegion       region;
	TurfDefenderConfig       config;
	String                   tag;
	final IntConsumer        waveNotice;
	private final IntSupplier      stock;
	private final IntUnaryOperator consume;
	/** The last known post per spot, for walking home; a spot stays null until its chunk was seen loaded. */
	private final Post[]           known = new Post[5];

	long          lastLossAt   = -1L;
	/** When the wave started waiting for a hidden post; -1 when it is not waiting. */
	long          delayedSince = -1L;
	/** The known posts with no null in them ({@link GarrisonPosts#nearest} takes none); null until a spawn pass. */
	List<Post>    posts;
	FactionSquads squads;
	boolean       standingDown;
	long          standDownAt;
	/** Gang that owns the turf and bought the guards; -1 when none (a guard then pays no bounty). */
	int           ownerGangId  = -1;
	/** What one guard cost the owner at the last adoption (ponytail: not the historical purchase price). */
	int           guardPrice;

	/** Records who paid for the guards and what one cost; the bounty is a share of it. */
	public void funded(int ownerGangId, int guardPrice) {
		this.ownerGangId = ownerGangId;
		this.guardPrice  = guardPrice;
	}

	public TurfGarrison(int turfId, CuboidRegion region, TurfDefenderConfig config, String tag, IntSupplier stock,
	                    IntUnaryOperator consume, IntConsumer waveNotice) {
		this.turfId     = turfId;
		this.region     = region;
		this.config     = config;
		this.tag        = tag;
		this.stock      = stock;
		this.consume    = consume;
		this.waveNotice = waveNotice;
	}

	/** Guards the gang owns, standing or not: the row's count. */
	public int owned() {
		return stock.getAsInt();
	}

	/** Takes {@code n} guards out of the stock; returns how many were taken. */
	public int spend(int n) {
		return consume.applyAsInt(n);
	}

	/** Guards that should stand: at most the stock and {@code Max_Alive}, and outside a contest at most the cap. */
	public static int target(int stock, boolean contested, TurfDefenderConfig c) {
		return contested ? Math.min(stock, c.maxAlive()) : Math.min(stock, Math.min(c.standingCap(), c.maxAlive()));
	}

	/** The lowest callsign number from 1 that no live guard holds. A number is a slot; this stops double spawns. */
	static int lowestFree(Collection<Integer> taken) {
		int number = 1;
		while (taken.contains(number)) {
			number++;
		}
		return number;
	}

	/** T-767: a contest start takes the live config, tag, owner and price of a freshly assembled garrison. */
	void adopt(TurfGarrison fresh) {
		config      = fresh.config;
		tag         = fresh.tag;
		ownerGangId = fresh.ownerGangId;
		guardPrice  = fresh.guardPrice;
	}

	/** Takes in a spawn pass's spots (null where the chunk is not loaded), keeping the spots already known. */
	void remember(List<Post> loaded) {
		List<Post> rebuilt = new ArrayList<>(known.length);
		for (int i = 0; i < known.length; i++) {
			if (loaded.get(i) != null) {
				known[i] = loaded.get(i);
			}
			if (known[i] != null) {
				rebuilt.add(known[i]);
			}
		}
		posts = rebuilt;
	}

	/** Guard {@code number}'s own post, or null for a guard with no number or a spot never seen loaded. */
	@Nullable
	Post homeOf(int number) {
		return number < 1 ? null : known[(number - 1) % known.length];
	}

	/**
	 * How many guards to spawn now: the gap to the target, at most one wave, at most the reserve, never past {@code maxAlive}
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
