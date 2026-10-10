package org.luckyraven.gangland.turf.command;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.state.CapturePhase;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code /glw turf status} on an unclaimed capture: the contesting line carries an empty phase, so no "Phase 1 /
 * Phase 2" text (and no literal {@code &8} suffix) reaches the admin (T-207).
 */
@DisplayName("TurfStatusCommand - contesting line without phase texts")
class TurfStatusCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> staticSettings;
	private TurfMessages           text;
	private TurfManager            turfs;
	private Player                 player;
	private TurfStatusCommand      command;
	private String                 sentKey;
	private List<Object>           sentReplacements;

	/** Records the last send(): the key, and the replacement pairs as a flat list. */
	private TurfMessageContract recorder() {
		return new TurfMessageContract() {
			@Override
			public String format(String key, Object... replacements) {
				return key;
			}

			@Override
			public void send(org.bukkit.command.CommandSender recipient, String key, Object... replacements) {
				sentKey          = key;
				sentReplacements = new ArrayList<>(List.of(replacements));
			}

			@Override
			public void broadcast(String key, Object... replacements) {
			}

			@Override
			public String formatDuration(long seconds) {
				return seconds + "s";
			}
		};
	}

	@BeforeEach
	void setUp() throws IOException {
		staticSettings = TurfTextFixture.stubMoneySymbol();
		TurfMessageContract messages = recorder();
		text = TurfTextFixture.messages(tempDir, messages);

		Turf docks = new Turf(1, "Docks", new CuboidRegion("world", 0, 0, 10, 10), null,
		                      BigDecimal.TEN, System.currentTimeMillis(), 0L);
		turfs = mock(TurfManager.class);
		when(turfs.get(1)).thenReturn(docks);

		TurfRuntimeState state = new TurfRuntimeState(1);
		state.setState(TurfState.CONTESTING);
		state.setPhase(CapturePhase.CLAIM);
		state.setChallengerGangId(2);
		state.setCaptureProgress(34.0);
		when(turfs.getRuntimeState(1)).thenReturn(state);

		player = mock(Player.class);
		when(player.hasPermission(TurfPermissions.ADMIN)).thenReturn(true);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		WandSelectionManager selections = new WandSelectionManager();
		selections.get(player).setActiveTurfId(1);

		CaptureSettings capture = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		JavaPlugin     plugin = PluginMocks.plugin(tempDir);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			command = new TurfStatusCommand(plugin, tree, parent, turfs, mock(GangLookupContract.class), selections,
			                                messages, mock(TurfSettings.class), text,
			                                mock(CaptureEligibility.class));
		}
	}

	@AfterEach
	void releaseStatics() {
		staticSettings.close();
	}

	@Test
	@DisplayName("an unclaimed contest sends the contesting status line")
	void unclaimedContest_sendsContestingLine() {
		command.action().accept(mock(Argument.class), player, new String[]{"turf", "status"});

		assertEquals("TURF_STATUS_CONTESTING", sentKey);
	}

	@Test
	@DisplayName("the contesting line passes an empty phase, so the Phase 1 / Phase 2 suffix is gone")
	void unclaimedContest_passesEmptyPhase() {
		command.action().accept(mock(Argument.class), player, new String[]{"turf", "status"});

		int index = sentReplacements.indexOf("phase");
		assertTrue(index >= 0, sentReplacements.toString());
		assertEquals("", sentReplacements.get(index + 1));
	}

	@Test
	@DisplayName("no phase text or literal &8 suffix reaches the admin on an unclaimed contest")
	void unclaimedContest_noPhaseTextAnywhere() {
		command.action().accept(mock(Argument.class), player, new String[]{"turf", "status"});

		for (Object value : sentReplacements) {
			String string = String.valueOf(value);
			assertFalse(string.contains("Phase"), string);
			assertFalse(string.contains("&8"), string);
		}
	}
}
