package org.luckyraven.gangland.gang.chat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.data.gang.GangChatChannel;
import org.luckyraven.gangland.events.gangs.GangChatEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.support.W2P2Messages;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** Who receives a gang or ally line, and what the staff spy sees (W5-C). */
@DisplayName("Gang chat routing - gang / allies / spy / gangless / cancelled event")
class GangChatRoutingTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private PluginManager          pluginManager;
	private GangMessages           messages;
	private ChatSpyService         spy;
	private GangChatToggles        toggles;

	private Player rex;
	private Player mate;
	private Player ally;
	private Player foe;
	private Player staff;
	private Player loner;

	private GangChatToggles.Context context;

	@BeforeEach
	void setUp() throws IOException {
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return path;
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		settings.when(Settings::getLanguagePicked).thenReturn("en");
		pluginManager = mock(PluginManager.class);
		bukkit        = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

		messages = W2P2Messages.shipped(tempDir);
		spy      = new ChatSpyService();
		GangLimits limits = new GangLimits() {
			@Override
			public String chatPrefix(int gangId) {
				return "&8[&a3&8]";
			}
		};
		ChatSettings chatSettings = new ChatSettings();
		chatSettings.set(false, false, "");
		toggles = new GangChatToggles(spy, limits, chatSettings);

		rex   = player("Rex");
		mate  = player("Mate");
		ally  = player("Ally");
		foe   = player("Foe");
		staff = player("Staff");
		loner = player("Loner");
		Map<Player, Integer> gangOf = Map.of(rex, 1, mate, 1, ally, 2, foe, 3);
		context = new GangChatToggles.Context(List.of(rex, mate, ally, foe, staff, loner),
		                                      p -> gangOf.getOrDefault(p, -1), id -> id == 2);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	@Test
	@DisplayName("gang channel: only the sender's gang, nobody else")
	void gang() {
		assertTrue(toggles.deliver(messages, rex, 1, "Vipers", GangChatChannel.GANG, "hi", context));

		verify(rex).sendMessage(anyString());
		verify(mate).sendMessage(anyString());
		verify(ally, never()).sendMessage(anyString());
		verify(foe, never()).sendMessage(anyString());
		verify(loner, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("allies channel: the gang plus the allied gang, not a stranger, with the gang name")
	void allies() {
		toggles.deliver(messages, rex, 1, "Vipers", GangChatChannel.ALLIES, "hi", context);

		verify(rex).sendMessage(anyString());
		verify(mate).sendMessage(anyString());
		verify(ally).sendMessage(argThat((String line) -> ChatColor.stripColor(line).contains("Vipers")));
		verify(foe, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("gang and ally lines carry the level prefix of the sender's gang")
	void hasLevelPrefix() {
		toggles.deliver(messages, rex, 1, "Vipers", GangChatChannel.GANG, "hi", context);
		toggles.deliver(messages, rex, 1, "Vipers", GangChatChannel.ALLIES, "hi", context);

		verify(mate).sendMessage(argThat((String line) -> ChatColor.stripColor(line).contains("[3]")
		                                                  && line.startsWith(ChatColor.COLOR_CHAR + "a")));
		verify(ally).sendMessage(argThat((String line) -> ChatColor.stripColor(line).contains("[3]")));
	}

	@Test
	@DisplayName("spy: staff outside both gangs gets one copy of the gang line and of the ally line")
	void spy_getsCopy() {
		spy.toggle(staff.getUniqueId());

		toggles.deliver(messages, rex, 1, "Vipers", GangChatChannel.GANG, "hi", context);
		toggles.deliver(messages, rex, 1, "Vipers", GangChatChannel.ALLIES, "yo", context);

		verify(staff, times(2)).sendMessage(anyString());
		verify(foe, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("spy who is already a recipient gets the line once")
	void spy_notDuplicated() {
		spy.toggle(mate.getUniqueId());

		toggles.deliver(messages, rex, 1, "Vipers", GangChatChannel.GANG, "hi", context);

		verify(mate, times(1)).sendMessage(anyString());
	}

	@Test
	@DisplayName("gangless sender: nobody gets a line")
	void gangless() {
		toggles.deliver(messages, loner, -1, "", GangChatChannel.GANG, "hi", context);

		verify(rex, never()).sendMessage(anyString());
		verify(loner, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a handler cancelling GangChatEvent stops every send, spies included")
	void cancelledEvent_deliversNothing() {
		spy.toggle(staff.getUniqueId());
		doAnswer(invocation -> {
			((GangChatEvent) invocation.getArgument(0)).setCancelled(true);
			return null;
		}).when(pluginManager).callEvent(any(GangChatEvent.class));

		assertFalse(toggles.deliver(messages, rex, 1, "Vipers", GangChatChannel.GANG, "hi", context));

		verify(rex, never()).sendMessage(anyString());
		verify(mate, never()).sendMessage(anyString());
		verify(staff, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("channel state: public by default, gang or allies on toggle, switching off again, gang <-> allies swap")
	void channelStates() {
		UUID id = UUID.randomUUID();
		assertEquals(GangChatChannel.PUBLIC, toggles.channelOf(id));
		assertTrue(toggles.toggleAllies(id));
		assertEquals(GangChatChannel.ALLIES, toggles.channelOf(id));
		assertTrue(toggles.isOn(id));
		assertTrue(toggles.toggle(id));
		assertEquals(GangChatChannel.GANG, toggles.channelOf(id));
		assertTrue(toggles.toggleAllies(id));
		assertFalse(toggles.toggleAllies(id));
		assertEquals(GangChatChannel.PUBLIC, toggles.channelOf(id));
		assertFalse(toggles.isOn(id));
	}

	private static Player player(String name) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		return player;
	}

}
