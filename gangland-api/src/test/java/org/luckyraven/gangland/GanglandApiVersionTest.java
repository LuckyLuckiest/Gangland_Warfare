package org.luckyraven.gangland;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GanglandApiVersionTest {

	@Test
	@DisplayName("GanglandApi.VERSION is 2.7")
	void versionIs27() {
		assertEquals("2.7", GanglandApi.VERSION);
	}

	private static String hostApi(String module) throws IOException {
		Path root = Path.of(System.getProperty("user.dir")).resolve("..").normalize();
		Path yml  = root.resolve("gangland-features").resolve(module).resolve("src/main/resources/module.yml");
		return Files.readAllLines(yml).stream().filter(l -> l.startsWith("Host_Api:")).findFirst()
		            .map(l -> l.substring("Host_Api:".length()).trim()).orElseThrow();
	}

	@Test
	@DisplayName("the four modules that use the 2.7 surface declare Host_Api 2.7")
	void usingModulesDeclare27() throws IOException {
		for (String module : List.of("cops-n-crooks", "gangland-gadget", "gangland-npc-shops", "gangland-lootchest")) {
			assertEquals("2.7", hostApi(module), module);
		}
	}

	@Test
	@DisplayName("modules that do not use the new surface keep their Host_Api (civilians stays 2.6)")
	void otherModulesUnchanged() throws IOException {
		assertEquals("2.6", hostApi("gangland-civilians"));
		for (String module : List.of("gangland-turf", "gangland-gang", "gangland-gang-fights", "gangland-mail",
		                             "gangland-healthbars")) {
			assertTrue(Double.parseDouble(hostApi(module)) < 2.7, module);
		}
	}
}
