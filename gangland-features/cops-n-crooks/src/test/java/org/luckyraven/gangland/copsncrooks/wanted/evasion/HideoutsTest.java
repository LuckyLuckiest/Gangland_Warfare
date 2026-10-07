package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.luckyraven.gangland.data.region.PlaceNames;
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.gangland.data.region.RegionProvider;
import org.luckyraven.gangland.data.region.RegionShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link Hideouts}: which hideouts a player may use. Owner rule: a hideout tagged {@code hideout} that is nobody's or his
 * own gang's, the smallest one wins, and none gives {@code null}.
 */
@DisplayName("Hideouts")
class HideoutsTest {

	private static final int MY_GANG = 7;

	private final UUID          id      = UUID.randomUUID();
	private final World         world   = mock(World.class);
	private final List<PlaceRegion> regions = new ArrayList<>();

	private Hideouts hideouts;
	private Player   player;
	private Location at;

	@BeforeEach
	void setUp() {
		when(world.getName()).thenReturn("world");
		at = new Location(world, 5, 64, 5);
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.getLocation()).thenAnswer(inv -> at.clone());

		RegionProvider provider = mock(RegionProvider.class);
		when(provider.source()).thenReturn("test");
		when(provider.regionsAt(any())).thenAnswer(inv -> List.copyOf(regions));
		PlaceNames places = new PlaceNames();
		places.register(provider);

		GangMembershipView view = mock(GangMembershipView.class);
		when(view.gangIdOf(any())).thenReturn(-1);
		when(view.gangIdOf(id)).thenReturn(MY_GANG);
		GangMembership gangs = new GangMembership();
		gangs.install(view);

		hideouts = new Hideouts(places, gangs);
	}

	private static PlaceRegion region(String regionId, int size, int owner, String... tags) {
		return new PlaceRegion(regionId, regionId, "world", RegionShape.Cuboid.column(0, 0, size, size), owner,
		                       Set.of(tags));
	}

	@Test
	@DisplayName("an unowned hideout is open to everyone")
	void unownedHideout_isOpen() {
		regions.add(region("a", 10, PlaceRegion.NO_OWNER, PlaceRegion.TAG_HIDEOUT));

		assertEquals("a", hideouts.at(player).id());
		assertEquals("a", hideouts.idAt(at, id));
	}

	@Test
	@DisplayName("his own gang's hideout is open to him")
	void ownGangHideout_isOpen() {
		regions.add(region("mine", 10, MY_GANG, PlaceRegion.TAG_HIDEOUT));

		assertEquals("mine", hideouts.idAt(at, id));
	}

	@Test
	@DisplayName("a rival gang's hideout is not open to him")
	void rivalGangHideout_isNotOpen() {
		regions.add(region("rival", 10, 99, PlaceRegion.TAG_HIDEOUT));

		assertNull(hideouts.at(player));
		assertNull(hideouts.idAt(at, id));
	}

	@Test
	@DisplayName("a region without the hideout tag (a district, a turf) is no hideout")
	void untaggedRegion_isNoHideout() {
		regions.add(region("district", 10, PlaceRegion.NO_OWNER, PlaceRegion.TAG_DISTRICT));
		regions.add(region("turf", 10, MY_GANG, PlaceRegion.TAG_TURF));

		assertNull(hideouts.at(player));
	}

	@Test
	@DisplayName("the smallest open hideout wins; a smaller rival one is skipped, not blocking")
	void smallestOpenHideoutWins() {
		regions.add(region("big", 100, PlaceRegion.NO_OWNER, PlaceRegion.TAG_HIDEOUT));
		regions.add(region("small-mine", 20, MY_GANG, PlaceRegion.TAG_HIDEOUT));
		regions.add(region("tiny-rival", 8, 99, PlaceRegion.TAG_HIDEOUT));

		assertEquals("small-mine", hideouts.at(player).id());
	}

	@Test
	@DisplayName("outside every hideout gives null")
	void outsideEveryHideout_isNull() {
		assertNull(hideouts.at(player));
		assertNull(hideouts.idAt(at, id));
	}

	@Test
	@DisplayName("a gangless player (-1) only gets unowned hideouts, never a gang's")
	void gangless_onlyUnowned() {
		UUID loner = UUID.randomUUID();
		regions.add(region("gang", 10, MY_GANG, PlaceRegion.TAG_HIDEOUT));

		assertNull(hideouts.idAt(at, loner));
	}
}
