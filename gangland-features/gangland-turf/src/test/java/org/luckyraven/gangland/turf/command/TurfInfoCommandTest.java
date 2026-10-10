package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.InMemoryTurfSequence;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /glw turf info [id|name]}: the optional id-or-name argument (completion, lookup, not-found) and the capture
 * reason line on the card. Built over a real {@link TurfManager} with the shipped turf_messages.yml, so the reason
 * sentence is the one every other surface shows.
 */
@DisplayName("TurfInfoCommand - id or name argument and the capture reason line")
class TurfInfoCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> staticSettings;
	private TurfManager            turfs;
	private FakeGangLookup         gangs;
	private WandSelectionManager   selections;
	private TurfMessageContract    messages;
	private CaptureEligibility     eligibility;
	private TurfMessages           text;
	private TurfInfoCommand        command;

	@BeforeEach
	void setUp() throws IOException {
		staticSettings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		staticSettings.when(Settings::getMoneySymbol).thenReturn("$");
		staticSettings.when(() -> Settings.formatAmount(any(BigDecimal.class))).thenReturn("10");

		gangs      = new FakeGangLookup();
		selections = new WandSelectionManager();
		messages   = mock(TurfMessageContract.class);
		when(messages.formatDuration(anyLong())).thenReturn("14m");

		CaptureSettings capture = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		eligibility = new CaptureEligibility(gangs, capture);
		FileManager fileManager = YamlFixture.fileManager(tempDir, "turf_messages",
		                                                  YamlFixture.shipped("/turf/turf_messages.yml"));
		text = new TurfMessages(fileManager, messages, capture);

		gangs.register(CaptureFixtures.gang(7));

		long                 now        = System.currentTimeMillis();
		InMemoryTurfRepository repository = new InMemoryTurfRepository();
		// Docks: unclaimed, never captured, so attackable.
		repository.seed(new Turf(1, "Docks", new CuboidRegion("world", 0, 0, 10, 10), null,
		                         BigDecimal.TEN, now, 0L));
		// Harbor: owned by gang 7, captured a minute ago, so shielded by the cooldown.
		repository.seed(new Turf(2, "Harbor", new CuboidRegion("world", 20, 0, 30, 10), 7,
		                         BigDecimal.TEN, now, now - 60_000L));
		turfs = new TurfManager(repository, new InMemoryTurfSequence());
		turfs.initialize();

		JavaPlugin     plugin = PluginMocks.plugin(tempDir);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			command = new TurfInfoCommand(plugin, tree, parent, turfs, gangs, selections, messages,
			                              mock(TurfSettings.class), eligibility, text);
		}
	}

	@AfterEach
	void releaseStatics() {
		staticSettings.close();
	}

	@Test
	@DisplayName("completion offers every turf id and every turf name")
	void completion_offersIdsAndNames() {
		CommandSender sender = mock(CommandSender.class);

		List<String> suggestions = command.idArgument().getArgumentString(sender);

		assertTrue(suggestions.containsAll(List.of("1", "2", "Docks", "Harbor")), suggestions.toString());
	}

	@Test
	@DisplayName("an integer argument renders the turf with that id")
	void info_byId_rendersThatTurf() {
		CommandSender sender = mock(CommandSender.class);

		command.idArgument().executeArgument(sender, new String[]{"turf", "info", "2"});

		verify(messages).send(sender, "TURF_INFO_HEADER", "turf", "Harbor", "id", "2");
	}

	@Test
	@DisplayName("a name argument renders the turf with that name, ignoring case")
	void info_byNameIgnoringCase_rendersThatTurf() {
		CommandSender sender = mock(CommandSender.class);

		command.idArgument().executeArgument(sender, new String[]{"turf", "info", "docks"});

		verify(messages).send(sender, "TURF_INFO_HEADER", "turf", "Docks", "id", "1");
	}

	@Test
	@DisplayName("an unknown id or name reports turf not found")
	void info_unknown_saysNotFound() {
		CommandSender sender = mock(CommandSender.class);

		command.idArgument().executeArgument(sender, new String[]{"turf", "info", "nope"});

		verify(messages).send(sender, "TURF_NOT_FOUND", "turf", "nope");
	}

	@Test
	@DisplayName("an unclaimed turf that was never captured shows the attackable reason line")
	void card_showsAttackableReason() {
		CommandSender sender = mock(CommandSender.class);

		command.idArgument().executeArgument(sender, new String[]{"turf", "info", "1"});

		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(sender, atLeastOnce()).sendMessage(lines.capture());
		assertTrue(lines.getAllValues().stream().anyMatch(line -> line.contains("attackable")),
		           lines.getAllValues().toString());
	}

	@Test
	@DisplayName("a turf inside its capture cooldown shows the just-captured reason line")
	void card_showsCooldownReason() {
		CommandSender sender = mock(CommandSender.class);

		command.idArgument().executeArgument(sender, new String[]{"turf", "info", "2"});

		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(sender, atLeastOnce()).sendMessage(lines.capture());
		assertTrue(lines.getAllValues().stream().anyMatch(line -> line.contains("just captured")),
		           lines.getAllValues().toString());
	}
}
