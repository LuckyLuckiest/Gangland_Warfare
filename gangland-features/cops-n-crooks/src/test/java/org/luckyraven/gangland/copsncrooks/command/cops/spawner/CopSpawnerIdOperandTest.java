package org.luckyraven.gangland.copsncrooks.command.cops.spawner;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The {@code <id>} operand of {@code cop spawner info|remove|teleport}: a non-numeric operand is answered with
 * {@code BAD_ID} carrying the raw text, before any manager is asked; {@link Integer#parseInt} decides what is numeric.
 */
@DisplayName("cop spawner commands: the <id> operand")
class CopSpawnerIdOperandTest {

	private BukkitStatics   bukkit;
	private CopSpawnManager manager;
	private CommandMessages messages;
	private Player          player;

	@BeforeEach
	void setUp() {
		bukkit   = BukkitStatics.install(); // SubArgument registers a Bukkit permission on construction
		manager  = mock(CopSpawnManager.class);
		messages = mock(CommandMessages.class);
		player   = mock(Player.class);

		// every line comes back as its key name, so the test can see which message went out
		when(messages.format(any(), anyMap())).thenAnswer(call -> call.getArgument(0).toString());
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private List<Argument> commands() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		Argument   parent = mock(Argument.class);

		return List.of(new CopSpawnerInfoCommand(plugin, new Tree<>(), parent, manager, messages),
		               new CopSpawnerRemoveCommand(plugin, new Tree<>(), parent, manager, messages),
		               new CopSpawnerTeleportCommand(plugin, new Tree<>(), parent, manager, messages));
	}

	private static void run(Argument command, CommandSender sender, String id) {
		Argument idArg = command.getNode().getChildren().get(0).getData();
		idArg.getAction().accept(idArg, sender, new String[]{"cop", "spawner", "x", id});
	}

	@ParameterizedTest(name = "[{index}] \"{0}\"")
	@ValueSource(strings = {"abc", "", " 7", "7.5", "2147483648"})
	@DisplayName("a non-numeric operand gets BAD_ID with the raw text, and nothing else happens")
	void badOperand_sendsBadIdAndStops(String raw) {
		for (Argument command : commands()) {
			run(command, player, raw);
		}

		verify(messages, times(3)).format(CommandMessages.Key.BAD_ID, Map.of("value", raw));
		verify(player, times(3)).sendMessage("BAD_ID");
		verify(player, times(3)).sendMessage(any(String.class));
		verifyNoInteractions(manager);
		verify(player, never()).teleport(any(org.bukkit.Location.class));
	}

	@Test
	@DisplayName("an operand Integer.parseInt accepts (+5) goes on to the lookup and keeps its raw text in the reply")
	void signedOperand_goesOnToTheLookup() {
		for (Argument command : commands()) {
			run(command, player, "+5");
		}

		verify(manager, times(3)).getSpawnerLocation(5);
		verify(messages, times(3)).format(CommandMessages.Key.SPAWNER_UNKNOWN, Map.of("id", "+5"));
		verify(messages, never()).format(any(), org.mockito.ArgumentMatchers.eq(Map.of("value", "+5")));
	}

	@Test
	@DisplayName("teleport: a console is turned away with NOT_PLAYER before the operand is looked at")
	void teleport_console_notPlayerFirst() {
		CommandSender console = mock(CommandSender.class);
		Argument      command = commands().get(2);

		run(command, console, "abc");

		verify(messages).format(CommandMessages.Key.NOT_PLAYER, Map.of());
		verify(messages, never()).format(any(), org.mockito.ArgumentMatchers.eq(Map.of("value", "abc")));
		verify(console).sendMessage("NOT_PLAYER");
		verify(console, times(1)).sendMessage(any(String.class));
		verifyNoInteractions(manager);
	}
}
