package org.luckyraven.gangland.file.configuration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Docket T-18: a YAML mapping key declared twice under the same parent is silently overridden by the last
 * occurrence, so every entry of the first block disappears without any loader warning. {@code message_en.yml}
 * declared {@code Errors.Bounty} twice, which dropped {@code Errors.Bounty.Below_Minimum} (added by WB-02) and made
 * {@code LanguageLoader} report the key as missing on every boot.
 */
@DisplayName("message files: no duplicate mapping keys")
class MessageFileDuplicateKeysTest {

	private static final Pattern KEY      = Pattern.compile("^(\\s*)([A-Za-z0-9_\\-]+):(.*)$");

	@ParameterizedTest
	@ValueSource(strings = {"/message/message_en.yml", "/message/message_es.yml"})
	@DisplayName("every mapping path is declared once")
	void noDuplicateMappingPaths(String resource) throws Exception {
		Map<String, List<Integer>> occurrences = new LinkedHashMap<>();
		Deque<int[]>               indents     = new ArrayDeque<>();
		Deque<String>              keys        = new ArrayDeque<>();

		int lineNumber = 0;
		for (String line : readResourceLines(resource)) {
			lineNumber++;
			if (line.isBlank() || line.stripLeading().startsWith("#")) continue;

			Matcher matcher = KEY.matcher(line);
			if (!matcher.matches()) continue;

			int indent = matcher.group(1).length();
			while (!indents.isEmpty() && indents.peek()[0] >= indent) {
				indents.pop();
				keys.pop();
			}

			List<String> reversed = new ArrayList<>(keys);
			java.util.Collections.reverse(reversed);
			String path = String.join(".", reversed);
			path = path.isEmpty() ? matcher.group(2) : path + "." + matcher.group(2);

			occurrences.computeIfAbsent(path, ignored -> new ArrayList<>()).add(lineNumber);
			indents.push(new int[]{indent});
			keys.push(matcher.group(2));
		}

		List<String> duplicates = new ArrayList<>();
		occurrences.forEach((path, lines) -> {
			if (lines.size() > 1) duplicates.add(path + " at lines " + lines);
		});

		assertEquals(List.of(), duplicates, "duplicate mapping keys silently drop the earlier block");
	}

	@ParameterizedTest
	@ValueSource(strings = {"/message/message_en.yml", "/message/message_es.yml"})
	@DisplayName("Errors.Bounty.Below_Minimum survives a plain YAML load")
	void bountyBelowMinimumIsLoadable(String resource) throws Exception {
		YamlConfiguration yaml = new YamlConfiguration();
		try (InputStream stream = MessageFileDuplicateKeysTest.class.getResourceAsStream(resource);
		     InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			yaml.load(reader);
		}

		assertNotNull(yaml.getString("Errors.Bounty.Below_Minimum"),
		              "the second Errors.Bounty block overrides the first and drops Below_Minimum");
	}

	private static List<String> readResourceLines(String resource) throws Exception {
		try (InputStream stream = MessageFileDuplicateKeysTest.class.getResourceAsStream(resource)) {
			assertNotNull(stream, resource + " must be on the test classpath");
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
		}
	}

}
