package org.luckyraven.gangland.turf.command;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code /glw turf list}: nearest first for a player in his world, id order for the console, 8 rows a page, and the
 * {@code (tp)} suffix only for a turf admin.
 */
@DisplayName("TurfListCommand - distance order, paging, admin tp suffix")
class TurfListCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private TurfMessages           text;

	@BeforeEach
	void setUp() throws IOException {
		settings = TurfTextFixture.stubMoneySymbol();
		text     = TurfTextFixture.messages(tempDir, mock(TurfMessageContract.class));
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	private static Turf turf(int id, int x, int z) {
		return new Turf(id, "Turf" + id, new CuboidRegion("world", x, z, x, z), null, BigDecimal.ONE, 0L, 0L);
	}

	private static Location at(int x, int z) {
		World world = mock(World.class);
		when(world.getName()).thenReturn("world");
		return new Location(world, x, 64, z);
	}

	private static String plain(BaseComponent[] row) {
		return Arrays.stream(row).map(BaseComponent::toPlainText).collect(Collectors.joining());
	}

	@Test
	@DisplayName("a player sees his world's turfs nearest first: 20, 100, 300 blocks")
	void ordered_playerNearestFirst() {
		Turf far    = turf(2, -300, 0);
		Turf near   = turf(1, 20, 0);
		Turf middle = turf(3, 0, -100);

		List<Turf> ordered = TurfListCommand.ordered(List.of(far, near, middle), at(0, 0));

		assertEquals(List.of(1, 3, 2), ordered.stream().map(Turf::getId).toList());
		assertTrue(plain(TurfListCommand.row(text, near, "Rats", at(0, 0), false)).contains("20m E"));
		assertTrue(plain(TurfListCommand.row(text, middle, "Rats", at(0, 0), false)).contains("100m N"));
		assertTrue(plain(TurfListCommand.row(text, far, "Rats", at(0, 0), false)).contains("300m W"));
	}

	@Test
	@DisplayName("the console sees every turf sorted by id")
	void ordered_consoleById() {
		List<Turf> ordered = TurfListCommand.ordered(List.of(turf(5, 0, 0), turf(2, 900, 900), turf(9, 1, 1)), null);

		assertEquals(List.of(2, 5, 9), ordered.stream().map(Turf::getId).toList());
	}

	@Test
	@DisplayName("ten turfs page as 8 then 2")
	void pageSlice_eightPerPage() {
		List<Turf> all = new ArrayList<>();
		for (int id = 1; id <= 10; id++) {
			all.add(turf(id, id, id));
		}

		assertEquals(2, TurfListCommand.pageCount(all.size()));
		assertEquals(8, TurfListCommand.pageSlice(all, 1).size());
		assertEquals(2, TurfListCommand.pageSlice(all, 2).size());
		assertEquals(9, TurfListCommand.pageSlice(all, 2).get(0).getId());
	}

	@Test
	@DisplayName("the tp suffix is shown to a turf admin only")
	void row_tpSuffix_adminOnly() {
		Turf turf = turf(1, 20, 0);

		assertTrue(plain(TurfListCommand.row(text, turf, "Rats", at(0, 0), true)).contains("(tp)"));
		assertFalse(plain(TurfListCommand.row(text, turf, "Rats", at(0, 0), false)).contains("(tp)"));
	}
}
