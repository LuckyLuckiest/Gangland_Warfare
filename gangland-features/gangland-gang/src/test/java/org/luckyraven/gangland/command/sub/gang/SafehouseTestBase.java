package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.WaypointTeleport;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.safehouse.Safehouse;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.gang.support.W2P2Messages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Shared rig of the safehouse and rally command tests: one gang (id 1), a leader, a real service over fakes. */
abstract class SafehouseTestBase {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	MockedStatic<Bukkit>   bukkit;
	MockedStatic<Settings> settings;

	GangMessages                   messages;
	UserManager<Player>            users;
	MemberManager                  members;
	GangManager                    gangs;
	GangRules                      rules;
	GangWorldPolicy                policy;
	GangLimits                     limits;
	IRepository<Safehouse>         repository;
	PluginManager                  pluginManager;
	GangSafehouseService           service;
	World                          world;
	Gang                           vipers;
	Player                         player;
	User<Player>                   user;
	final List<String>             chat       = new ArrayList<>();
	final List<Waypoint>           teleported = new ArrayList<>();
	final AtomicLong               now        = new AtomicLong(1_000_000L);
	Location                       standingAt;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUpRig() throws IOException {
		GangSettings.bind(new FakeGangSettingsContract());
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
		Server server = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		world = mock(World.class);
		when(world.getName()).thenReturn("world");
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
		// XMaterial reads the server version when SafeSpot first loads it
		bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.16.5-R0.1-SNAPSHOT");
		bukkit.when(Bukkit::getVersion).thenReturn("git-Spigot-test (MC: 1.16.5)");
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.of());

		Command.setInformationManager(mock(InformationManager.class));
		messages = W2P2Messages.shipped(tempDir);

		users      = mock(UserManager.class);
		members    = mock(MemberManager.class);
		gangs      = mock(GangManager.class);
		rules      = mock(GangRules.class);
		policy     = mock(GangWorldPolicy.class);
		limits     = mock(GangLimits.class);
		repository = mock(IRepository.class);

		when(rules.isSafehouseEnabled()).thenReturn(true);
		when(rules.getSafehouseWarmUpSeconds()).thenReturn(5);
		when(rules.getSafehouseCooldownSeconds()).thenReturn(60);
		when(rules.getSafehouseRallyConsentSeconds()).thenReturn(60);
		when(rules.isSafehouseSafeCheck()).thenReturn(false);
		when(policy.enabledIn(any())).thenReturn(true);

		vipers = mock(Gang.class);
		when(vipers.getId()).thenReturn(1);
		when(gangs.getGang(1)).thenReturn(vipers);

		service = new GangSafehouseService(mock(JavaPlugin.class), repository, users, members, gangs, rules, messages,
		                                   policy, now::get, (plugin, who, waypoint, during) -> {
			teleported.add(waypoint);
			return CompletableFuture.completedFuture(new WaypointTeleport.TeleportResult(true, who, waypoint));
		});

		standingAt = new Location(world, 10.5, 64, 10.5, 90F, 5F);
		player     = member("Rex", standingAt, true);
		user       = users.getUser(player);
		when(vipers.getOnlineMembers(any())).thenAnswer(invocation -> new ArrayList<>(onlineMembers));
		onlineMembers.add(user);
	}

	final List<User<Player>> onlineMembers = new ArrayList<>();

	@AfterEach
	void tearDownRig() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
		chat.clear();
	}

	/** An online member of gang 1; {@code leader} decides whether the rank holds every gang node (top rank). */
	@SuppressWarnings("unchecked")
	Player member(String name, Location at, boolean leader) {
		Player who = mock(Player.class);
		UUID   id  = UUID.randomUUID();
		when(who.getUniqueId()).thenReturn(id);
		when(who.getName()).thenReturn(name);
		when(who.getLocation()).thenReturn(at);
		when(who.getWorld()).thenReturn(world);
		if (who == player || name.equals("Rex")) {
			doAnswer(invocation -> chat.add(invocation.getArgument(0))).when(who).sendMessage(anyString());
		}

		User<Player> asUser = mock(User.class);
		when(asUser.hasGang()).thenReturn(true);
		when(asUser.getGangId()).thenReturn(1);
		when(asUser.getUser()).thenReturn(who);
		when(users.getUser(who)).thenReturn(asUser);

		Member row = mock(Member.class);
		when(row.getRank()).thenReturn(leader ? new Rank("owner", 1) : lowRank());
		when(members.getMember(id)).thenReturn(row);
		return who;
	}

	/** A rank with a child above it is not the leader, so it holds no gang node unless the player is granted one. */
	static Rank lowRank() {
		Rank low = new Rank("member", 2);
		low.getNode().add(new Rank("officer", 3).getNode());
		return low;
	}

	void makeRexAMember() {
		Member row = mock(Member.class);
		when(row.getRank()).thenReturn(lowRank());
		when(members.getMember(player.getUniqueId())).thenReturn(row);
	}

	Argument safehouseCommand() {
		return new GangSafehouseCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), service, limits,
		                                policy);
	}

	Argument rallyCommand() {
		return new GangRallyCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), service);
	}

	/** The child of {@code parent} labelled {@code label}. */
	static Argument child(Argument parent, String label) {
		return parent.getNode().getChildren().stream().map(node -> node.getData())
				.filter(data -> Arrays.asList(data.getArguments()).contains(label)).findFirst().orElseThrow();
	}

	/** The free-text child of {@code parent}. */
	static Argument freeText(Argument parent) {
		return parent.getNode().getChildren().stream().map(node -> node.getData())
				.filter(data -> data instanceof OptionalArgument).findFirst().orElseThrow();
	}

	String say(String key, Object... pairs) {
		return messages.text(key, "", pairs);
	}
}
