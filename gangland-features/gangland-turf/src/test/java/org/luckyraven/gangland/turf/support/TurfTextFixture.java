package org.luckyraven.gangland.turf.support;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.keystone.persistence.FileManager;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * Builds the real turf text and rules classes over the shipped YAML, so tests read the same wording a server shows.
 * {@link #stubMoneySymbol()} is needed wherever a message is coloured: GanglandChatUtil reads the money symbol from
 * Settings, which no unit test initialises.
 */
public final class TurfTextFixture {

	private TurfTextFixture() {
	}

	/** Pass to the test class's {@code @BeforeEach}; close it in {@code @AfterEach}. */
	public static MockedStatic<Settings> stubMoneySymbol() {
		MockedStatic<Settings> settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		return settings;
	}

	/** The shipped turf_messages.yml, read through the real class. */
	public static TurfMessages messages(Path dir, TurfMessageContract legacy) throws IOException {
		FileManager fileManager = YamlFixture.fileManager(dir, "turf_messages",
		                                                  YamlFixture.shipped("/turf/turf_messages.yml"));
		return new TurfMessages(fileManager, legacy, CaptureFixtures.settings(180, 15, 15, 10,
		                                                                      new int[]{25, 50, 75}, 90, 90));
	}

	/** A turf_rules.yml with the given body, read through the real class. */
	public static TurfRules rules(Path dir, String yaml) throws IOException {
		return new TurfRules(YamlFixture.fileManager(dir, "turf_rules", yaml));
	}
}
