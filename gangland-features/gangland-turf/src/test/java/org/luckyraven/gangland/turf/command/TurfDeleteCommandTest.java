package org.luckyraven.gangland.turf.command;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.selection.Selection;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Covers {@code TurfDeleteCommand.action()} end to end through the real {@code SubArgument} construction:
 *
 * <ul>
 *   <li>GI-35 (P1): a sender without {@code gangland.turf.admin} must be refused — the nested sub-argument
 *       permission only adds onto the base {@code gangland.command.turf} node, it never required this stricter
 *       one on its own, so any player granted the base command could delete/clear-owner any turf.</li>
 *   <li>GI-34 / TF-03 (P1): a sender with the admin permission deletes the turf AND cascades cleanup to the
 *       garrison, active-buff and Quartermaster-NPC rows keyed to that turf id — none of those are foreign-keyed
 *       to the turf, so without the cascade they survived and were inherited by whichever new turf later reused
 *       the freed id (TF-05).</li>
 * </ul>
 */
@DisplayName("TurfDeleteCommand — admin permission gate (GI-35) + cascade cleanup (GI-34)")
class TurfDeleteCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private TurfManager          turfs;
	private WandSelectionManager selections;
	private TurfMessageContract  messages;
	private GarrisonManager      garrisons;
	private ActiveBuffManager    buffs;
	private TurfPowerupManager   powerupNpcs;
	private TurfDeleteCommand    command;
	private Turf                 turf;

	// Messages.COMMAND_NO_PERM is a Type.ERROR entry, so .toString() runs through
	// GanglandChatUtil.errorMessage -> ChatUtil.color(..., Replacement("%money_symbol%", Settings.getMoneySymbol())).
	// Settings' ~200 fields are process-wide statics with no reset hook, so a real (minimal) Settings.initialize()
	// pass is required or that Replacement NPEs — same fixture shape as gangland-impl's SettingsFixture, inlined
	// here since gangland-turf has no test-jar dependency on gangland-impl.
	@BeforeAll
	static void initStatics(@TempDir Path staticDir) throws IOException {
		Files.writeString(staticDir.resolve("settings.yml"), "Money_Symbol: '$'\n", StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(staticDir);
		FileHandler handler     = new FileHandler(plugin, staticDir.resolve("settings.yml").toFile());
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(handler, false);
		new Settings(fileManager).initialize();

		MessageProvider provider = mock(MessageProvider.class);
		when(provider.getString("Errors.Prefix")).thenReturn("");
		when(provider.getString("Errors.Permissions.Command")).thenReturn("&cYou don't have permission.");
		Messages.init(provider);
	}

	@BeforeEach
	void setUp() {
		turfs       = mock(TurfManager.class);
		selections  = new WandSelectionManager();
		messages    = mock(TurfMessageContract.class);
		garrisons   = mock(GarrisonManager.class);
		buffs       = mock(ActiveBuffManager.class);
		powerupNpcs = mock(TurfPowerupManager.class);

		JavaPlugin     plugin = PluginMocks.plugin(tempDir);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			command = new TurfDeleteCommand(plugin, tree, parent, turfs, selections, messages,
			                                garrisons, buffs, powerupNpcs);
		}

		turf = new Turf(5, "Dup", new CuboidRegion("world", 0, 0, 10, 10), null,
		                BigDecimal.TEN, System.currentTimeMillis(), 0L);
		when(turfs.get(5)).thenReturn(turf);
	}

	private Player adminPlayer(boolean isAdmin) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(WandSelectionManager.ADMIN_PERMISSION)).thenReturn(isAdmin);
		return player;
	}

	@Test
	@DisplayName("GI-35: no gangland.turf.admin -> COMMAND_NO_PERM, and nothing about the turf is touched")
	void action_withoutAdminPermission_deniedAndNoMutation() {
		Player player = adminPlayer(false);
		Selection selection = selections.get(player);
		selection.setActiveTurfId(5);

		command.action().accept(command, player, new String[]{"turf", "delete"});

		verify(player).sendMessage(Messages.COMMAND_NO_PERM.toString());
		verify(turfs, never()).delete(any());
		verify(garrisons, never()).remove(anyInt());
		verify(buffs, never()).removeAll(anyInt());
		verify(powerupNpcs, never()).remove(anyInt());
		assertEquals(5, selection.getActiveTurfId(), "a denied attempt must not even clear the active selection");
	}

	@Test
	@DisplayName("GI-34: with gangland.turf.admin, delete cascades to garrison/buff/NPC cleanup for that turf id")
	void action_withAdminPermission_deletesAndCascadesCleanup() {
		Player player = adminPlayer(true);
		selections.get(player).setActiveTurfId(5);

		command.action().accept(command, player, new String[]{"turf", "delete"});

		verify(turfs).delete(turf);
		verify(garrisons).remove(5);
		verify(buffs).removeAll(5);
		verify(powerupNpcs).remove(5);
		verify(player, never()).sendMessage(Messages.COMMAND_NO_PERM.toString());
	}
}
