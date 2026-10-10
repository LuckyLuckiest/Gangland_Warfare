package org.luckyraven.gangland.turf.command;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
		StringBuilder out = new StringBuilder();
		for (BaseComponent component : row) {
			collect(component, out);
		}
		return ChatColor.stripColor(out.toString());
	}

	/** Text of a component and all its extras: the appended tp suffix lives in an extra. */
	private static void collect(BaseComponent component, StringBuilder out) {
		if (component instanceof TextComponent text) {
			out.append(text.getText());
		}
		if (component.getExtra() != null) {
			for (BaseComponent extra : component.getExtra()) {
				collect(extra, out);
			}
		}
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

	/** The text of every line {@code list} sends on {@code page} through the spigot chat API. */
	private List<String> listedLines(int page) {
		List<String> lines = new ArrayList<>();
		CommandSender.Spigot spigot = mock(CommandSender.Spigot.class, invocation -> {
			if (invocation.getMethod().getName().equals("sendMessage")) {
				for (Object arg : invocation.getArguments()) {
					if (arg instanceof BaseComponent component) {
						lines.add(plain(new BaseComponent[]{component}));
					}
				}
			}
			return null;
		});
		CommandSender sender = mock(CommandSender.class);
		when(sender.spigot()).thenReturn(spigot);

		TurfManager manager = mock(TurfManager.class);
		List<Turf>  all     = new ArrayList<>();
		for (int id = 1; id <= 10; id++) {
			all.add(turf(id, id, id));
		}
		when(manager.getAll()).thenReturn(all);

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		Tree<Argument> tree = new Tree<>();
		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			TurfListCommand command = new TurfListCommand(plugin, tree, parent, manager,
			                                              mock(GangLookupContract.class),
			                                              mock(TurfMessageContract.class), text);
			command.list(sender, page);
		}
		return lines;
	}

	@Test
	@DisplayName("ten turfs: the next-page footer is sent on page 1 and not on page 2")
	void list_footerOnlyBeforeLastPage() {
		String footer = "Next: /glw turf list 2";

		assertTrue(listedLines(1).stream().anyMatch(line -> line.contains(footer)));
		assertFalse(listedLines(2).stream().anyMatch(line -> line.contains(footer)));
	}

	@Test
	@DisplayName("the tp suffix is shown to a turf admin only")
	void row_tpSuffix_adminOnly() {
		Turf turf = turf(1, 20, 0);

		assertTrue(plain(TurfListCommand.row(text, turf, "Rats", at(0, 0), true)).contains("(tp)"));
		assertFalse(plain(TurfListCommand.row(text, turf, "Rats", at(0, 0), false)).contains("(tp)"));
	}
}
