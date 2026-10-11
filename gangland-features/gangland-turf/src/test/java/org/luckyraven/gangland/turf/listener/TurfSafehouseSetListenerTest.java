package org.luckyraven.gangland.turf.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.events.gang.GangSafehouseSetEvent;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.TurfTextFixture;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** W5-T: a gang may not set a safehouse inside a rival or contested turf. Gang 1 sets; Alpha is x 0..99, z 0..99. */
@DisplayName("TurfSafehouseSetListener - no safehouse in rival or contested turf")
class TurfSafehouseSetListenerTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private World                    world;
	private Player                   setter;
	private TurfManager              turfs;
	private TurfMessages             text;
	private TurfSafehouseSetListener listener;

	@BeforeEach
	void setUp() throws IOException {
		world = mock(World.class);
		when(world.getName()).thenReturn("world");
		setter = CaptureFixtures.player();
		when(world.getPlayers()).thenReturn(List.of(setter));
		when(setter.getLocation()).thenReturn(new Location(world, 10, 64, 50));
		turfs = new TurfManager(mock(TurfRepositoryContract.class));
		GangLookupContract gangs = mock(GangLookupContract.class);
		var gang1 = CaptureFixtures.gang(1);
		var gang2 = CaptureFixtures.gang(2);
		when(gangs.findById(1)).thenReturn(gang1);
		when(gangs.findById(2)).thenReturn(gang2);
		text     = TurfTextFixture.messages(tempDir, mock(TurfMessageContract.class));
		listener = new TurfSafehouseSetListener(turfs, gangs, text);
	}

	private void alpha(Integer owner, boolean contested) {
		turfs.create(new Turf(1, "Alpha", new CuboidRegion("world", 0, 0, 99, 99), owner, BigDecimal.TEN, 0L, 0L));
		if (contested) {
			turfs.getRuntimeState(1).setState(TurfState.CONTESTING);
			turfs.getRuntimeState(1).setChallengerGangId(3);
		}
	}

	private GangSafehouseSetEvent set() {
		return new GangSafehouseSetEvent(1, new Location(world, 10, 64, 50), "hq");
	}

	@Test
	@DisplayName("rivalTurf_cancelled")
	void rivalTurf_cancelled() {
		alpha(2, false);
		GangSafehouseSetEvent event = set();

		listener.onSafehouseSet(event);

		assertTrue(event.isCancelled());
		verify(setter).sendMessage(text.text("Teleport_Guard.Safehouse_Refused", "", "turf", "Alpha"));
	}

	@Test
	@DisplayName("contestedTurf_cancelled: even the owner's own turf while it is under attack")
	void contestedTurf_cancelled() {
		alpha(1, true);
		GangSafehouseSetEvent event = set();

		listener.onSafehouseSet(event);

		assertTrue(event.isCancelled());
	}

	@Test
	@DisplayName("ownQuiet_allowed")
	void ownQuiet_allowed() {
		alpha(1, false);
		GangSafehouseSetEvent event = set();

		listener.onSafehouseSet(event);

		assertFalse(event.isCancelled());
	}

	@Test
	@DisplayName("outsideAnyTurf_allowed")
	void outsideAnyTurf_allowed() {
		GangSafehouseSetEvent event = set();

		listener.onSafehouseSet(event);

		assertFalse(event.isCancelled());
	}
}
