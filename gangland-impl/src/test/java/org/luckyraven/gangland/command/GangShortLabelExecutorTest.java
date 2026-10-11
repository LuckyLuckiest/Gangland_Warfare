package org.luckyraven.gangland.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.data.gang.GangMembershipView;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("GangShortLabelExecutor - /gw /gwc /gwa forward into /glw gang")
class GangShortLabelExecutorTest {

	private final CommandSender             sender    = mock(CommandSender.class);
	private final CommandExecutor           executor  = mock(CommandExecutor.class);
	private final TabCompleter              completer = mock(TabCompleter.class);
	private final org.bukkit.command.Command target    = mock(org.bukkit.command.Command.class);

	private GangShortLabelExecutor label(String name, String... prefix) {
		return new GangShortLabelExecutor(name, List.of(prefix), "gangland.command.gang", executor, completer, target,
		                                  "glw");
	}

	@Test
	@DisplayName("the label's prefix words are put in front of what the player typed")
	void prefixing() {
		when(sender.hasPermission(anyString())).thenReturn(true);

		label("gwc", "gang", "chat").execute(sender, "gwc", new String[]{"hello", "all"});

		verify(executor).onCommand(sender, target, "glw", new String[]{"gang", "chat", "hello", "all"});
	}

	@Test
	@DisplayName("no arguments still runs the bare prefix")
	void emptyArgs() {
		when(sender.hasPermission(anyString())).thenReturn(true);

		label("gw", "gang").execute(sender, "gw", new String[0]);

		verify(executor).onCommand(sender, target, "glw", new String[]{"gang"});
	}

	@Test
	@DisplayName("a sender without the label's permission is refused and nothing is forwarded")
	void permissionRefused() {
		when(sender.hasPermission(anyString())).thenReturn(false);

		label("gw", "gang").execute(sender, "gw", new String[]{"info"});

		verify(executor, never()).onCommand(any(), any(), anyString(), any());
	}

	@Test
	@DisplayName("tab completion is asked with the prefixed arguments")
	void tabDelegation() {
		when(sender.hasPermission(anyString())).thenReturn(true);
		when(completer.onTabComplete(eq(sender), eq(target), eq("glw"), any())).thenReturn(List.of("info", "invite"));

		List<String> result = label("gw", "gang").tabComplete(sender, "gw", new String[]{"in"});

		assertEquals(List.of("info", "invite"), result);
		verify(completer).onTabComplete(sender, target, "glw", new String[]{"gang", "in"});
	}

	@Test
	@DisplayName("each label is registered under the plugin fallback prefix so /gangland_warfare:gw works if /gw is taken")
	void namespacedFallback() {
		CommandMap map = mock(CommandMap.class);

		int registered = GangShortLabelExecutor.registerIfInstalled("gangland_warfare", map, installed(), executor,
		                                                           completer, target, "glw");

		assertEquals(3, registered);
		ArgumentCaptor<org.bukkit.command.Command> commands = ArgumentCaptor.forClass(org.bukkit.command.Command.class);
		verify(map, times(3)).register(eq("gangland_warfare"), commands.capture());
		assertEquals(List.of("gw", "gwc", "gwa"), commands.getAllValues().stream().map(org.bukkit.command.Command::getName).toList());
		assertEquals("gangland.command.gang.chat.allies", commands.getAllValues().get(2).getPermission());
	}

	@Test
	@DisplayName("without the gang module installed no label is registered")
	void notRegisteredWithoutGangModule() {
		CommandMap map = mock(CommandMap.class);

		int registered = GangShortLabelExecutor.registerIfInstalled("gangland_warfare", map, new GangMembership(),
		                                                           executor, completer, target, "glw");

		assertEquals(0, registered);
		verifyNoInteractions(map);
	}

	private static GangMembership installed() {
		GangMembership membership = new GangMembership();
		membership.install(mock(GangMembershipView.class));
		return membership;
	}

}
