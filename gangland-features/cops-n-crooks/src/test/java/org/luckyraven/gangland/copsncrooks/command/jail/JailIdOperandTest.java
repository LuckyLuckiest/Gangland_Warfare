package org.luckyraven.gangland.copsncrooks.command.jail;

import org.bukkit.Location;
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
import org.luckyraven.gangland.copsncrooks.jail.JailExitService;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The {@code <id>} operand of {@code jail info|remove|setexit|teleport}: a non-numeric operand is answered with
 * {@code BAD_ID} carrying the raw text, before any service is asked; {@link Integer#parseInt} decides what is numeric.
 */
@DisplayName("jail commands: the <id> operand")
class JailIdOperandTest {

	private BukkitStatics   bukkit;
	private JailRegistry    registry;
	private JailService     jailService;
	private JailExitService exitService;
	private CommandMessages messages;
	private Player          player;

	@BeforeEach
	void setUp() {
		bukkit      = BukkitStatics.install(); // SubArgument registers a Bukkit permission on construction
		registry    = mock(JailRegistry.class);
		jailService = mock(JailService.class);
		exitService = mock(JailExitService.class);
		messages    = mock(CommandMessages.class);
		player      = mock(Player.class);

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

		return List.of(new JailInfoCommand(plugin, new Tree<>(), parent, registry, messages),
		               new JailRemoveCommand(plugin, new Tree<>(), parent, jailService, registry, messages),
		               new JailSetExitCommand(plugin, new Tree<>(), parent, registry, exitService, messages),
		               new JailTeleportCommand(plugin, new Tree<>(), parent, registry, messages));
	}

	private static void run(Argument command, CommandSender sender, String id) {
		Argument idArg = command.getNode().getChildren().get(0).getData();
		idArg.getAction().accept(idArg, sender, new String[]{"jail", "x", id});
	}

	@ParameterizedTest(name = "[{index}] \"{0}\"")
	@ValueSource(strings = {"abc", "", " 7", "7.5", "2147483648"})
	@DisplayName("a non-numeric operand gets BAD_ID with the raw text, and nothing else happens")
	void badOperand_sendsBadIdAndStops(String raw) {
		for (Argument command : commands()) {
			run(command, player, raw);
		}

		verify(messages, times(4)).format(CommandMessages.Key.BAD_ID, Map.of("value", raw));
		verify(player, times(4)).sendMessage("BAD_ID");
		verify(player, times(4)).sendMessage(any(String.class));
		verifyNoInteractions(registry, jailService, exitService);
		verify(player, never()).teleport(any(Location.class));
	}

	@Test
	@DisplayName("an operand Integer.parseInt accepts (+5) goes on to the lookup and keeps its raw text in the reply")
	void signedOperand_goesOnToTheLookup() {
		for (Argument command : commands()) {
			run(command, player, "+5");
		}

		// info, setexit: getJail(5); teleport: getJailLocation(5); each unknown jail answers JAIL_UNKNOWN with the raw text
		verify(registry, times(2)).getJail(5);
		verify(registry).getJailLocation(5);
		verify(messages, times(3)).format(CommandMessages.Key.JAIL_UNKNOWN, Map.of("id", "+5"));
		// remove: no lookup, the service is asked straight away and the parsed id is echoed
		verify(jailService).removeJail(5);
		verify(messages).format(CommandMessages.Key.JAIL_REMOVED, Map.of("id", "5"));
		verify(messages, never()).format(any(), eq(Map.of("value", "+5")));
	}

	@Test
	@DisplayName("setexit and teleport: a console is turned away with NOT_PLAYER before the operand is looked at")
	void setExitAndTeleport_console_notPlayerFirst() {
		CommandSender console = mock(CommandSender.class);
		List<Argument> commands = commands();

		run(commands.get(2), console, "abc");
		run(commands.get(3), console, "abc");

		verify(messages, times(2)).format(CommandMessages.Key.NOT_PLAYER, Map.of());
		verify(messages, never()).format(any(), eq(Map.of("value", "abc")));
		verify(console, times(2)).sendMessage("NOT_PLAYER");
		verify(console, times(2)).sendMessage(any(String.class));
		verifyNoInteractions(registry, jailService, exitService);
	}
}
