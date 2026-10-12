package org.luckyraven.gangland.turf.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.CaptureEligibility.Reason;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.persistence.FileManager;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * {@link TurfMessages}: the reason sentence for each verdict, the in-code fallback for a key the file lacks, and
 * replace-then-colour. Built on the same LocalizedModuleYaml base as CivilianMessages; the Settings language is never
 * initialised here, so the English file is the one read.
 */
@DisplayName("TurfMessages - turf_messages.yml and the reason sentence")
class TurfMessagesTest {

	private static final long NOW = 100_000_000L;

	private MockedStatic<Settings> settings;

	/** GanglandChatUtil.color reads the money symbol from Settings, so stub it the way the other module tests do. */
	@BeforeEach
	void stubMoneySymbol() {
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
	}

	@AfterEach
	void releaseMoneySymbol() {
		settings.close();
	}

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private TurfMessages messages(String yaml, TurfMessageContract legacy) throws IOException {
		FileManager fileManager = YamlFixture.fileManager(tempDir, "turf_messages", yaml);
		CaptureSettings settings = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		return new TurfMessages(fileManager, legacy, settings);
	}

	private TurfMessages shipped(TurfMessageContract legacy) throws IOException {
		return messages(YamlFixture.shipped("/turf/turf_messages.yml"), legacy);
	}

	@Test
	@DisplayName("an attackable turf reads attackable")
	void reasonAttackable() throws IOException {
		TurfMessages messages = shipped(mock(TurfMessageContract.class));

		String text = messages.reason(new CaptureEligibility.Verdict(Reason.ATTACKABLE, 0L), "", NOW);

		assertTrue(text.contains("attackable"), text);
	}

	@Test
	@DisplayName("a cooldown reads the time left, rounded up to whole seconds")
	void reasonCooldown() throws IOException {
		TurfMessageContract legacy = mock(TurfMessageContract.class);
		when(legacy.formatDuration(90L)).thenReturn("1m 30s");
		TurfMessages messages = shipped(legacy);

		String text = messages.reason(new CaptureEligibility.Verdict(Reason.COOLDOWN, NOW + 90_000L), "", NOW);

		assertTrue(text.contains("attackable in 1m 30s"), text);
	}

	@Test
	@DisplayName("owners online names the gang and the protection window")
	void reasonOwnersActive() throws IOException {
		TurfMessageContract legacy = mock(TurfMessageContract.class);
		when(legacy.formatDuration(600L)).thenReturn("10m");
		TurfMessages messages = shipped(legacy);

		String text = messages.reason(new CaptureEligibility.Verdict(Reason.OWNERS_ACTIVE, NOW), "Rats", NOW);

		assertTrue(text.contains("Rats"), text);
		assertTrue(text.contains("10m"), text);
	}

	@Test
	@DisplayName("a key missing from the file returns its in-code fallback")
	void missingKeyUsesFallback() throws IOException {
		TurfMessages messages = messages("Other: x\n", mock(TurfMessageContract.class));

		assertEquals(GanglandChatUtil.color("&7fallback text"), messages.text("Map.Nope", "&7fallback text"));
	}

	@Test
	@DisplayName("a value with colour codes keeps its colour inside a placeholder")
	void replaceThenColour() throws IOException {
		TurfMessages messages = messages("Probe: \"%gang% here\"\n", mock(TurfMessageContract.class));

		assertEquals(GanglandChatUtil.color("&cRats here"), messages.text("Probe", "x", "gang", "&cRats"));
	}
}
