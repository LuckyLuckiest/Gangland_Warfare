package org.luckyraven.gangland.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command-format rule (0.21.3): a usage never lists its choices as a pipe list. Every {@code commands.json} (core
 * and every runtime module) and every usage literal in main sources uses a short placeholder (the choices are tab
 * completed), and every help description is one short line.
 */
@DisplayName("Command usage format")
class CommandUsageFormatTest {

	/** The longest help description of any command. */
	private static final int MAX_DESCRIPTION = 100;
	/** The longest description of a {@code /glw cop} help row. */
	private static final int MAX_COP_DESCRIPTION = 60;

	private static final Pattern PIPE_PLACEHOLDER = Pattern.compile("\"[^\"]*<[^<>\"]*[|][^<>\"]*[|][^<>\"]*>");

	/** {@code new Argument(plugin, "word", tree)} with no action: Keystone answers "not implemented yet". */
	private static final Pattern ACTIONLESS_NODE = Pattern.compile(
			"new Argument\\(\\s*[\\w.]+(\\(\\))?,\\s*\"[^\"]+\",\\s*[\\w.]+(\\(\\))?\\)");

	private static Path root() {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null && !Files.isDirectory(dir.resolve("gangland-features"))) dir = dir.getParent();
		assertTrue(dir != null, "repo root not found from " + Path.of("").toAbsolutePath());
		return dir;
	}

	private static List<Path> commandsJsons() throws IOException {
		try (Stream<Path> walk = Files.walk(root())) {
			return walk.filter(p -> p.getFileName().toString().equals("commands.json"))
			           .filter(p -> p.toString().replace(File.separatorChar, '/').contains("/src/main/resources/"))
			           .toList();
		}
	}

	private static JsonObject read(Path path) throws IOException {
		try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return new JsonParser().parse(in).getAsJsonObject();
		}
	}

	@Test
	@DisplayName("commands.json files were found in core and the modules")
	void filesFound() throws IOException {
		assertTrue(commandsJsons().size() >= 9, "found " + commandsJsons());
	}

	@Test
	@DisplayName("no commands.json usage has two or more pipes")
	void noPipeListInUsage() throws IOException {
		List<String> bad = new ArrayList<>();
		for (Path file : commandsJsons()) {
			for (Map.Entry<String, com.google.gson.JsonElement> entry : read(file).entrySet()) {
				String usage = entry.getValue().getAsJsonObject().get("usage").getAsString();
				if (usage.chars().filter(c -> c == '|').count() >= 2) bad.add(entry.getKey() + " -> " + usage);
			}
		}
		assertTrue(bad.isEmpty(), "pipe lists in usage: " + bad);
	}

	@Test
	@DisplayName("no commands.json description is longer than the agreed line")
	void descriptionsAreOneShortLine() throws IOException {
		List<String> bad = new ArrayList<>();
		for (Path file : commandsJsons()) {
			for (Map.Entry<String, com.google.gson.JsonElement> entry : read(file).entrySet()) {
				String description = entry.getValue().getAsJsonObject().get("description").getAsString();
				int    max         = entry.getKey().startsWith("cop_") ? MAX_COP_DESCRIPTION : MAX_DESCRIPTION;
				if (description.length() > max) bad.add(entry.getKey() + " (" + description.length() + ")");
			}
		}
		assertTrue(bad.isEmpty(), "descriptions over the limit: " + bad);
	}

	@Test
	@DisplayName("no usage literal in main sources holds a placeholder with two or more pipes")
	void noPipeListInSources() throws IOException {
		List<String> bad = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(root())) {
			for (Path file : walk.filter(p -> p.toString().endsWith(".java"))
			                     .filter(p -> p.toString().replace(File.separatorChar, '/').contains("/src/main/java/"))
			                     .toList()) {
				List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
				for (int i = 0; i < lines.size(); i++) {
					String line = lines.get(i).trim();
					if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) continue;
					if (PIPE_PLACEHOLDER.matcher(line).find()) bad.add(file.getFileName() + ":" + (i + 1) + " " + line);
				}
			}
		}
		assertTrue(bad.isEmpty(), "pipe lists in sources: " + bad);
	}

	@Test
	@DisplayName("noNodeWithoutAction")
	void noNodeWithoutAction() throws IOException {
		List<String> bad = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(root())) {
			for (Path file : walk.filter(p -> p.toString().endsWith(".java"))
			                     .filter(p -> p.toString().replace(File.separatorChar, '/').contains("/src/main/java/"))
			                     .toList()) {
				List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
				for (int i = 0; i < lines.size(); i++) {
					String line = lines.get(i);
					if (line.contains(".find(")) continue;
					if (ACTIONLESS_NODE.matcher(line).find()) bad.add(file.getFileName() + ":" + (i + 1));
				}
			}
		}
		assertTrue(bad.isEmpty(), "Argument nodes with no action: " + bad);
	}
}
