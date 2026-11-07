package org.luckyraven.gangland.turf.command;

import org.bukkit.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The owner label the turf commands print: "Unclaimed" with no owner, the gang's display name with one, and "Unknown"
 * when the owner id no longer resolves to a gang. Pins {@code /glw turf list} and {@code /glw turf status}.
 */
@DisplayName("Turf commands - the owner label of an unclaimed, owned and orphaned turf")
class TurfOwnerLabelTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> staticSettings;
	private TurfMessages           text;
	private GangLookupContract     gangs;
	private TurfManager            turfs;

	@BeforeEach
	void setUp() throws IOException {
		staticSettings = TurfTextFixture.stubMoneySymbol();
		text           = TurfTextFixture.messages(tempDir, mock(TurfMessageContract.class));
		gangs          = mock(GangLookupContract.class);
		turfs          = mock(TurfManager.class);

		Gang wolves = mock(Gang.class);
		when(wolves.getDisplayNameString()).thenReturn("Wolves");
		when(gangs.findById(5)).thenReturn(wolves);
		when(gangs.findById(6)).thenReturn(null);
	}

	@AfterEach
	void tearDown() {
		staticSettings.close();
	}

	private static Turf turf(int id, String name, Integer owner) {
		return new Turf(id, name, new CuboidRegion("world", 0, 0, 10, 10), owner, BigDecimal.ONE, 0L, 0L);
	}

	private static String plain(BaseComponent component) {
		StringBuilder out = new StringBuilder();
		collect(component, out);
		return ChatColor.stripColor(out.toString());
	}

	private static void collect(BaseComponent component, StringBuilder out) {
		if (component instanceof TextComponent textComponent) {
			out.append(textComponent.getText());
		}
		if (component.getExtra() != null) {
			for (BaseComponent extra : component.getExtra()) {
				collect(extra, out);
			}
		}
	}

	private Argument parent() {
		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");
		return parent;
	}

	@Test
	@DisplayName("list: each row carries Unclaimed, the gang's display name, or Unknown for an owner that is gone")
	void list_rowsCarryTheOwnerLabel() {
		List<String> lines = new ArrayList<>();
		CommandSender.Spigot spigot = mock(CommandSender.Spigot.class, invocation -> {
			if (invocation.getMethod().getName().equals("sendMessage")) {
				for (Object arg : invocation.getArguments()) {
					if (arg instanceof BaseComponent component) {
						lines.add(plain(component));
					}
				}
			}
			return null;
		});
		CommandSender sender = mock(CommandSender.class);
		when(sender.spigot()).thenReturn(spigot);
		when(turfs.getAll()).thenReturn(List.of(turf(1, "Alpha", null), turf(2, "Bravo", 5), turf(3, "Charlie", 6)));

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			new TurfListCommand(plugin, new Tree<>(), parent(), turfs, gangs, mock(TurfMessageContract.class), text)
					.list(sender, 1);
		}

		assertEquals(3, lines.size(), lines.toString());
		assertTrue(lines.get(0).contains("Alpha") && lines.get(0).contains("Unclaimed"), lines.get(0));
		assertTrue(lines.get(1).contains("Bravo") && lines.get(1).contains("Wolves"), lines.get(1));
		assertTrue(lines.get(2).contains("Charlie") && lines.get(2).contains("Unknown"), lines.get(2));
	}

	private void status(Turf turf, TurfMessageContract messages) {
		Player player = mock(Player.class);
		when(player.hasPermission(TurfPermissions.ADMIN)).thenReturn(true);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(turfs.get(turf.getId())).thenReturn(turf);
		when(turfs.getRuntimeState(turf.getId())).thenReturn(null);

		WandSelectionManager selections = new WandSelectionManager();
		selections.get(player).setActiveTurfId(turf.getId());

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			TurfStatusCommand command = new TurfStatusCommand(plugin, new Tree<>(), parent(), turfs, gangs, selections,
			                                                  messages, mock(TurfSettings.class), text,
			                                                  mock(CaptureEligibility.class));
			command.action().accept(mock(Argument.class), player, new String[]{"turf", "status"});
		}
	}

	@Test
	@DisplayName("status: an idle turf reports Unclaimed, the owner's display name, or Unknown for a gone owner")
	void status_idleLineCarriesTheOwnerLabel() {
		TurfMessageContract unclaimed = mock(TurfMessageContract.class);
		status(turf(1, "Alpha", null), unclaimed);
		verify(unclaimed).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("TURF_STATUS_IDLE"),
		                       org.mockito.ArgumentMatchers.eq("turf"), org.mockito.ArgumentMatchers.eq("Alpha"),
		                       org.mockito.ArgumentMatchers.eq("gang"), org.mockito.ArgumentMatchers.eq("Unclaimed"));

		TurfMessageContract owned = mock(TurfMessageContract.class);
		status(turf(2, "Bravo", 5), owned);
		verify(owned).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("TURF_STATUS_IDLE"),
		                   org.mockito.ArgumentMatchers.eq("turf"), org.mockito.ArgumentMatchers.eq("Bravo"),
		                   org.mockito.ArgumentMatchers.eq("gang"), org.mockito.ArgumentMatchers.eq("Wolves"));

		TurfMessageContract gone = mock(TurfMessageContract.class);
		status(turf(3, "Charlie", 6), gone);
		verify(gone).send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("TURF_STATUS_IDLE"),
		                  org.mockito.ArgumentMatchers.eq("turf"), org.mockito.ArgumentMatchers.eq("Charlie"),
		                  org.mockito.ArgumentMatchers.eq("gang"), org.mockito.ArgumentMatchers.eq("Unknown"));
	}
}
