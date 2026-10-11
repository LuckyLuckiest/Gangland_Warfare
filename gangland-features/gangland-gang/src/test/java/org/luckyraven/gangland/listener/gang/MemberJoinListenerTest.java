package org.luckyraven.gangland.listener.gang;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.events.user.UserDataInitEvent;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GP-45: the gang notice is shown once per login, to members of that gang only. */
@DisplayName("MemberJoinListener - gang notice on login")
class MemberJoinListenerTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private MemberManager          memberManager;
	private GangManager            gangManager;
	private MemberJoinListener     listener;

	@BeforeEach
	void setUp() throws IOException {
		settings = mockStatic(Settings.class);
		settings.when(Settings::getLanguagePicked).thenReturn("en");
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		memberManager = mock(MemberManager.class);
		gangManager   = mock(GangManager.class);
		listener      = new MemberJoinListener(mock(JavaPlugin.class), memberManager, gangManager,
		                                       GangUxTestMessages.shipped(tempDir));
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	@SuppressWarnings("unchecked")
	private Player login(int gangId, String notice) {
		UUID   uuid   = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.isOnline()).thenReturn(true);
		Member member = new Member(uuid);
		member.setGangId(gangId);
		when(memberManager.getMember(uuid)).thenReturn(member);

		Gang gang = mock(Gang.class);
		when(gang.getNotice()).thenReturn(notice);
		when(gang.getName()).thenReturn("Rats");
		when(gangManager.getGang(7)).thenReturn(gang);

		User<Player> user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		listener.onUserDataInit(new UserDataInitEvent(false, user));
		return player;
	}

	@Test
	@DisplayName("notice_oncePerLogin_membersOnly: a member sees it once, a gangless player never")
	void notice_oncePerLogin_membersOnly() {
		Player member = login(7, "Meet at the docks");
		verify(member, times(1)).sendMessage(contains("Meet at the docks"));

		Player stranger = login(-1, "Meet at the docks");
		verify(stranger, never()).sendMessage(contains("Meet at the docks"));

		Player noNotice = login(7, null);
		verify(noNotice, never()).sendMessage(contains("Meet"));
	}

}
