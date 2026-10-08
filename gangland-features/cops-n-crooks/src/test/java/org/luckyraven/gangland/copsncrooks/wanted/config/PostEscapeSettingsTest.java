package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link PostEscapeSettings} (0.16.1 T-187): {@code Wanted.Post_Escape} and {@code Wanted.Hud.Bounty} read key by key, the
 * shipped file equals the defaults, and a missing section is the default.
 */
@DisplayName("PostEscapeSettings - the post-escape search and its bounty bar")
class PostEscapeSettingsTest {

	private static NodeReader wantedRoot(String yaml, ConfigReport report) {
		NodeReader  file    = NodeReader.of(new ConfigParser().parse(Path.of("wanted.yml"), new StringReader(yaml), report)
		                                                      .root(), report);
		MappingNode section = file.get("Wanted").asMapping().orNull();
		return section == null ? null : NodeReader.of(section, report);
	}

	@Test
	@DisplayName("the shipped wanted.yml reads as the defaults, with no issue")
	void bundledFile_equalsDefault() throws IOException {
		String yaml;
		try (InputStream in = getClass().getClassLoader().getResourceAsStream("copsncrooks/wanted.yml")) {
			assertNotNull(in, "copsncrooks/wanted.yml must ship in the module jar");
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		ConfigReport report = new ConfigReport();

		assertEquals(PostEscapeSettings.DEFAULT, PostEscapeSettings.parse(wantedRoot(yaml, report)));
		// only this block's keys are read here; the other blocks report as unknown in this isolated parse
		String issues = report.issues().toString();
		assertFalse(issues.contains("Post_Escape") || issues.contains("Bounty"), issues);
	}

	@Test
	@DisplayName("a missing section is the default")
	void missingSection_isDefault() {
		assertEquals(PostEscapeSettings.DEFAULT, PostEscapeSettings.parse(null));
	}

	@Test
	@DisplayName("each key overrides its default; the bounty bar colour is read from Hud.Bounty")
	void overrides_areRead() {
		String yaml = "Wanted:\n"
		              + "   Post_Escape:\n"
		              + "      Enable: false\n"
		              + "      Search_Seconds: 45\n"
		              + "      Announce: false\n"
		              + "      Spotted_Stars: 2\n"
		              + "   Hud:\n"
		              + "      Bounty:\n"
		              + "         Enable: false\n"
		              + "         Bar_Color: \"RED\"\n";

		PostEscapeSettings parsed = PostEscapeSettings.parse(wantedRoot(yaml, new ConfigReport()));

		assertEquals(new PostEscapeSettings(false, 45, false, "RED", false, 2), parsed);
	}
}
