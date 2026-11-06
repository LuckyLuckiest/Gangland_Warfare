package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.turf.data.CuboidRegion;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Reaches the deployer's package-private test seams from tests in other packages, with a real deployer behind them. */
public final class DeployerSeam {

	/** Standing_Cap 0, so a peace group forms with nothing to spawn and the tick never touches a world. */
	private static final TurfDefenderConfig NO_STANDING = new TurfDefenderConfig(
			"turf_defender", 32.0, 3, 6, 20, 8, 16, GarrisonComposition.NONE, 0.5, 12, 6.0, 0);

	private DeployerSeam() {
	}

	public static TurfDefenderDeployer deployer() {
		TurfDefenderDeployer deployer = new TurfDefenderDeployer(mock(JavaPlugin.class), mock(CivilianService.class),
		                                                         mock(CivilianSpawnManager.class));
		deployer.setCitizensAvailable(() -> true);
		return deployer;
	}

	/** A garrison on turf {@code turfId} whose count is {@code stock}, owned by {@code ownerGang}, one guard at {@code price}. */
	public static TurfGarrison garrison(int turfId, AtomicInteger stock, int ownerGang, int price) {
		TurfGarrison garrison = new TurfGarrison(turfId, new CuboidRegion("world", 0, 0, 20, 20), NO_STANDING, "VIP",
		                                         stock::get, n -> stock.addAndGet(-n) + n, n -> {
		});
		garrison.funded(ownerGang, price);
		return garrison;
	}

	/** Forms the peace group of {@code garrison} the way the running server does: one reconcile through a roster. */
	public static void standing(TurfDefenderDeployer deployer, TurfGarrison garrison) {
		GarrisonRoster roster = mock(GarrisonRoster.class);
		when(roster.stockedTurfIds()).thenReturn(List.of(garrison.turfId));
		when(roster.garrisonOf(garrison.turfId)).thenReturn(garrison);
		when(roster.configOf(garrison.turfId)).thenReturn(NO_STANDING);
		when(roster.guardHealth(garrison.turfId)).thenReturn(1.0);
		deployer.bindRoster(roster);
		deployer.tick();
	}

	/** Whether {@code turfId} has a group that carries a garrison (the seam's bare {@code track} group does not). */
	public static boolean armed(TurfDefenderDeployer deployer, int turfId) {
		try {
			Field groups = TurfDefenderDeployer.class.getDeclaredField("byTurfId");
			groups.setAccessible(true);
			Object group = ((Map<?, ?>) groups.get(deployer)).get(turfId);
			if (group == null) {
				return false;
			}
			Field garrison = group.getClass().getDeclaredField("garrison");
			garrison.setAccessible(true);
			return garrison.get(group) != null;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	public static void tick(TurfDefenderDeployer deployer) {
		deployer.tick();
	}

	public static void track(TurfDefenderDeployer deployer, int turfId, CivilianNpc npc) {
		deployer.track(turfId, npc, System.currentTimeMillis());
	}
}
