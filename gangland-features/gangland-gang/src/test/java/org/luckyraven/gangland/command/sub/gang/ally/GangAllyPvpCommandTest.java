package org.luckyraven.gangland.command.sub.gang.ally;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** The body of {@code /glw gang ally pvp}: it flips only the sender's own alliance row. */
@DisplayName("gang ally pvp")
class GangAllyPvpCommandTest {

	private MockedStatic<Bukkit> bukkit;

	private final JavaPlugin     plugin        = mock(JavaPlugin.class);
	private final Tree<Argument> tree          = new Tree<>();
	private final Argument       parent        = mock(Argument.class);
	private final GangManager    gangManager   = mock(GangManager.class);
	private final MemberManager  memberManager = mock(MemberManager.class);
	private final GangMessages   messages      = mock(GangMessages.class);

	private FriendlyFirePolicy policy;
	private Gang               one;
	private Gang               two;
	private Player             player;

	@BeforeEach
	void setUp() {
		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());

		one = new Gang(1);
		two = new Gang(2);
		when(gangManager.getGang(1)).thenReturn(one);
		when(gangManager.getGang(2)).thenReturn(two);
		when(gangManager.getAll()).thenReturn(List.of(one, two));
		when(messages.text(anyString(), anyString(), any(), any())).thenReturn("text");

		UUID id = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.hasPermission(anyString())).thenReturn(true);
		Member member = new Member(id);
		member.setGangId(1);
		when(memberManager.getMember(id)).thenReturn(member);

		policy = new FriendlyFirePolicy(memberManager, gangManager, null, null, null, messages);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("ally pvp flips only the sender's own row and creates it when only the ally lists the alliance")
	void allyPvpFlipsOwnRow() {
		// only gang 2 lists the alliance, so gang 1 has no row yet
		GangAlliance theirs = new GangAlliance(two, one, 1L);
		two.addAlly(theirs);
		two.setName("Bravo");
		GangAllyPvpCommand command = new GangAllyPvpCommand(plugin, tree, parent, gangManager, policy);

		command.change(player, "Bravo", "on");

		GangAlliance mine = FriendlyFirePolicy.findRow(one, two);
		assertNotNull(mine);
		assertTrue(mine.isFriendlyFire());
		assertFalse(theirs.isFriendlyFire(), "the other side's row is never touched");
		assertFalse(FriendlyFirePolicy.row(two, one));

		command.change(player, "Bravo", "off");
		assertFalse(mine.isFriendlyFire());
	}
}
