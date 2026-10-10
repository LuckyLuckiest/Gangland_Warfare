package org.luckyraven.gangland.command.sub.gang;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** {@code /glw gang list [page]}: every gang, most members first, eight per page, with a clickable next page. */
@DisplayName("GangListCommand - /glw gang list [page]")
class GangListCommandTest {

	/** name, member count; the expected order is members desc, then name. */
	private static final String[][] SEVEN_GANGS = {
			{"Delta", "3"}, {"Alpha", "5"}, {"Bravo", "5"}, {"Echo", "1"}, {"Foxtrot", "2"}, {"Golf", "4"},
			{"Hotel", "6"}, {"India", "0"}, {"Juliet", "7"}, {"Kilo", "0"}
	};

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangMessages           messages;
	private GangManager            gangManager;
	private Player                 player;

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

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

		Command.setInformationManager(mock(InformationManager.class));
		messages = GangTestMessages.shipped(tempDir);

		gangManager = mock(GangManager.class);
		player      = mock(Player.class);
		when(player.hasPermission(anyString())).thenReturn(true);
		when(player.spigot()).thenReturn(mock(Player.Spigot.class));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("page 1 of ten gangs lists eight rows, most members first then by name, and a next-page button")
	void pageOne_sortedAndPaged() throws Exception {
		useGangs(SEVEN_GANGS);
		Argument list = list();

		list.executeArgument(player, new String[]{"gang", "list"});

		List<String> rows = sentRows(player);
		assertEquals(9, rows.size(), "eight gang rows and the next-page footer: " + rows);
		assertEquals(List.of("Juliet", "Hotel", "Alpha", "Bravo", "Golf", "Delta", "Foxtrot", "Echo"),
		             rows.subList(0, 8).stream().map(row -> row.split(" ")[0]).toList(), rows.toString());
		assertTrue(rows.get(8).contains("Next"), rows.get(8));
		assertEquals("/glw gang list 2", lastClick(player));
	}

	@Test
	@DisplayName("page 2 lists the rest and shows no next-page button")
	void pageTwo_restWithoutFooter() throws Exception {
		useGangs(SEVEN_GANGS);
		Argument page = page(list());

		page.executeArgument(player, new String[]{"gang", "list", "2"});

		List<String> rows = sentRows(player);
		assertEquals(List.of("India", "Kilo"), rows.stream().map(row -> row.split(" ")[0]).toList(), rows.toString());
	}

	@Test
	@DisplayName("with no gangs the list says so")
	void noGangs_emptyMessage() throws Exception {
		useGangs();

		list().executeArgument(player, new String[]{"gang", "list"});

		verify(player).sendMessage(messages.text("List.Empty", "&7There are no gangs yet."));
	}

	@Test
	@DisplayName("the page argument completes to every page number")
	void pageArgument_completesPages() throws Exception {
		useGangs(new String[][]{
				{"A1", "1"}, {"A2", "1"}, {"A3", "1"}, {"A4", "1"}, {"A5", "1"}, {"A6", "1"}, {"A7", "1"}, {"A8", "1"},
				{"A9", "1"}, {"B1", "1"}
		});

		assertEquals(List.of("1", "2"), page(list()).getArgumentString(player));
	}

	@Test
	@DisplayName("T-240: a one-member gang row reads 'members: 1', not '1 members'")
	void oneMemberRow_readsMembersColon() throws Exception {
		useGangs(new String[][]{{"Solo", "1"}});

		list().executeArgument(player, new String[]{"gang", "list"});

		List<String> rows = sentRows(player);
		assertTrue(rows.get(0).contains("members: 1"), rows.toString());
	}

	@Test
	@DisplayName("T-240: the shipped List.Row is the members-colon line")
	void shippedRow_isMembersColonLine() {
		assertEquals("&f%gang% &7- members: %members% - online: %online%",
		             messages.text("List.Row", "unused fallback"));
	}

	@Test
	@DisplayName("the list verb is its own verb, not an alias of members")
	void listVerb_notAliasOfMembers() {
		Argument members = new GangMembersCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class),
		                                          mock(UserManager.class), gangManager);

		assertEquals(List.of("members"), List.of(members.getArguments()));
		assertEquals(List.of("list"), List.of(list().getArguments()));
	}

	private void useGangs(String[][] gangs) {
		Collection<Gang> all = new ArrayList<>();
		for (String[] entry : gangs) {
			all.add(gang(entry[0], Integer.parseInt(entry[1])));
		}
		when(gangManager.getAll()).thenReturn(all);
	}

	private void useGangs() {
		when(gangManager.getAll()).thenReturn(List.of());
	}

	private static Gang gang(String name, int members) {
		Gang gang = mock(Gang.class);
		when(gang.getName()).thenReturn(name);
		when(gang.getDisplayNameString()).thenReturn(name);
		List<Member> roster = new ArrayList<>();
		for (int i = 0; i < members; i++) {
			roster.add(mock(Member.class));
		}
		when(gang.getMembers()).thenReturn(roster);
		return gang;
	}

	private Argument list() {
		return new GangListCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), gangManager,
		                           mock(UserManager.class), messages);
	}

	private static Argument page(Argument list) {
		return list.getNode().getChildren().get(0).getData();
	}

	/** Plain text of every clickable row or footer sent through {@code spigot()}, in order. */
	private static List<String> sentRows(CommandSender sender) {
		var captor = ArgumentCaptor.forClass(BaseComponent[].class);
		verify(sender.spigot(), atLeast(0)).sendMessage(captor.capture());
		return captor.getAllValues().stream().map(components -> ChatColor.stripColor(
				BaseComponent.toLegacyText(components))).toList();
	}

	private static String lastClick(CommandSender sender) {
		var captor = ArgumentCaptor.forClass(BaseComponent[].class);
		verify(sender.spigot(), atLeast(1)).sendMessage(captor.capture());
		BaseComponent[] last = captor.getValue();
		for (BaseComponent component : last) {
			ClickEvent click = component.getClickEvent();
			if (click != null) return click.getValue();
		}
		return null;
	}

}
