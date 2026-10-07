package org.luckyraven.gangland.data.region;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PlaceNames}: ordering by footprint, replacement by source, fault isolation and the blank-name / tag lookups
 * (CONTRACTS C1).
 */
@DisplayName("PlaceNames - the region lookup holder")
class PlaceNamesTest {

	private final Location here = mock(Location.class);

	private PlaceNames names() {
		when(here.getWorld()).thenReturn(mock(World.class));
		return new PlaceNames();
	}

	private static PlaceRegion region(String id, String name, int size, String... tags) {
		return new PlaceRegion(id, name, "world", RegionShape.Cuboid.column(0, 0, size - 1, size - 1), -1,
		                       Set.of(tags));
	}

	private static RegionProvider provider(String source, PlaceRegion... regions) {
		return new RegionProvider() {
			@Override
			public String source() {
				return source;
			}

			@Override
			public List<PlaceRegion> regionsAt(Location at) {
				return List.of(regions);
			}
		};
	}

	@Test
	@DisplayName("no provider registered answers empty")
	void noProvider_isEmpty() {
		PlaceNames names = names();

		assertTrue(names.regionsAt(here).isEmpty());
		assertTrue(names.placeAt(here).isEmpty());
		assertTrue(names.locate(here).isEmpty());
	}

	@Test
	@DisplayName("regions of every provider come back smallest footprint first")
	void smallestFootprintFirst() {
		PlaceNames names = names();
		names.register(provider("a", region("a:1", "Big", 100)));
		names.register(provider("b", region("b:1", "Small", 10)));

		assertEquals(List.of("b:1", "a:1"), names.regionsAt(here).stream().map(PlaceRegion::id).toList());
		assertEquals("Small", names.locate(here).orElseThrow());
	}

	@Test
	@DisplayName("a second provider with the same source replaces the first")
	void sameSourceReplaces() {
		PlaceNames names = names();
		names.register(provider("a", region("a:1", "Old", 10)));
		names.register(provider("a", region("a:2", "New", 10)));

		assertEquals(List.of("a:2"), names.regionsAt(here).stream().map(PlaceRegion::id).toList());
		names.unregister("a");
		assertTrue(names.regionsAt(here).isEmpty());
	}

	@Test
	@DisplayName("a provider that throws is skipped and the others still answer")
	void throwingProviderIsSkipped() {
		PlaceNames names = names();
		names.register(new RegionProvider() {
			@Override
			public String source() {
				return "bad";
			}

			@Override
			public List<PlaceRegion> regionsAt(Location at) {
				throw new IllegalStateException("boom");
			}
		});
		names.register(provider("a", region("a:1", "Fine", 10)));

		assertEquals("Fine", names.locate(here).orElseThrow());
	}

	@Test
	@DisplayName("locate skips a smaller region whose name is blank")
	void locate_skipsBlankNames() {
		PlaceNames names = names();
		names.register(provider("a", region("a:1", "", 5), region("a:2", "Named", 50)));

		assertEquals("Named", names.locate(here).orElseThrow());
	}

	@Test
	@DisplayName("withTag finds the smallest region carrying the tag")
	void withTag_findsTheSmallestTagged() {
		PlaceNames names = names();
		names.register(provider("a", region("a:1", "Tiny", 2), region("a:2", "Hide", 20, PlaceRegion.TAG_HIDEOUT),
		                        region("a:3", "Hide2", 40, PlaceRegion.TAG_HIDEOUT)));

		assertEquals("a:2", names.withTag(here, PlaceRegion.TAG_HIDEOUT).orElseThrow().id());
		assertTrue(names.withTag(here, PlaceRegion.TAG_BREAKER).isEmpty());
	}

	@Test
	@DisplayName("a null location or one without a world is empty")
	void nullWorld_isEmpty() {
		PlaceNames names = new PlaceNames();
		names.register(provider("a", region("a:1", "Anywhere", 10)));

		assertTrue(names.regionsAt(null).isEmpty());
		assertTrue(names.regionsAt(mock(Location.class)).isEmpty());
	}
}
