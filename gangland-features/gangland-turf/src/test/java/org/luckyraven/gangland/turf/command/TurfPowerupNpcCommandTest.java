package org.luckyraven.gangland.turf.command;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-803: staff {@code /glw turf powerupnpc set} places a Quartermaster only while the staff member stands inside the
 * selected turf; outside it (or inside another turf) the command refuses and nothing is placed.
 */
@DisplayName("TurfPowerupNpcCommand - set containment (T-803)")
class TurfPowerupNpcCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private TurfManager        turfs;
	private TurfMessageContract messages;
	private TurfPowerupManager powerupNpcs;
	private TurfPowerupNpcCommand command;
	private Turf               turf;
	private Player             player;
	private Location           location;

	@BeforeEach
	void setUp() throws IOException {
		turfs       = mock(TurfManager.class);
		messages    = mock(TurfMessageContract.class);
		powerupNpcs = mock(TurfPowerupManager.class);
		TurfMessages text = new TurfMessages(
				YamlFixture.fileManager(tempDir, "turf_messages", YamlFixture.shipped("/turf/turf_messages.yml")),
				messages, CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));

		JavaPlugin     plugin = PluginMocks.plugin(tempDir);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			command = new TurfPowerupNpcCommand(plugin, tree, parent, turfs, new WandSelectionManager(),
			                                    messages, text, powerupNpcs);
		}

		turf = new Turf(5, "Dup", new CuboidRegion("world", 0, 0, 10, 10), null,
		                BigDecimal.TEN, System.currentTimeMillis(), 0L);
		player   = mock(Player.class);
		location = mock(Location.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getLocation()).thenReturn(location);
	}

	@Test
	@DisplayName("outside any turf: refused with TURF_NOT_INSIDE and nothing is placed")
	void placeHere_outsideTurf_refusedAndNothingPlaced() {
		command.placeHere(player, turf);

		verify(messages).send(player, "TURF_NOT_INSIDE");
		verify(powerupNpcs, never()).place(anyInt(), any(), any());
	}

	@Test
	@DisplayName("inside another turf than the selected one: refused and nothing is placed")
	void placeHere_insideOtherTurf_refusedAndNothingPlaced() {
		when(turfs.findAt(location)).thenReturn(new Turf(6, "Other", new CuboidRegion("world", 20, 20, 30, 30), null,
		                                                  BigDecimal.TEN, System.currentTimeMillis(), 0L));

		command.placeHere(player, turf);

		verify(messages).send(player, "TURF_NOT_INSIDE");
		verify(powerupNpcs, never()).place(anyInt(), any(), any());
	}

	@Test
	@DisplayName("inside the selected turf: places exactly as before")
	void placeHere_insideTurf_places() {
		when(turfs.findAt(location)).thenReturn(turf);

		command.placeHere(player, turf);

		verify(powerupNpcs).place(5, location, null);
		verify(messages).send(player, "TURF_POWERUPNPC_SET", "turf", "Dup");
	}
}
