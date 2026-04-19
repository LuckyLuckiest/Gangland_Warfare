package me.luckyraven.persistence.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConfigSerializer}.
 *
 * <p>Comments are known-lost on round-trip (documented non-goal); tests check
 * structural equivalence only.
 */
@DisplayName("ConfigSerializer — round-trip YAML")
class ConfigSerializerTest {

	private static final Path FIXTURE = Path.of("round.yml");

	private static void assertStructurallyEqual(ConfigNode a, ConfigNode b) {
		switch (a) {
			case NullNode ignored -> assertInstanceOf(NullNode.class, b,
			                                          "expected NullNode, got " + b.getClass().getSimpleName());
			case ScalarNode sa -> {
				ScalarNode sb = (ScalarNode) b;
				assertEquals(sa.value(), sb.value(), "scalar value at " + sa.path());
			}
			case SequenceNode seqA -> {
				SequenceNode seqB = (SequenceNode) b;
				assertEquals(seqA.items().size(), seqB.items().size(), "sequence size at " + seqA.path());

				for (int i = 0; i < seqA.items().size(); i++) {
					assertStructurallyEqual(seqA.items().get(i), seqB.items().get(i));
				}
			}
			case MappingNode mapA -> {
				MappingNode mapB = (MappingNode) b;
				assertEquals(mapA.entries().keySet(), mapB.entries().keySet(), "keys at " + mapA.path());

				for (String key : mapA.entries().keySet()) {
					assertStructurallyEqual(mapA.get(key), mapB.get(key));
				}
			}
		}
	}

	@Test
	@DisplayName("round-trip preserves keys, order, and scalar values")
	void roundTrip_preservesStructure() {
		String original = """
				Display_Name: Smiley
				Cost: 500
				Allows_Barter: true
				Tags:
				  - alpha
				  - beta
				Profile:
				  Ratio: 0.8
				""";

		ConfigReport   report = new ConfigReport();
		ConfigDocument doc    = new ConfigParser().parse(FIXTURE, new StringReader(original), report);
		assertFalse(report.hasErrors());

		String dumped = new ConfigSerializer().writeToString(doc.root());

		ConfigReport   roundReport = new ConfigReport();
		ConfigDocument round       = new ConfigParser().parse(FIXTURE, new StringReader(dumped), roundReport);
		assertFalse(roundReport.hasErrors());

		assertStructurallyEqual(doc.root(), round.root());
	}

	@Test
	@DisplayName("integer scalar stays unquoted after round-trip")
	void intScalar_roundTrip_remainsInt() {
		String original = "Cost: 500\n";

		ConfigReport   report = new ConfigReport();
		ConfigDocument doc    = new ConfigParser().parse(FIXTURE, new StringReader(original), report);

		String dumped = new ConfigSerializer().writeToString(doc.root());

		assertTrue(dumped.contains("Cost: 500"), "integer should not be wrapped in quotes: " + dumped);
	}

	@Test
	@DisplayName("string-typed '42' stays string after round-trip")
	void quotedInt_roundTrip_remainsString() {
		String original = "Cost: '42'\n";

		ConfigReport   report = new ConfigReport();
		ConfigDocument doc    = new ConfigParser().parse(FIXTURE, new StringReader(original), report);

		String         dumped = new ConfigSerializer().writeToString(doc.root());
		ConfigDocument round  = new ConfigParser().parse(FIXTURE, new StringReader(dumped), new ConfigReport());

		ScalarNode cost = (ScalarNode) round.root().get("Cost");
		assertEquals("42", cost.value());
		assertTrue(cost.tag().endsWith(":str"), "quoted form should keep the string tag: " + cost.tag());
	}

}
