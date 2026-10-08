package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CopRadioMessages — Lines.<key> pool, root special keys, empty means silent, English fallback, es resolves")
class CopRadioMessagesTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("lines(key) reads Lines.<key>; Format/Compass/Sides read at the document root")
	void linesAndRootKeys_parsed() throws IOException {
		CopRadioMessages messages = build("""
				Format: "&9&l[RADIO] &b%unit%&8: &7%line%"
				Compass:
				   - "north"
				Sides:
				   - "front"
				Lines:
				   Contact:
				      - "Contact! %direction%!"
				""");

		assertEquals(List.of("Contact! %direction%!"), messages.lines("Contact"));
		assertEquals(List.of("&9&l[RADIO] &b%unit%&8: &7%line%"), messages.lines("Format"));
		assertEquals(List.of("north"), messages.lines("Compass"));
		assertEquals(List.of("front"), messages.lines("Sides"));
	}

	@Test
	@DisplayName("an explicit empty list is honoured as silent, not replaced by the English fallback")
	void explicitEmptyList_isSilent() throws IOException {
		CopRadioMessages messages = build("""
				Lines:
				   Ack: []
				""");

		assertTrue(messages.lines("Ack").isEmpty());
	}

	@Test
	@DisplayName("a key missing from the file falls back to the hardcoded English default")
	void missingKey_fallsBackToEnglishDefault() throws IOException {
		CopRadioMessages messages = build("Lines: {}\n");

		assertFalse(messages.lines("Responding").isEmpty());
		assertTrue(messages.lines("Responding").get(0).contains("%unit%"));
	}

	@Test
	@DisplayName("an unrecognised key is silent, not an error")
	void unknownKey_isSilent() throws IOException {
		CopRadioMessages messages = build("Lines: {}\n");

		assertTrue(messages.lines("Not_A_Real_Signal").isEmpty());
	}

	@Test
	@DisplayName("Settings.Language 'es' resolves cop_radio_messages_es.yml")
	void languageEs_resolvesSpanishFile() throws IOException {
		initializeSettingsLanguage("es");

		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		writeFile(fileManager, plugin, "cop_radio_messages.yml", "Lines:\n   Contact:\n      - \"en\"\n");
		writeFile(fileManager, plugin, "cop_radio_messages_es.yml", "Lines:\n   Contact:\n      - \"es\"\n");

		CopRadioMessages messages = new CopRadioMessages(fileManager);

		assertEquals(List.of("es"), messages.lines("Contact"));
	}

	@Test
	@DisplayName("Fall_Back, In_Cover (retreat to cover), Commander_Down and the field-care lines have English defaults and lines in both shipped files")
	void retreatLines_presentInDefaultsAndShippedFiles() throws IOException {
		String[]         keys     = {"Fall_Back", "In_Cover", "Commander_Down", "Hit", "Medic_Moving",
		                             "Covering_Fire", "Medic_Pinned", "Patched_Up"};
		CopRadioMessages fallback = build("Lines: {}\n");
		CopRadioMessages english  = build(shipped("copsncrooks/cop_radio_messages.yml"));

		initializeSettingsLanguage("es");
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		writeFile(fileManager, plugin, "cop_radio_messages.yml", shipped("copsncrooks/cop_radio_messages.yml"));
		writeFile(fileManager, plugin, "cop_radio_messages_es.yml", shipped("copsncrooks/cop_radio_messages_es.yml"));
		CopRadioMessages spanish = new CopRadioMessages(fileManager);

		for (String key : keys) {
			assertFalse(fallback.lines(key).isEmpty(), key);
			assertFalse(english.lines(key).isEmpty(), key);
			assertFalse(spanish.lines(key).isEmpty(), key);
			assertNotEquals(english.lines(key), spanish.lines(key), key);
		}
	}

	@Test
	@DisplayName("Regroup, Regroup_Push and Shots_Fired have lines in the code, English and Spanish, none needing a member")
	void newLines_exist_andNeedNoMember() throws IOException {
		CopRadioMessages fallback = build("Lines: {}\n");
		CopRadioMessages english  = build(shipped("copsncrooks/cop_radio_messages.yml"));
		String           spanish  = shipped("copsncrooks/cop_radio_messages_es.yml");

		for (String key : new String[]{"Regroup", "Regroup_Push", "Shots_Fired"}) {
			assertFalse(fallback.lines(key).isEmpty(), "default " + key);
			assertFalse(english.lines(key).isEmpty(), "English " + key);
			assertTrue(spanish.contains("\n   " + key + ":"), "Spanish " + key);
			assertTrue(fallback.lines(key).stream().noneMatch(line -> line.contains("%member%")), key);
			assertTrue(english.lines(key).stream().noneMatch(line -> line.contains("%member%")), key);
		}
	}

	@Test
	@DisplayName("Fall_Back no longer says \"I'm hit\": that is the field-care Hit line, and one hit crossing both thresholds must not say it twice")
	void fallBack_doesNotDuplicateHit() throws IOException {
		CopRadioMessages fallback = build("Lines: {}\n");
		CopRadioMessages english  = build(shipped("copsncrooks/cop_radio_messages.yml"));

		assertTrue(fallback.lines("Fall_Back").stream().noneMatch(line -> line.contains("I'm hit")));
		assertTrue(english.lines("Fall_Back").stream().noneMatch(line -> line.contains("I'm hit")));
	}

	@Test
	@DisplayName("the code defaults, the English file and the Spanish file carry the same line kinds, each with the same placeholders")
	void defaultsEnglishAndSpanish_haveParity() throws IOException {
		String english = shipped("copsncrooks/cop_radio_messages.yml");
		java.util.Set<String> keys = new java.util.TreeSet<>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^ {3}([A-Z][A-Za-z_]+):\\s*$").matcher(
				english.substring(english.indexOf("\nLines:")));
		while (m.find()) keys.add(m.group(1));
		assertFalse(keys.isEmpty());

		CopRadioMessages fallback = build("Lines: {}\n");
		CopRadioMessages en       = build(english);
		initializeSettingsLanguage("es");
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		writeFile(fileManager, plugin, "cop_radio_messages.yml", english);
		writeFile(fileManager, plugin, "cop_radio_messages_es.yml", shipped("copsncrooks/cop_radio_messages_es.yml"));
		CopRadioMessages es = new CopRadioMessages(fileManager);

		for (String key : keys) {
			assertFalse(fallback.lines(key).isEmpty(), "no code default for " + key);
			assertFalse(es.lines(key).isEmpty(), "no Spanish lines for " + key);
			assertEquals(placeholders(fallback.lines(key)), placeholders(en.lines(key)), "default vs English: " + key);
			assertEquals(placeholders(en.lines(key)), placeholders(es.lines(key)), "English vs Spanish: " + key);
		}
	}

	/** What each caller fills beyond the radio's own %unit%, %target%, %distance%, %direction%, %side%, %tier%, %level%. */
	private static final Map<String, java.util.Set<String>> CALLER_EXTRAS = Map.ofEntries(
			Map.entry("Push", java.util.Set.of("member")), Map.entry("Flank_Left", java.util.Set.of("member")),
			Map.entry("Flank_Right", java.util.Set.of("member")), Map.entry("Man_Down", java.util.Set.of("member")),
			Map.entry("Leader_Down", java.util.Set.of("member")),
			Map.entry("Commander_Down", java.util.Set.of("member")),
			Map.entry("Pull_Back", java.util.Set.of("member", "role")),
			Map.entry("Hit", java.util.Set.of("health", "role")),
			Map.entry("Medic_Moving", java.util.Set.of("member", "eta", "role")),
			Map.entry("Covering_Fire", java.util.Set.of("role")),
			Map.entry("Medic_Pinned", java.util.Set.of("member", "role")),
			Map.entry("Medic_Treating", java.util.Set.of("member", "health", "role")),
			Map.entry("Patched_Up", java.util.Set.of("member", "health", "role")),
			Map.entry("Overwatch_Set", java.util.Set.of("role")), Map.entry("Marksman_Spotted", java.util.Set.of("role")),
			Map.entry("Marksman_Lost", java.util.Set.of("role")),
			Map.entry("Marksman_Reloading", java.util.Set.of("role")), Map.entry("Shield_Up", java.util.Set.of("role")),
			Map.entry("Flanking", java.util.Set.of("role")), Map.entry("Flanking_Undirected", java.util.Set.of("role")),
			Map.entry("Commander_Orders", java.util.Set.of("role")),
			Map.entry("Commander_Orders_Undirected", java.util.Set.of("role")),
			Map.entry("Commander_Orders_Basic", java.util.Set.of("role")),
			Map.entry("Status_Check", java.util.Set.of("role")),
			Map.entry("Dispatch_En_Route", java.util.Set.of("count", "station", "eta")),
			Map.entry("Wipe_Refill", java.util.Set.of("eta")));

	// %place% is supplied by every radio voice (Unknown_Place when no caller names one)
	private static final java.util.Set<String> ALWAYS = java.util.Set.of("%unit%", "%target%", "%distance%",
	                                                                      "%direction%", "%side%", "%tier%", "%level%",
	                                                                      "%place%");

	@Test
	@DisplayName("no variant of any line, in the code defaults, the English file or the Spanish file, names a placeholder its caller does not fill")
	void everyVariant_usesOnlyPlaceholdersItsCallerFills() throws IOException {
		String english = shipped("copsncrooks/cop_radio_messages.yml");
		java.util.Set<String> keys = new java.util.TreeSet<>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^ {3}([A-Z][A-Za-z_]+):\\s*$").matcher(
				english.substring(english.indexOf("\nLines:")));
		while (m.find()) keys.add(m.group(1));

		CopRadioMessages fallback = build("Lines: {}\n");
		CopRadioMessages en       = build(english);
		initializeSettingsLanguage("es");
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		writeFile(fileManager, plugin, "cop_radio_messages.yml", english);
		writeFile(fileManager, plugin, "cop_radio_messages_es.yml", shipped("copsncrooks/cop_radio_messages_es.yml"));
		CopRadioMessages es = new CopRadioMessages(fileManager);

		for (String key : keys) {
			java.util.Set<String> filled = new java.util.HashSet<>(ALWAYS);
			CALLER_EXTRAS.getOrDefault(key, java.util.Set.of()).forEach(name -> filled.add("%" + name + "%"));
			for (CopRadioMessages source : List.of(fallback, en, es))
				for (String line : source.lines(key))
					for (String used : placeholders(List.of(line)))
						assertTrue(filled.contains(used), key + " uses " + used + " that no caller fills: " + line);
		}
	}

	private static final Map<String, String> NEW_LINES = Map.of(
			"Dispatch_Wanted", "All units, be advised: %target% is wanted in %place%, level %level%.",
			"Dispatch_En_Route", "%count% units en route from %station%, ETA %eta% s.",
			"Wipe_Refill", "Squad down. Backup inbound in %eta% s.",
			"Handoff", "Lost him heading %direction%. Units ahead, pick him up.",
			"Post_Up", "Holding the corner.",
			"Eyes_On", "Eyes on suspect near %place%, moving %direction%.",
			"Returning_To_Patrol", "Units returning to patrol.",
			"Contact_Lost", "Lost visual near %place%. Searching the last known position.");

	@Test
	@DisplayName("an upgraded file without the new keys still yields the 0.16 lines from the code defaults")
	void upgradedFile_withoutTheNewKeys_stillSpeaksThem() throws IOException {
		CopRadioMessages upgraded = build("""
				Lines:
				   Contact:
				      - "Contact!"
				""");

		NEW_LINES.forEach((key, text) -> assertEquals(List.of(text), upgraded.lines(key), key));
	}

	@Test
	@DisplayName("Speaker_Name is a root key: the default without it, and the bundled English file carries it")
	void speakerName_isARootKey_defaultAndShipped() throws IOException {
		List<String> def = List.of("{rank} {role} &f{name} &7#{number}");

		assertEquals(def, build("Lines: {}\n").lines("Speaker_Name"));
		assertEquals(def, build(shipped("copsncrooks/cop_radio_messages.yml")).lines("Speaker_Name"));
	}

	@Test
	@DisplayName("the Spanish file carries its own Speaker_Name root key: a Spanish server never reads the English key")
	void spanishFile_carriesItsOwnSpeakerName() throws IOException {
		String spanish = shipped("copsncrooks/cop_radio_messages_es.yml");

		assertTrue(spanish.contains("\nSpeaker_Name: "), "Spanish file has no Speaker_Name root key");
		assertEquals(List.of("{rank} {role} &f{name} &7#{number}"), build(spanish).lines("Speaker_Name"));
	}

	@Test
	@DisplayName("the bundled English file carries the 0.16 lines word for word")
	void bundledEnglish_carriesTheNewLines() throws IOException {
		CopRadioMessages english = build(shipped("copsncrooks/cop_radio_messages.yml"));

		NEW_LINES.forEach((key, text) -> assertEquals(List.of(text), english.lines(key), key));
		assertEquals(List.of("the area"), english.lines("Unknown_Place"));
	}

	@Test
	@DisplayName("Unknown_Place falls back to 'the area' without the key, and reads the file's word with it")
	void unknownPlace_fallsBackToTheArea() throws IOException {
		assertEquals(List.of("the area"), build("Lines: {}\n").lines("Unknown_Place"));
		assertEquals(List.of("la zona"), build("Unknown_Place: \"la zona\"\n").lines("Unknown_Place"));
	}

	private static java.util.Set<String> placeholders(List<String> pool) {
		java.util.Set<String> found = new java.util.TreeSet<>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("%[a-z_]+%").matcher(String.join("\n", pool));
		while (m.find()) found.add(m.group());
		return found;
	}

	private String shipped(String resource) throws IOException {
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private CopRadioMessages build(String yaml) throws IOException {
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		writeFile(fileManager, plugin, "cop_radio_messages.yml", yaml);
		return new CopRadioMessages(fileManager);
	}

	private void writeFile(FileManager fileManager, JavaPlugin plugin, String name, String yaml) throws IOException {
		Files.writeString(tempDir.resolve(name), yaml, StandardCharsets.UTF_8);
		fileManager.addFile(new FileHandler(plugin, tempDir.resolve(name).toFile()), false);
	}

	private void initializeSettingsLanguage(String language) throws IOException {
		Files.writeString(tempDir.resolve("settings.yml"), "Language: " + language + "\nMoney_Symbol: '$'\n",
		                  StandardCharsets.UTF_8);

		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileHandler handler     = new FileHandler(plugin, tempDir.resolve("settings.yml").toFile());
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(handler, false);

		new Settings(fileManager).initialize();
	}
}
