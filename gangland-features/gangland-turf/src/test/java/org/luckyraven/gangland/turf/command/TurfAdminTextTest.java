package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
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
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-242: the six admin verbs (buff, delete, garrison, income, powerupnpc, setowner) answer a non-admin with the turf
 * text {@code Turf.Admin_Only}, never the generic {@code COMMAND_NO_PERM}. Delete checks in its action; the other five
 * check inside their optional argument, which is reached through the command tree.
 */
@DisplayName("Turf admin verbs - non-admin gets Turf.Admin_Only")
class TurfAdminTextTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	// Settings and Messages need the same minimal static init the other turf command tests use.
	@BeforeAll
	static void initStatics(@TempDir(cleanup = CleanupMode.NEVER) Path staticDir) throws IOException {
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

	private String               adminOnly;
	private JavaPlugin           plugin;
	private Tree<Argument>       tree;
	private Argument             parent;
	private Argument             verb;
	private TurfManager          turfs;
	private WandSelectionManager selections;
	private TurfMessageContract  messages;

	@BeforeEach
	void setUp() throws Exception {
		TurfMessages text = new TurfMessages(
				YamlFixture.fileManager(tempDir, "turf_messages", YamlFixture.shipped("/turf/turf_messages.yml")),
				mock(TurfMessageContract.class),
				CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90));
		adminOnly = text.text("Turf.Admin_Only", "&cOnly turf admins can do that.");

		plugin     = PluginMocks.plugin(tempDir);
		tree       = new Tree<>();
		parent     = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");
		turfs      = mock(TurfManager.class);
		selections = new WandSelectionManager();
		messages   = mock(TurfMessageContract.class);
	}

	private static Player nonAdmin() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		return player;
	}

	/** Runs the one optional-argument node of this command tree, the way the dispatcher does after the verb matched. */
	private void runOptional(CommandSender sender, String... args) {
		// The dispatcher roots the tree at the verb; the test does the same so the optional child is reachable.
		tree.add(verb.getNode());
		Argument optional = tree.getAllNodes()
				.stream()
				.map(node -> node.getData())
				.filter(OptionalArgument.class::isInstance)
				.findFirst()
				.orElseThrow();
		optional.executeArgument(sender, args);
	}

	private void assertOnlyAdminText(Player player) {
		ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
		verify(player, atLeastOnce()).sendMessage(sent.capture());
		assertEquals(List.of(adminOnly), sent.getAllValues());
	}

	@Test
	@DisplayName("buff: a non-admin gets Turf.Admin_Only")
	void buff_nonAdmin_getsAdminOnlyText() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			verb = new TurfBuffCommand(plugin, tree, parent, turfs, selections, messages,
			                    mock(PowerupRegistry.class), mock(ActiveBuffManager.class));
		}
		Player player = nonAdmin();

		runOptional(player, "turf", "buff", "speed");

		assertOnlyAdminText(player);
	}

	@Test
	@DisplayName("delete: a non-admin gets Turf.Admin_Only")
	void delete_nonAdmin_getsAdminOnlyText() {
		TurfDeleteCommand command;
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			command = new TurfDeleteCommand(plugin, tree, parent, turfs, selections, messages,
			                                mock(GarrisonManager.class), mock(ActiveBuffManager.class),
			                                mock(TurfPowerupManager.class), new TurfOwnership(turfs));
		}
		Player player = nonAdmin();

		command.action().accept(command, player, new String[]{"turf", "delete"});

		assertOnlyAdminText(player);
	}

	@Test
	@DisplayName("garrison: a non-admin gets Turf.Admin_Only")
	void garrison_nonAdmin_getsAdminOnlyText() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			verb = new TurfGarrisonCommand(plugin, tree, parent, turfs, selections, messages, mock(GarrisonManager.class));
		}
		Player player = nonAdmin();

		runOptional(player, "turf", "garrison", "3");

		assertOnlyAdminText(player);
	}

	@Test
	@DisplayName("income: a non-admin gets Turf.Admin_Only")
	void income_nonAdmin_getsAdminOnlyText() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			verb = new TurfIncomeCommand(plugin, tree, parent, turfs, selections, messages, mock(TurfSettings.class));
		}
		Player player = nonAdmin();

		runOptional(player, "turf", "income", "100");

		assertOnlyAdminText(player);
	}

	@Test
	@DisplayName("powerupnpc: a non-admin gets Turf.Admin_Only")
	void powerupNpc_nonAdmin_getsAdminOnlyText() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			verb = new TurfPowerupNpcCommand(plugin, tree, parent, turfs, selections, messages,
			                          mock(TurfPowerupManager.class));
		}
		Player player = nonAdmin();

		runOptional(player, "turf", "powerupnpc", "spawn");

		assertOnlyAdminText(player);
	}

	@Test
	@DisplayName("setowner: a non-admin gets Turf.Admin_Only")
	void setOwner_nonAdmin_getsAdminOnlyText() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			verb = new TurfSetOwnerCommand(plugin, tree, parent, turfs, new FakeGangLookup(), selections, messages,
			                        new TurfOwnership(turfs));
		}
		Player player = nonAdmin();

		runOptional(player, "turf", "setowner", "none");

		assertOnlyAdminText(player);
	}
}
