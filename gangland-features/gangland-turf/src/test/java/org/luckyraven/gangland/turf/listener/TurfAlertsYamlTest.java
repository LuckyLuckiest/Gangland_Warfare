package org.luckyraven.gangland.turf.listener;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;

/**
 * The shipped turf/turf_messages.yml carries every Alerts key the notifier reads. A key missing from the file makes
 * text() return its sentinel fallback, which this test rejects.
 */
@DisplayName("Turf alerts YAML - every Alerts key the notifier reads ships in turf_messages.yml")
class TurfAlertsYamlTest {

	private static final String SENTINEL = "@@missing@@";

	private static final String[] KEYS = {
			"Alerts.Start",
			"Alerts.Start_No_Side",
			"Alerts.Start_Ally",
			"Alerts.Half_Guards",
			"Alerts.Last_Stand",
			"Alerts.Held",
			"Alerts.Attack_Failed",
			"Alerts.Stalled",
			"Alerts.Lost",
			"Alerts.Taken",
			"Alerts.Cop_Hit",
			"Alerts.Guard_Down",
			"Alerts.Quartermaster_Hit",
			"Alerts.Quartermaster_Down"
	};

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> staticSettings;
	private TurfMessages           messages;

	@BeforeEach
	void setUp() throws IOException {
		staticSettings = TurfTextFixture.stubMoneySymbol();
		TurfMessageContract legacy = mock(TurfMessageContract.class);
		messages = TurfTextFixture.messages(tempDir, legacy);
	}

	@AfterEach
	void tearDown() {
		staticSettings.close();
	}

	@Test
	@DisplayName("every Alerts key the notifier reads is present in the shipped file")
	void shippedFile_hasEveryAlertsKey() {
		for (String key : KEYS) {
			assertNotEquals(SENTINEL, messages.text(key, SENTINEL), "missing key: " + key);
		}
	}
}
