package org.luckyraven.gangland.listener.gang;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.data.gang.GangChatChannel;
import org.luckyraven.gangland.events.gangs.GangChatEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.support.W2P2Messages;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Gang chat listener: a toggled player's line is cancelled and delivered on the main thread (W2-P2). */
@DisplayName("GangChatListener - gang chat cancels the public line and delivers it on the main thread")
class GangChatListenerTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private JavaPlugin             plugin;
	private BukkitScheduler        scheduler;
	private GangChatToggles        channel;
	private UserManager<Player>    userManager;
	private GangMessages           messages;
	private GangManager            gangManager;
	private PluginManager          pluginManager;
	private Gang                   vipers;
	private Gang                   rats;
	private GangChatListener       listener;
	private List<Player>           online;

	private Player rex;
	private Player mate;
	private Player foe;

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

		scheduler = mock(BukkitScheduler.class);
		bukkit    = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
		pluginManager = mock(PluginManager.class);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.copyOf(online));

		plugin      = mock(JavaPlugin.class);
		channel     = new GangChatToggles();
		userManager = mock(UserManager.class);
		messages    = W2P2Messages.shipped(tempDir);
		gangManager = mock(GangManager.class);
		vipers      = gang(1, "Vipers");
		rats        = gang(2, "Rats");
		listener    = new GangChatListener(plugin, channel, userManager, messages, gangManager);

		rex  = player("Rex", 1);
		mate = player("Mate", 1);
		foe  = player("Foe", 2);
		online = List.of(rex, mate, foe);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	@Test
	@DisplayName("a toggled member's line is cancelled; both gang members get it on the main thread, the other gang none")
	void toggled_cancelsAndDeliversToGang() {
		channel.toggle(rex.getUniqueId());
		AsyncPlayerChatEvent event = chat(rex, "hi");

		listener.onChat(event);

		assertTrue(event.isCancelled());
		verify(rex, never()).sendMessage(anyString());
		verifyNoInteractions(userManager);
		Runnable delivery = capturedTask();
		delivery.run();

		String line = channel.format(messages, "Rex", "hi");
		verify(rex).sendMessage(line);
		verify(mate).sendMessage(line);
		verify(foe, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a player who never toggled gang chat keeps normal chat: the event is not cancelled and nothing is scheduled")
	void untoggled_notCancelled() {
		AsyncPlayerChatEvent event = chat(rex, "hi");

		listener.onChat(event);

		assertFalse(event.isCancelled());
		verifyNoInteractions(scheduler);
	}

	@Test
	@DisplayName("a toggled player who has left the gang gets Chat_Off, the toggle is cleared and the line is re-sent as public chat")
	void toggledButGangless_forgetsAndTellsChatOff() {
		channel.toggle(rex.getUniqueId());
		User<Player> left = user(false, -1);
		when(userManager.getUser(rex)).thenReturn(left);
		AsyncPlayerChatEvent event = chat(rex, "hi");

		listener.onChat(event);
		// Pinned: the line is cancelled off the main thread, before the gang check can run there (one line lost).
		assertTrue(event.isCancelled());
		capturedTask().run();

		assertFalse(channel.isOn(rex.getUniqueId()));
		verify(rex).sendMessage(messages.text("Social.Chat_Off", "&7Gang chat off."));
		verify(rex).chat("hi"); // T-264: the line is re-sent as public chat, not lost
		verify(mate, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a toggled player with no cached user (quit raced the deliver task) gets nothing re-sent as public chat")
	void toggledButUncached_neverRepostsAsPublicChat() {
		channel.toggle(rex.getUniqueId());
		when(userManager.getUser(rex)).thenReturn(null);
		AsyncPlayerChatEvent event = chat(rex, "hi");

		listener.onChat(event);
		capturedTask().run();

		assertFalse(channel.isOn(rex.getUniqueId()));
		verify(rex, never()).chat(anyString());
	}

	@Test
	@DisplayName("quitting clears the player's gang chat toggle")
	void quit_forgetsToggle() {
		channel.toggle(rex.getUniqueId());

		listener.onQuit(new PlayerQuitEvent(rex, "bye"));

		assertFalse(channel.isOn(rex.getUniqueId()));
	}

	@Test
	@DisplayName("noManagerReadOffMainThread: the async handler reads no manager; the gang is looked up in the task")
	void noManagerReadOffMainThread() {
		channel.toggleAllies(rex.getUniqueId());

		listener.onChat(chat(rex, "hi"));

		verifyNoInteractions(userManager, gangManager);
		verify(scheduler).runTask(eq(plugin), any(Runnable.class));
	}

	@Test
	@DisplayName("eventFiresOnMainThread: GangChatEvent is fired only when the scheduled task runs, never from the async handler")
	void eventFiresOnMainThread() {
		channel.toggle(rex.getUniqueId());

		listener.onChat(chat(rex, "hi"));
		verify(pluginManager, never()).callEvent(any(GangChatEvent.class));

		capturedTask().run();
		ArgumentCaptor<GangChatEvent> fired = ArgumentCaptor.forClass(GangChatEvent.class);
		verify(pluginManager).callEvent(fired.capture());
		assertEquals(GangChatChannel.GANG, fired.getValue().channel());
		assertEquals(1, fired.getValue().gangId());
		assertEquals("hi", fired.getValue().message());
		assertSame(rex, fired.getValue().sender());
	}

	@Test
	@DisplayName("a cancelled GangChatEvent delivers nothing, and the public line stays cancelled")
	void cancelledEvent_deliversNothing() {
		channel.toggle(rex.getUniqueId());
		doAnswer(invocation -> {
			((GangChatEvent) invocation.getArgument(0)).setCancelled(true);
			return null;
		}).when(pluginManager).callEvent(any(GangChatEvent.class));
		AsyncPlayerChatEvent event = chat(rex, "hi");

		listener.onChat(event);
		capturedTask().run();

		assertTrue(event.isCancelled());
		verify(rex, never()).sendMessage(anyString());
		verify(mate, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("snapshotRefreshedOnAllyChange: an alliance made between two messages is seen by the second")
	void snapshotRefreshedOnAllyChange() {
		channel.toggleAllies(rex.getUniqueId());
		when(vipers.isAlly(rats)).thenReturn(false);
		online = List.of(rex, mate, foe);
		Player rat = player("Rat", 2);
		online = List.of(rex, mate, rat);

		listener.onChat(chat(rex, "one"));
		ArgumentCaptor<Runnable> first = ArgumentCaptor.forClass(Runnable.class);
		verify(scheduler).runTask(eq(plugin), first.capture());
		first.getValue().run();
		verify(rat, never()).sendMessage(anyString());

		when(vipers.isAlly(rats)).thenReturn(true);
		listener.onChat(chat(rex, "two"));
		ArgumentCaptor<Runnable> both = ArgumentCaptor.forClass(Runnable.class);
		verify(scheduler, times(2)).runTask(eq(plugin), both.capture());
		both.getAllValues().get(1).run();

		verify(rat).sendMessage(anyString());
		verify(mate, times(2)).sendMessage(anyString());
	}

	/** The one task the listener scheduled; the main-thread delivery. */
	private Runnable capturedTask() {
		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(scheduler).runTask(eq(plugin), captor.capture());
		return captor.getValue();
	}

	private AsyncPlayerChatEvent chat(Player who, String message) {
		Set<Player> recipients = new HashSet<>(online);
		return new AsyncPlayerChatEvent(true, who, message, recipients);
	}

	/** A player in gang {@code gangId}, registered with the user manager; {@code 2} for {@code Foe}. */
	private Player player(String name, int gangId) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		User<Player> member = user(true, gangId);
		when(userManager.getUser(player)).thenReturn(member);
		return player;
	}

	private Gang gang(int id, String name) {
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(id);
		when(gang.getDisplayNameString()).thenReturn(name);
		when(gangManager.getGang(id)).thenReturn(gang);
		return gang;
	}

	@SuppressWarnings("unchecked")
	private static User<Player> user(boolean hasGang, int gangId) {
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(hasGang);
		when(user.getGangId()).thenReturn(gangId);
		return user;
	}

}
