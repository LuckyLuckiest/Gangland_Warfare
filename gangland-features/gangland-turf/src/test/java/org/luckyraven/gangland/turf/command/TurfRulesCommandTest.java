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
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /glw turf rules}: the header and the five sentences of how turf wars work, with this server's numbers read
 * live from TurfRules and TurfSettings. Built over the shipped turf_messages.yml and turf_rules.yml, with a
 * TurfSettings mock (10-minute logout window, 10-minute income interval, 250 income) and a duration contract that
 * renders seconds as {@code Ns}.
 */
@DisplayName("TurfRulesCommand - /glw turf rules sentences with live numbers")
class TurfRulesCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> staticSettings;
	private FileManager            fileManager;
	private TurfRules              rules;
	private TurfSettings           turfSettings;
	private TurfMessageContract    messages;
	private TurfRulesCommand       command;

	@BeforeEach
	void setUp() throws IOException {
		staticSettings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		staticSettings.when(Settings::getMoneySymbol).thenReturn("$");
		staticSettings.when(() -> Settings.formatAmount(any(BigDecimal.class))).thenReturn("250");

		messages = mock(TurfMessageContract.class);
		when(messages.formatDuration(anyLong())).thenAnswer(invocation -> invocation.getArgument(0) + "s");

		turfSettings = mock(TurfSettings.class);
		when(turfSettings.getCapturePostLogoffProtectionMinutes()).thenReturn(10);
		when(turfSettings.getIncomeIntervalMinutes()).thenReturn(10);
		when(turfSettings.getDefaultIncomeAmount()).thenReturn(BigDecimal.valueOf(250));

		fileManager = YamlFixture.fileManager(tempDir, "turf_rules", YamlFixture.shipped("/turf/turf_rules.yml"));
		rules       = new TurfRules(fileManager);

		command = build();
	}

	private TurfRulesCommand build() throws IOException {
		FileManager messageFiles = YamlFixture.fileManager(tempDir, "turf_messages",
		                                                   YamlFixture.shipped("/turf/turf_messages.yml"));
		CaptureSettings capture = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		TurfMessages    text    = new TurfMessages(messageFiles, messages, capture);
		JavaPlugin      plugin  = PluginMocks.plugin(tempDir);
		Tree<Argument>  tree    = new Tree<>();
		Argument        parent  = mock(Argument.class);
		when(parent.getPermission()).thenReturn("gangland.command.turf");

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			return new TurfRulesCommand(plugin, tree, parent, rules, turfSettings, text, messages);
		}
	}

	@AfterEach
	void releaseStatics() {
		staticSettings.close();
	}

	/** Runs the action and returns every line it sent, the header first. */
	private List<String> run(CommandSender sender) {
		command.action().accept(null, sender, new String[]{"turf", "rules"});

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(sender, atLeastOnce()).sendMessage(captor.capture());
		return captor.getAllValues();
	}

	@Test
	@DisplayName("the shipped rules read as a header and five sentences carrying this server's numbers")
	void onlineWindow_fiveSentencesWithNumbers() {
		CommandSender sender = mock(CommandSender.class);

		List<String> lines = run(sender);

		assertEquals(6, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("600s"), lines.get(1));
		assertTrue(lines.get(2).contains("120s"), lines.get(2));
		assertTrue(lines.get(3).contains("15s"), lines.get(3));
		assertTrue(lines.get(4).contains("4") && lines.get(4).contains("60s") && lines.get(4).contains("600s"),
		           lines.get(4));
		for (String expected : List.of("2", "1", "6", "250", "600s", "86400s")) {
			assertTrue(lines.get(5).contains(expected), expected + " missing from " + lines.get(5));
		}
	}

	@Test
	@DisplayName("owners-offline turfs drop the wake-up sentence and say when a turf opens")
	void offlineWindow_skipsWakeUp() throws IOException {
		Files.writeString(tempDir.resolve("turf_rules.yml"), "Capture:\n   Window: OWNERS_OFFLINE\n",
		                  StandardCharsets.UTF_8);
		fileManager.getFile("turf_rules").reloadData();
		rules.initialize();
		CommandSender sender = mock(CommandSender.class);

		List<String> lines = run(sender);

		assertEquals(5, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("offline for") && lines.get(1).contains("600s"), lines.get(1));
		assertFalse(lines.get(1).contains("shielded for"), lines.get(1));
	}

	@Test
	@DisplayName("zero presence and zero guards drop their clauses instead of printing a zero")
	void zeroRulesDropTheirClauses() throws IOException {
		Files.writeString(tempDir.resolve("turf_rules.yml"),
		                  "Capture:\n   Presence_Seconds: 0\n   Guard_Weight_Cap: 0\n", StandardCharsets.UTF_8);
		fileManager.getFile("turf_rules").reloadData();
		rules.initialize();
		CommandSender sender = mock(CommandSender.class);

		List<String> lines = run(sender);
		String       all   = String.join("\n", lines).toLowerCase(Locale.ROOT);

		assertEquals(6, lines.size(), lines.toString());
		assertTrue(lines.get(3).contains("A capture starts"), lines.get(3));
		assertFalse(all.contains("you count after"), all);
		assertFalse(all.contains("guard"), all);
	}

	@Test
	@DisplayName("a zero logout window and zero per-level drop their clauses instead of printing a zero")
	void zeroWindowAndPerLevelDropTheirClauses() throws IOException {
		when(turfSettings.getCapturePostLogoffProtectionMinutes()).thenReturn(0);
		Files.writeString(tempDir.resolve("turf_rules.yml"), "Turf_Cap:\n   Per_Level: 0\n", StandardCharsets.UTF_8);
		fileManager.getFile("turf_rules").reloadData();
		rules.initialize();
		CommandSender sender = mock(CommandSender.class);

		List<String> lines = run(sender);
		String       all   = String.join("\n", lines);

		assertEquals(6, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("while one of its owners is online."), lines.get(1));
		assertFalse(all.contains("for 0s"), all);
		assertFalse(all.contains("plus 0"), all);
		assertTrue(lines.get(5).contains("at most"), lines.get(5));
	}

	@Test
	@DisplayName("a reloaded turf_rules.yml shows its new numbers on the next call")
	void reloadIsSeen() throws IOException {
		Files.writeString(tempDir.resolve("turf_rules.yml"), "Capture:\n   Wake_Up_Shield_Seconds: 300\n",
		                  StandardCharsets.UTF_8);
		fileManager.getFile("turf_rules").reloadData();
		rules.initialize();
		CommandSender sender = mock(CommandSender.class);

		List<String> lines = run(sender);

		assertTrue(lines.get(2).contains("300s"), lines.get(2));
		assertFalse(lines.get(2).contains("120s"), lines.get(2));
	}

}
