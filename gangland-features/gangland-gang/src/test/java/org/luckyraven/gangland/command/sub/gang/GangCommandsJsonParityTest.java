package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.membership.GangLeadership;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.command.option.GangOptionContribution;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Keeps the gang module's {@code commands.json} in step with the verbs {@link GangCommand} registers: every
 * {@code /glw gang ...} usage must name a real verb, and every real verb must have a usage (the help index is built
 * from this file).
 */
@DisplayName("commands.json - gang usages match the registered verbs")
class GangCommandsJsonParityTest {

	private static final String GANG_PREFIX = "/glw gang ";
	private static final String ALLY_PREFIX = "/glw gang ally ";

	/** The root help page; the gang root executor serves it, it is not a registered child. */
	private static final String HELP_VERB = "help";

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;

	@BeforeEach
	void setUp() {
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

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

		Command.setInformationManager(mock(InformationManager.class));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("every /glw gang <verb> usage names a registered gang verb, and every /glw gang ally <verb> an ally verb")
	void everyUsage_namesARegisteredVerb() throws Exception {
		GangCommand      command  = registeredGangCommand();
		List<Argument>   gangVerbs = children(command.getArgument());
		List<Argument>   allyVerbs = children(findVerb(gangVerbs, "ally"));
		List<String>     unknown  = new ArrayList<>();

		for (String usage : gangUsages().values()) {
			if (!usage.startsWith(GANG_PREFIX)) continue;

			String verb = firstWord(usage.substring(GANG_PREFIX.length()));
			if (verb.isEmpty() || HELP_VERB.equals(verb)) continue;

			if (!namesAnyVerb(gangVerbs, verb)) {
				unknown.add(usage);
				continue;
			}

			if (verb.equals("ally") && usage.startsWith(ALLY_PREFIX)) {
				String allyVerb = firstWord(usage.substring(ALLY_PREFIX.length()));
				if (!allyVerb.isEmpty() && !namesAnyVerb(allyVerbs, allyVerb)) unknown.add(usage);
			}
		}

		assertTrue(unknown.isEmpty(), "usages naming no registered verb: " + unknown);
	}

	@Test
	@DisplayName("every registered gang verb (and every ally verb) has at least one commands.json usage")
	void everyVerb_hasAUsage() throws Exception {
		GangCommand    command   = registeredGangCommand();
		List<Argument> gangVerbs = children(command.getArgument());
		List<Argument> allyVerbs = children(findVerb(gangVerbs, "ally"));
		List<String>   usages    = new ArrayList<>(gangUsages().values());
		List<String>   missing   = new ArrayList<>();

		for (Argument verb : gangVerbs) {
			if (aliases(verb).contains(HELP_VERB)) continue;
			if (usages.stream().noneMatch(usage -> usage.startsWith(GANG_PREFIX)
			                                       && aliases(verb).contains(firstWord(usage.substring(GANG_PREFIX.length()))))) {
				missing.add(aliases(verb).get(0));
			}
		}

		for (Argument verb : allyVerbs) {
			if (usages.stream().noneMatch(usage -> usage.startsWith(ALLY_PREFIX)
			                                       && aliases(verb).contains(firstWord(usage.substring(ALLY_PREFIX.length()))))) {
				missing.add("ally " + aliases(verb).get(0));
			}
		}

		assertTrue(missing.isEmpty(), "registered verbs with no commands.json usage: " + missing);
	}

	private static final String OPTION_GANG_PREFIX = "/glw option gang ";

	@Test
	@DisplayName("every /glw option gang <verb> usage names a staff verb, and every staff verb has a usage")
	void optionGangTree_matchesUsages() throws Exception {
		Tree<Argument> tree = new Tree<>();
		GangOptionContribution contribution = new GangOptionContribution(mock(JavaPlugin.class),
		                                                                 mock(UserManager.class),
		                                                                 mock(MemberManager.class),
		                                                                 mock(GangManager.class),
		                                                                 mock(RankManager.class));
		List<Argument> created = contribution.create(tree, mock(Argument.class));
		assertTrue(created.size() == 1, "the contribution attaches one gang argument under option");
		List<Argument> verbs = children(created.get(0));

		List<String> usages = new ArrayList<>();
		try (InputStream in = GangCommandsJsonParityTest.class.getResourceAsStream("/commands.json")) {
			JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
				if (!entry.getKey().startsWith("option_gang")) continue;
				usages.add(entry.getValue().getAsJsonObject().get("usage").getAsString().trim());
			}
		}

		List<String> unknown = new ArrayList<>();
		for (String usage : usages) {
			assertTrue(usage.startsWith(OPTION_GANG_PREFIX), usage);
			if (!namesAnyVerb(verbs, firstWord(usage.substring(OPTION_GANG_PREFIX.length())))) unknown.add(usage);
		}
		List<String> missing = new ArrayList<>();
		for (Argument verb : verbs) {
			if (usages.stream().noneMatch(usage -> aliases(verb).contains(
					firstWord(usage.substring(OPTION_GANG_PREFIX.length()))))) {
				missing.add(aliases(verb).get(0));
			}
		}

		assertTrue(unknown.isEmpty(), "option gang usages naming no staff verb: " + unknown);
		assertTrue(missing.isEmpty(), "staff verbs with no commands.json usage: " + missing);
		assertTrue(verbs.size() >= 8, "rank plus the seven staff stubs: " + verbs.size());
	}

	private static GangCommand registeredGangCommand() {
		GangCommand command = new GangCommand(mock(JavaPlugin.class),
		                                      mock(UserManager.class), mock(UserManager.class),
		                                      mock(GangManager.class), mock(MemberManager.class),
		                                      mock(RankManager.class), mock(RepositoryRegistry.class),
		                                      mock(PermissionManager.class), mock(DependencyContainer.class), null,
		                                      mock(GangRules.class), mock(GangMessages.class),
		                                      new GangChatToggles(), new GangWorldPolicy(mock(GangRules.class)), mock(GangMembershipService.class),
		                       mock(GangDisbandService.class), mock(GangLeadership.class), new CombatTag(),
		                       new GangStatsService(), new GangLimits(), new GangLevelTable(),
		                       new GangSafehouseService(), mock(FriendlyFirePolicy.class));
		command.initializeArguments();
		return command;
	}

	/** Usage strings of every {@code gang*} entry, keyed by entry id. */
	private static Map<String, String> gangUsages() throws Exception {
		Map<String, String> usages = new LinkedHashMap<>();

		try (InputStream in = GangCommandsJsonParityTest.class.getResourceAsStream("/commands.json")) {
			assertTrue(in != null, "the gang module ships commands.json on its classpath root");
			JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);

			for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
				if (!entry.getKey().startsWith("gang")) continue;
				usages.put(entry.getKey(), entry.getValue().getAsJsonObject().get("usage").getAsString().trim());
			}
		}

		return usages;
	}

	private static String firstWord(String text) {
		String trimmed = text.trim();
		int    space   = trimmed.indexOf(' ');
		return space < 0 ? trimmed : trimmed.substring(0, space);
	}

	private static List<String> aliases(Argument verb) {
		return Arrays.asList(verb.getArguments());
	}

	private static boolean namesAnyVerb(List<Argument> verbs, String word) {
		return verbs.stream().anyMatch(verb -> aliases(verb).contains(word));
	}

	private static Argument findVerb(List<Argument> verbs, String word) {
		return verbs.stream()
				.filter(verb -> aliases(verb).contains(word))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no registered verb named " + word));
	}

	private static List<Argument> children(Argument parent) {
		List<Argument> result = new ArrayList<>();
		for (Tree.Node<Argument> child : parent.getNode().getChildren()) {
			result.add(child.getData());
		}
		return result;
	}

}
