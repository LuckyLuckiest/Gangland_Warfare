package org.luckyraven.gangland.command.sub.module;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.keystone.module.ModuleDescriptor;
import org.luckyraven.keystone.module.artifact.ArtifactCoordinate;
import org.luckyraven.keystone.update.PluginVersion;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ModuleInstalls} — the two pure decisions behind {@code /glw module install}: what the operator
 * typed resolves to, and whether the jar that came back may stay in the modules folder.
 */
@DisplayName("ModuleInstalls — coordinate resolution and the post-download descriptor check")
class ModuleInstallsTest {

	private static final PluginVersion HOST = PluginVersion.parse("1.0");

	private static ModuleDescriptor descriptor(String hostApi, List<String> depends, List<String> plugins) {
		return new ModuleDescriptor("turf", "Gangland Turf", "0.9.1", "org.luckyraven.gangland.turf.TurfModule",
		                            hostApi, depends, plugins, "org.luckyraven:gangland-turf",
		                            Path.of("modules", "gangland-turf-0.9.1.jar"));
	}

	@ParameterizedTest(name = "\"{0}\" is one of the six official ids")
	@ValueSource(strings = {"mail", "turf", "civilians", "copsncrooks", "gadget", "npcshops"})
	@DisplayName("an official module id resolves to its org.luckyraven coordinate without a version")
	void coordinate_officialId_resolvesToCoordinate(String id) {
		ArtifactCoordinate coordinate = ModuleInstalls.coordinate(id);

		assertNotNull(coordinate);
		assertEquals("org.luckyraven", coordinate.groupId());
		assertFalse(coordinate.hasVersion());
		assertEquals(ModuleInstalls.OFFICIAL.get(id), coordinate.toString());
	}

	@Test
	@DisplayName("an official id is matched case-insensitively")
	void coordinate_officialIdUpperCase_stillResolves() {
		assertEquals("org.luckyraven:gangland-mail", String.valueOf(ModuleInstalls.coordinate("MAIL")));
	}

	@Test
	@DisplayName("a full group:artifact:version coordinate is taken as typed")
	void coordinate_fullCoordinate_keepsVersion() {
		ArtifactCoordinate coordinate = ModuleInstalls.coordinate("com.example:my-module:1.2.3");

		assertNotNull(coordinate);
		assertEquals("com.example", coordinate.groupId());
		assertEquals("my-module", coordinate.artifactId());
		assertEquals("1.2.3", coordinate.version());
	}

	@Test
	@DisplayName("a version-less third-party coordinate resolves, leaving the version to the metadata lookup")
	void coordinate_groupAndArtifact_hasNoVersion() {
		ArtifactCoordinate coordinate = ModuleInstalls.coordinate("com.example:my-module");

		assertNotNull(coordinate);
		assertFalse(coordinate.hasVersion());
	}

	@ParameterizedTest(name = "\"{0}\" is rejected as unusable input")
	@ValueSource(strings = {"nonsense", "", "   ", "a:b:c:d", "onlyone", ":", "com.example:"})
	@DisplayName("anything that is neither an official id nor a coordinate returns null, not a bogus coordinate")
	void coordinate_unusableInput_isNull(String input) {
		assertNull(ModuleInstalls.coordinate(input));
	}

	@Test
	@DisplayName("null input returns null rather than throwing")
	void coordinate_null_isNull() {
		assertNull(ModuleInstalls.coordinate(null));
	}

	@Test
	@DisplayName("a descriptor built for another host API line is incompatible and carries no warnings")
	void inspect_wrongHostApi_isIncompatible() {
		ModuleInstalls.Verdict verdict = ModuleInstalls.inspect(descriptor("0.9", List.of("civilians"),
		                                                                   List.of("Bartizan")), HOST, Set.of(),
		                                                        name -> false);

		assertFalse(verdict.compatible());
		assertTrue(verdict.missingDepends().isEmpty());
		assertTrue(verdict.missingPlugins().isEmpty());
	}

	@Test
	@DisplayName("a matching host API with every requirement met is compatible and warning-free")
	void inspect_requirementsMet_isClean() {
		ModuleInstalls.Verdict verdict = ModuleInstalls.inspect(descriptor("1.0", List.of("civilians"),
		                                                                   List.of("Bartizan")),
		                                                        HOST, Set.of("civilians"), "Bartizan"::equals);

		assertTrue(verdict.compatible());
		assertTrue(verdict.missingDepends().isEmpty());
		assertTrue(verdict.missingPlugins().isEmpty());
	}

	@Test
	@DisplayName("a compatible jar whose Depends and Plugins are absent is kept, with one warning per requirement")
	void inspect_missingRequirements_warnsPerRequirement() {
		ModuleInstalls.Verdict verdict = ModuleInstalls.inspect(descriptor("1.0", List.of("civilians", "turf"),
		                                                                   List.of("Bartizan", "Citizens")),
		                                                        HOST, Set.of("turf"), "Citizens"::equals);

		assertTrue(verdict.compatible());
		assertEquals(List.of("civilians"), verdict.missingDepends());
		assertEquals(List.of("Bartizan"), verdict.missingPlugins());
	}

	@Test
	@DisplayName("the host API line ignores the patch component, so 1.0.4 and 1.0 are the same line")
	void inspect_patchDiffersOnly_isCompatible() {
		ModuleInstalls.Verdict verdict = ModuleInstalls.inspect(descriptor("1.0.4", List.of(), List.of()), HOST,
		                                                        Set.of(), name -> true);

		assertTrue(verdict.compatible());
	}

}
