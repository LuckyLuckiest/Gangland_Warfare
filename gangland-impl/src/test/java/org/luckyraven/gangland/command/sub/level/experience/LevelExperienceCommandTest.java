package org.luckyraven.gangland.command.sub.level.experience;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;

import java.nio.file.Path;

import static org.mockito.Mockito.*;

/**
 * GI 0: {@code /glw level exp add/remove NaN|Infinity} used to fall through to {@link Level#addExperience} /
 * {@link Level#removeExperience} and then report a false success ({@code LEVEL_EXP_ADD}/{@code LEVEL_EXP_REMOVE})
 * even though the value never applied. {@link Level} now guards non-finite deltas itself (the root-cause fix);
 * this pins the command layer's matching UX fix — reply {@code MUST_BE_NUMBERS} instead of a false success.
 */
@DisplayName("LevelExperienceAdd/RemoveCommand - non-finite amount is rejected, not silently accepted")
class LevelExperienceCommandTest {

	@TempDir
	static Path tempDir;

	private User<Player> target;
	private Level         level;
	private CommandSender sender;

	@BeforeAll
	static void initStatics() {
		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider()
				              .withString("Errors.Prefix", "")
				              .withString("Errors.Must_Be_Numbers", "must be numbers"));
	}

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		target = mock(User.class);
		level  = mock(Level.class);
		sender = mock(CommandSender.class);

		when(target.getLevel()).thenReturn(level);
	}

	@ParameterizedTest(name = "add {0} is rejected, never reaches Level.addExperience")
	@ValueSource(strings = {"NaN", "Infinity", "-Infinity"})
	void applyAdd_nonFinite_rejected(String rawAmount) {
		LevelExperienceAddCommand.applyAdd(sender, rawAmount, target);

		verify(level, never()).addExperience(anyDouble(), any());
		verify(sender).sendMessage(contains("must be numbers"));
		verify(target, never()).sendMessage(anyString());
	}

	@ParameterizedTest(name = "remove {0} is rejected, never reaches Level.removeExperience")
	@ValueSource(strings = {"NaN", "Infinity", "-Infinity"})
	void applyRemove_nonFinite_rejected(String rawAmount) {
		LevelExperienceRemoveCommand.applyRemove(sender, rawAmount, target);

		verify(level, never()).removeExperience(anyDouble());
		verify(sender).sendMessage(contains("must be numbers"));
		verify(target, never()).sendMessage(anyString());
	}

}
