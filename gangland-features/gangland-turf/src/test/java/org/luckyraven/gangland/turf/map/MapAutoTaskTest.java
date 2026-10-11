package org.luckyraven.gangland.turf.map;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.capture.TurfRelation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MapAutoTask} gates: first tick sends, same content never resends, a change waits for the interval, a cell
 * move with unchanged content does not send, and a player that leaves auto is forgotten. Runs on a fake clock.
 */
@DisplayName("MapAutoTask - auto map gates on a fake clock")
class MapAutoTaskTest {

	private static final int INTERVAL_SECONDS = 5;

	private final MapPrefsService prefs   = new MapPrefsService();
	private final AtomicLong      clock   = new AtomicLong();
	private final List<Player>    sent    = new ArrayList<>();
	private final UUID            id      = UUID.randomUUID();
	private final Player          viewer  = mock(Player.class);
	private char                  glyph   = 'A';
	private int                   rasters = 0;
	private int                   cell    = 16;
	private Location              at      = new Location(null, 8, 64, 8);

	private MapAutoTask task() {
		when(viewer.getLocation()).thenReturn(at);
		return new MapAutoTask(mock(JavaPlugin.class), prefs, uuid -> uuid.equals(id) ? viewer : null,
		                       player -> {
			                       rasters++;
			                       return raster(glyph);
		                       },
		                       sent::add, clock::get, player -> cell, () -> INTERVAL_SECONDS);
	}

	@Test
	@DisplayName("a zoom change at the same position recomputes the map on the next tick, and sends once the interval passes")
	void zoomChange_recomputesTheMap() {
		prefs.setAuto(id, true);
		at = new Location(null, 0, 64, 0);
		MapAutoTask task = task();
		clock.set(seconds(0));
		task.tick();
		assertEquals(1, sent.size());
		sent.clear();

		cell  = 8;
		glyph = 'B';
		int computed = rasters;
		clock.set(seconds(1));
		task.tick();
		assertEquals(computed + 1, rasters);
		assertEquals(0, sent.size());

		clock.set(seconds(INTERVAL_SECONDS));
		task.tick();
		assertEquals(1, sent.size());
	}

	private static TerritoryRaster.Raster raster(char glyph) {
		TerritoryRaster.Cell[][] cells = {{new TerritoryRaster.Cell(glyph, TurfRelation.OWN,
		                                                            TerritoryRaster.Style.PLAIN, 1)}};
		return new TerritoryRaster.Raster(cells, List.of(), 1, -1);
	}

	private static long seconds(long value) {
		return value * 1000L;
	}

	@Test
	@DisplayName("the first tick sends the map (the command does not), so a move before it cannot be lost")
	void firstTick_sendsTheMap() {
		prefs.setAuto(id, true);
		MapAutoTask task = task();

		clock.set(seconds(0));
		task.tick();

		assertEquals(1, sent.size());
		assertEquals(1, rasters);
	}

	@Test
	@DisplayName("the same content is never sent twice")
	void sameContent_noResend() {
		prefs.setAuto(id, true);
		MapAutoTask task = task();
		clock.set(seconds(0));
		task.tick();
		sent.clear();

		clock.set(seconds(INTERVAL_SECONDS));
		task.tick();
		clock.set(seconds(INTERVAL_SECONDS + 1));
		task.tick();

		assertEquals(0, sent.size());
	}

	@Test
	@DisplayName("a content change before the interval waits until the interval has passed")
	void contentChange_waitsForTheInterval() {
		prefs.setAuto(id, true);
		MapAutoTask task = task();
		clock.set(seconds(0));
		task.tick();
		sent.clear();

		glyph = 'B';
		clock.set(seconds(2));
		task.tick();
		assertEquals(0, sent.size());

		clock.set(seconds(INTERVAL_SECONDS));
		task.tick();
		assertEquals(1, sent.size());
	}

	@Test
	@DisplayName("a cell move that changes the content before the interval recomputes but waits to send")
	void cellMove_contentChange_waitsForTheInterval() {
		prefs.setAuto(id, true);
		MapAutoTask task = task();
		clock.set(seconds(0));
		task.tick();
		sent.clear();
		int computed = rasters;

		glyph = 'B';
		at = new Location(null, 40, 64, 8);
		when(viewer.getLocation()).thenReturn(at);
		clock.set(seconds(1));
		task.tick();
		assertEquals(computed + 1, rasters);
		assertEquals(0, sent.size());

		clock.set(seconds(INTERVAL_SECONDS));
		task.tick();
		assertEquals(1, sent.size());
	}

	@Test
	@DisplayName("a cell move with unchanged content recomputes but does not send")
	void cellMove_unchangedContent_noSend() {
		prefs.setAuto(id, true);
		MapAutoTask task = task();
		clock.set(seconds(0));
		task.tick();
		sent.clear();
		int computed = rasters;

		at = new Location(null, 40, 64, 8);
		when(viewer.getLocation()).thenReturn(at);
		clock.set(seconds(1));
		task.tick();

		assertEquals(computed + 1, rasters);
		assertEquals(0, sent.size());
	}

	@Test
	@DisplayName("a player removed from auto is forgotten: turning auto back on seeds it again from a fresh raster")
	void leavingAuto_forgotten() {
		prefs.setAuto(id, true);
		MapAutoTask task = task();
		clock.set(seconds(0));
		task.tick();
		sent.clear();

		prefs.setAuto(id, false);
		clock.set(seconds(1));
		task.tick();
		int computed = rasters;

		prefs.setAuto(id, true);
		clock.set(seconds(2));
		task.tick();

		assertEquals(computed + 1, rasters);
		assertEquals(1, sent.size(), "turning auto back on sends the map again on the next tick");
	}
}
