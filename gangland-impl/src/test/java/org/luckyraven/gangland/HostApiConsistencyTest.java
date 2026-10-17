package org.luckyraven.gangland;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped module.yml declares a Host_Api the host satisfies (same major, minor at most the host's), and the
 * modules that use members added in the current api line declare exactly that line (0.20.0, api 2.7).
 */
@DisplayName("module.yml Host_Api - at most GanglandApi.VERSION; the 2.7 readers carry 2.7")
class HostApiConsistencyTest {

	/** Module ids that read gangland-api members added in the current VERSION (0.20.0: the 2.7 readers). */
	static final Set<String> ON_CURRENT = Set.of("copsncrooks", "gadget", "npcshops", "lootchest");

	private static List<Map<String, Object>> descriptors() throws IOException {
		List<Map<String, Object>> out = new ArrayList<>();
		try (Stream<Path> dirs = Files.list(Path.of("..", "gangland-features"))) {
			for (Path dir : dirs.toList()) {
				Path file = dir.resolve("src/main/resources/module.yml");
				if (!Files.isRegularFile(file)) {
					continue;
				}
				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					Map<String, Object> map = new Yaml().load(reader);
					out.add(map);
				}
			}
		}
		return out;
	}

	private static int[] parse(Object version) {
		String[] parts = String.valueOf(version).trim().split("\\.");
		return new int[]{Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0};
	}

	@Test
	@DisplayName("every module.yml Host_Api has the host's major and a minor at most the host's")
	void everyHostApi_isSatisfiedByTheHost() throws IOException {
		int[] host = parse(GanglandApi.VERSION);
		List<Map<String, Object>> all = descriptors();
		assertFalse(all.isEmpty(), "no module.yml found under ../gangland-features");
		for (Map<String, Object> descriptor : all) {
			int[] wanted = parse(descriptor.get("Host_Api"));
			assertEquals(host[0], wanted[0], descriptor.get("Id") + " Host_Api major");
			assertTrue(wanted[1] <= host[1], descriptor.get("Id") + " Host_Api minor above the host's");
		}
	}

	@Test
	@DisplayName("the modules that read the current api line declare it")
	void currentApiReaders_declareTheCurrentLine() throws IOException {
		Set<String> seen = new java.util.HashSet<>();
		for (Map<String, Object> descriptor : descriptors()) {
			String id = String.valueOf(descriptor.get("Id"));
			if (ON_CURRENT.contains(id)) {
				seen.add(id);
				assertEquals(GanglandApi.VERSION, String.valueOf(descriptor.get("Host_Api")), id + " Host_Api");
			}
		}
		assertEquals(ON_CURRENT, seen, "every current-line module ships a module.yml");
	}
}
