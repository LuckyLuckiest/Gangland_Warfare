package org.luckyraven.gangland.gang.chat;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.support.W2P2Messages;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Gang chat: the per-player toggle, who receives a gang line, and the line itself (W2-P2). */
@DisplayName("GangChatChannel - gang chat toggle, recipients and line format")
class GangChatChannelTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangChatChannel        channel;
	private GangMessages           messages;

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
		bukkit = mockStatic(Bukkit.class);

		channel  = new GangChatChannel();
		messages = W2P2Messages.shipped(tempDir);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	@Test
	@DisplayName("recipients are the online players of that gang only: not the other gang, not a gangless player")
	void recipients_onlyTheGang() {
		Player ada = player();
		Player bo  = player();
		Player cy  = player();
		Player di  = player();
		Map<Player, Integer> gangOf = Map.of(ada, 1, bo, 1, cy, 2);

		List<Player> recipients = channel.recipients(1, List.of(ada, bo, cy, di), p -> gangOf.getOrDefault(p, -1));

		assertEquals(List.of(ada, bo), recipients);
	}

	@Test
	@DisplayName("the line is the Social.Chat_Format text with the sender and the message filled in")
	void format_fillsSenderAndMessage() {
		assertEquals("[Gang] Rex: hello", ChatColor.stripColor(channel.format(messages, "Rex", "hello")));
	}

	@Test
	@DisplayName("colour codes typed in the message stay literal: they are not rendered to the gang")
	void format_keepsMessageColourCodesLiteral() {
		assertEquals("[Gang] Rex: &chi", ChatColor.stripColor(channel.format(messages, "Rex", "&chi")));
	}

	@Test
	@DisplayName("toggling twice switches gang chat on and then off")
	void toggle_onThenOff() {
		UUID id = UUID.randomUUID();

		assertTrue(channel.toggle(id));
		assertTrue(channel.isOn(id));
		assertFalse(channel.toggle(id));
		assertFalse(channel.isOn(id));
	}

	@Test
	@DisplayName("forget clears gang chat for that player only")
	void forget_clearsOnePlayer() {
		UUID rex  = UUID.randomUUID();
		UUID mate = UUID.randomUUID();
		channel.toggle(rex);
		channel.toggle(mate);

		channel.forget(rex);

		assertFalse(channel.isOn(rex));
		assertTrue(channel.isOn(mate));
	}

	private static Player player() {
		return mock(Player.class);
	}

}
