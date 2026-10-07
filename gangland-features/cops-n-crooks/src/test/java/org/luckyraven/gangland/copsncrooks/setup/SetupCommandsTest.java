package org.luckyraven.gangland.copsncrooks.setup;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code /glw cop setup list|tp|link}: the one-line row format, the kind filter, the teleport targets, the jail link. */
@DisplayName("SetupCommands.list, tp and link")
class SetupCommandsTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private SetupFixture fx;

	@BeforeEach
	void setUp() throws IOException {
		fx = new SetupFixture(tempDir);
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	private void oneOfEach() {
		fx.stations.create("HQ", fx.at(10, 64, 20));
		fx.regions.create("Downtown", fx.at(0, 60, 0), fx.at(5, 70, 5), "district");
		fx.points.create("pickup", "Dock", fx.at(7, 65, 9));
	}

	@Test
	@DisplayName("list prints kind id name world x y z [tags], one line per row")
	void list_printsOneLinePerRow_inTheC15Format() {
		oneOfEach();
		CommandSender sender = mock(CommandSender.class);

		fx.commands.list(sender, null);

		verify(sender).sendMessage("station 1 HQ world 10 64 20");
		verify(sender).sendMessage("region 1 Downtown world 0 60 0 [district]");
		verify(sender).sendMessage("point 1 Dock world 7 65 9 [pickup]");
	}

	@Test
	@DisplayName("list <kind> prints only that kind, and refuses a kind it does not know")
	void list_filtersByKind() {
		oneOfEach();
		CommandSender sender = mock(CommandSender.class);

		fx.commands.list(sender, "region");
		fx.commands.list(sender, "bogus");

		verify(sender).sendMessage("region 1 Downtown world 0 60 0 [district]");
		verify(sender, never()).sendMessage("station 1 HQ world 10 64 20");
		verify(sender, never()).sendMessage("point 1 Dock world 7 65 9 [pickup]");
		verify(sender).sendMessage(fx.text(SetupMessages.Key.UNKNOWN_KIND));
	}

	@Test
	@DisplayName("list with nothing placed says so")
	void list_empty_saysSo() {
		CommandSender sender = mock(CommandSender.class);

		fx.commands.list(sender, null);

		verify(sender).sendMessage(fx.text(SetupMessages.Key.LIST_EMPTY));
	}

	@Test
	@DisplayName("tp station goes to the station anchor")
	void tp_station_goesToTheAnchor() {
		oneOfEach();
		Player admin = fx.admin();

		fx.commands.teleport(admin, "station", "1");

		ArgumentCaptor<Location> to = ArgumentCaptor.forClass(Location.class);
		verify(admin).teleport(to.capture());
		assertEquals(10.0, to.getValue().getX());
		assertEquals(64.0, to.getValue().getY());
		assertEquals(20.0, to.getValue().getZ());
	}

	@Test
	@DisplayName("tp region goes to the centre of the cuboid, one block above the top block")
	void tp_region_goesToTheCentre() {
		fx.regions.create("Big", fx.at(0, 60, 0), fx.at(10, 70, 10), "district");
		when(fx.world.getHighestBlockYAt(5, 5)).thenReturn(68);
		Player admin = fx.admin();

		fx.commands.teleport(admin, "region", "1");

		ArgumentCaptor<Location> to = ArgumentCaptor.forClass(Location.class);
		verify(admin).teleport(to.capture());
		assertEquals(5.5, to.getValue().getX());
		assertEquals(69.0, to.getValue().getY());
		assertEquals(5.5, to.getValue().getZ());
	}

	@Test
	@DisplayName("tp point goes to the point")
	void tp_point_goesToThePoint() {
		oneOfEach();
		Player admin = fx.admin();

		fx.commands.teleport(admin, "point", "1");

		ArgumentCaptor<Location> to = ArgumentCaptor.forClass(Location.class);
		verify(admin).teleport(to.capture());
		assertEquals(7.0, to.getValue().getX());
		assertEquals(9.0, to.getValue().getZ());
	}

	@Test
	@DisplayName("link sets the station's jail")
	void link_setsTheJail() {
		oneOfEach();
		fx.jails.addJail(new Jail(7, fx.at(0, 64, 0), 5));
		CommandSender sender = mock(CommandSender.class);

		fx.commands.link(sender, "1", "7");

		assertEquals(7, fx.stations.get(1).getJailId());
		verify(sender).sendMessage(fx.text(SetupMessages.Key.LINKED, Map.of("station", "1", "jail", "7")));
	}

	@Test
	@DisplayName("link none unlinks")
	void link_none_unlinks() {
		oneOfEach();
		fx.jails.addJail(new Jail(7, fx.at(0, 64, 0), 5));
		CommandSender sender = mock(CommandSender.class);
		fx.commands.link(sender, "1", "7");

		fx.commands.link(sender, "1", "none");

		assertNull(fx.stations.get(1).getJailId());
		verify(sender).sendMessage(fx.text(SetupMessages.Key.UNLINKED, Map.of("station", "1")));
	}

	@Test
	@DisplayName("link refuses an unknown station and an unknown jail, changing nothing")
	void link_unknownIds_refuse() {
		oneOfEach();
		CommandSender sender = mock(CommandSender.class);

		fx.commands.link(sender, "9", "none");
		fx.commands.link(sender, "1", "42");

		verify(sender).sendMessage(fx.text(SetupMessages.Key.NOT_FOUND, Map.of("kind", "station", "id", "9")));
		verify(sender).sendMessage(fx.text(SetupMessages.Key.UNKNOWN_JAIL, Map.of("id", "42")));
		assertNull(fx.stations.get(1).getJailId());
	}
}
