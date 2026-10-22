package org.luckyraven.gangland.npcshops.integration;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.logging.Logger;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T-311: the shipped Hold_Up block (owned by HoldUpSettings) must not log config.unknown_key on boot. */
@DisplayName("TraderSettingsImpl - Hold_Up is a known key")
class TraderSettingsImplLogTest {

	private final List<String> lines = new ArrayList<>();
	private org.apache.logging.log4j.core.Logger logger;
	private Level                                savedLevel;
	private AbstractAppender                     capture;

	@BeforeEach
	void setUp() {
		logger     = (org.apache.logging.log4j.core.Logger) Logger.getLogger(TraderSettingsImpl.class);
		savedLevel = logger.getLevel();
		capture    = new AbstractAppender("trader-capture", null, null, true) {
			@Override
			public void append(LogEvent event) {
				lines.add(event.getMessage().getFormattedMessage());
			}
		};
		capture.start();
		Configurator.setLevel(logger.getName(), Level.DEBUG);
		logger.addAppender(capture);
	}

	@AfterEach
	void tearDown() {
		logger.removeAppender(capture);
		Configurator.setLevel(logger.getName(), savedLevel);
	}

	@Test
	@DisplayName("a file with a Hold_Up block logs no unknown-key warning for it")
	void holdUpBlock_isNotReportedUnknown() throws Exception {
		String yaml = "Respawn_Cooldown: 60\nHold_Up:\n   Enable: true\n   Range: 6.0\n";
		FileHandler handler = mock(FileHandler.class);
		when(handler.getParsedDocument()).thenReturn(
				new ConfigParser().parse(Path.of("trader_settings.yml"), new StringReader(yaml), new ConfigReport()));
		FileManager fileManager = mock(FileManager.class);
		when(fileManager.getFile("trader_settings")).thenReturn(handler);

		TraderSettingsImpl settings = new TraderSettingsImpl(fileManager);

		assertEquals(60, settings.getRespawnCooldownSeconds());
		assertTrue(lines.stream().noneMatch(l -> l.contains("Hold_Up")), lines.toString());
	}
}
