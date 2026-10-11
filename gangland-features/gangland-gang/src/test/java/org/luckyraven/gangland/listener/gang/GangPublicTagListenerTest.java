package org.luckyraven.gangland.listener.gang;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.chat.ChatSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.support.W2P2Messages;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The optional {glw_gang} public chat tag (W5-C). */
@DisplayName("GangPublicTagListener - {glw_gang} in the public chat format")
class GangPublicTagListenerTest {

	private static final String FORMAT = "<%1$s> {glw_gang} %2$s";

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private ChatSettings           chatSettings;
	private GangPublicTagListener  listener;
	private Player                 rex;
	private Player                 loner;
	private UserManager<Player>    userManager;
	private GangManager            gangManager;

	@BeforeEach
	@SuppressWarnings("unchecked")
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
		bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
		bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));

		userManager = mock(UserManager.class);
		gangManager = mock(GangManager.class);
		GangLimits limits = new GangLimits() {
			@Override
			public String chatPrefix(int gangId) {
				return "&8[&a3&8]";
			}
		};
		Gang gang = mock(Gang.class);
		when(gang.getDisplayNameString()).thenReturn("100%Vipers");
		when(gangManager.getGang(1)).thenReturn(gang);

		rex   = mock(Player.class);
		loner = mock(Player.class);
		User<Player> member = mock(User.class);
		when(member.hasGang()).thenReturn(true);
		when(member.getGangId()).thenReturn(1);
		when(userManager.getUser(rex)).thenReturn(member);
		User<Player> none = mock(User.class);
		when(none.hasGang()).thenReturn(false);
		when(userManager.getUser(loner)).thenReturn(none);

		chatSettings = new ChatSettings();
		listener     = new GangPublicTagListener(mock(JavaPlugin.class), userManager, gangManager, limits,
		                                         chatSettings);
		java.util.UUID rexId = java.util.UUID.randomUUID();
		when(rex.getUniqueId()).thenReturn(rexId);
		when(loner.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
		bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(rex, loner));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	@Test
	@DisplayName("offByDefault: Chat.Public_Tag.Enabled is false, so the format keeps its {glw_gang} token untouched")
	void offByDefault() {
		AsyncPlayerChatEvent event = chat(rex);

		listener.onChat(event);

		assertEquals(FORMAT, event.getFormat());
	}

	@Test
	@DisplayName("percentInDisplayName_escaped: a % in the gang name is doubled so String.format on the format still works")
	void percentInDisplayName_escaped() {
		chatSettings.set(false, true, "");
		listener.refresh();
		AsyncPlayerChatEvent event = chat(rex);

		listener.onChat(event);

		assertFalse(event.getFormat().contains("{glw_gang}"));
		assertTrue(event.getFormat().contains("100%%Vipers"), event.getFormat());
		String rendered = String.format(event.getFormat(), "Rex", "hi");
		assertEquals("<Rex> [3] 100%Vipers hi", ChatColor.stripColor(rendered));
	}

	@Test
	@DisplayName("a gangless sender gets Chat.No_Gang_Tag, escaped too")
	void gangless_noGangTag() {
		chatSettings.set(false, true, "50%");
		listener.refresh();
		AsyncPlayerChatEvent event = chat(loner);

		listener.onChat(event);

		assertEquals("<Rex> 50% hi", String.format(event.getFormat(), "Rex", "hi"));
	}

	@Test
	@DisplayName("noManagerReadOffMainThread: an async chat event splices the cached tag and never touches the managers")
	void noManagerReadOffMainThread() {
		chatSettings.set(false, true, "");
		listener.refresh();
		clearInvocations(userManager, gangManager);
		AsyncPlayerChatEvent event = chat(rex);

		listener.onChat(event);

		verifyNoInteractions(userManager, gangManager);
		assertTrue(event.getFormat().contains("100%%Vipers"), event.getFormat());
	}

	private AsyncPlayerChatEvent chat(Player who) {
		AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, who, "hi", new HashSet<>());
		event.setFormat(FORMAT);
		return event;
	}

}
