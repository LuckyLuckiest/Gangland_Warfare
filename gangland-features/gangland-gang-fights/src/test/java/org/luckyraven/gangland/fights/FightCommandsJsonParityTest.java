package org.luckyraven.gangland.fights;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import org.luckyraven.gangland.fights.command.arena.ArenaTreeFixture;
import org.luckyraven.gangland.fights.command.fight.FightContribution;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Keeps the fights module's {@code commands.json} in step with the verbs it registers: every usage names a real
 * verb with the same number of operands, and every verb has a usage. Same idea as the gang module's parity test.
 */
@DisplayName("commands.json - fight and arena usages match the registered verbs")
class FightCommandsJsonParityTest {

	private static final String FIGHT_PREFIX = "/glw gang fight ";
	private static final String ARENA_PREFIX = "/glw arena ";

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
	@DisplayName("everyUsage_namesARegisteredVerb")
	void everyUsage_namesARegisteredVerb() throws Exception {
		List<Argument> fightVerbs = fightVerbs();
		List<Argument> arenaVerbs = ArenaTreeFixture.verbs(mock(JavaPlugin.class));
		List<String>   problems   = new ArrayList<>();

		for (String usage : usages().values()) {
			if (usage.startsWith(FIGHT_PREFIX)) {
				check(usage, usage.substring(FIGHT_PREFIX.length()), fightVerbs, problems);
			} else if (usage.startsWith(ARENA_PREFIX)) {
				check(usage, usage.substring(ARENA_PREFIX.length()), arenaVerbs, problems);
			}
		}

		assertTrue(problems.isEmpty(), "usages that do not match a registered verb: " + problems);
	}

	@Test
	@DisplayName("everyVerb_hasAUsage")
	void everyVerb_hasAUsage() throws Exception {
		List<String> usages  = new ArrayList<>(usages().values());
		List<String> missing = new ArrayList<>();

		for (Argument verb : fightVerbs()) {
			if (usages.stream().noneMatch(u -> u.startsWith(FIGHT_PREFIX + label(verb)))) {
				missing.add("fight " + label(verb));
			}
		}
		for (Argument verb : ArenaTreeFixture.verbs(mock(JavaPlugin.class))) {
			if (usages.stream().noneMatch(u -> u.startsWith(ARENA_PREFIX + label(verb)))) {
				missing.add("arena " + label(verb));
			}
		}

		assertTrue(missing.isEmpty(), "registered verbs with no commands.json usage: " + missing);
		assertTrue(usages.contains("/glw gang fight"), "the fight parent has a usage");
		assertTrue(usages.contains("/glw arena"), "the arena root has a usage");
	}

	/** The verb word must be registered and the number of operands in the usage must equal the operand chain depth. */
	private static void check(String usage, String rest, List<Argument> verbs, List<String> problems) {
		String[] words = rest.trim().split("\\s+");
		Argument verb  = verbs.stream()
				.filter(v -> Arrays.asList(v.getArguments()).contains(words[0]))
				.findFirst()
				.orElse(null);

		if (verb == null) {
			problems.add(usage);
			return;
		}

		int      depth   = 0;
		Argument current = verb;
		while (!current.getNode().getChildren().isEmpty()
		       && current.getNode().getChildren().get(0).getData() instanceof OptionalArgument next) {
			depth++;
			current = next;
		}
		if (depth != words.length - 1) {
			problems.add(usage + " (operands " + (words.length - 1) + " vs chain " + depth + ")");
		}
	}

	private static String label(Argument verb) {
		return verb.getArguments()[0];
	}

	private static List<Argument> fightVerbs() {
		Tree<Argument> tree   = new Tree<>();
		JavaPlugin     plugin = mock(JavaPlugin.class);
		Argument       gang   = new Argument(plugin, "gang", tree, (argument, sender, args) -> {
		});
		Argument       fight  = new FightContribution(plugin).create(tree, gang).get(0);

		assertEquals("fight", label(fight));
		List<Argument> verbs = new ArrayList<>();
		for (Tree.Node<Argument> child : fight.getNode().getChildren()) {
			verbs.add(child.getData());
		}
		return verbs;
	}

	private static Map<String, String> usages() throws Exception {
		Map<String, String> usages = new LinkedHashMap<>();

		try (InputStream in = FightCommandsJsonParityTest.class.getResourceAsStream("/commands.json")) {
			assertTrue(in != null, "the fights module ships commands.json on its classpath root");
			JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);

			for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
				usages.put(entry.getKey(), entry.getValue().getAsJsonObject().get("usage").getAsString().trim());
			}
		}

		return usages;
	}
}
