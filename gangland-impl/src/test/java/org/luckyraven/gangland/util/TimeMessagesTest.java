package org.luckyraven.gangland.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link TimeMessages}: the "not initialized yet" guard, the one-shot {@code initialize()} idempotency (W11,
 * commands-messages-platform.md — a second {@code LanguageLoader} reload does NOT replace the singleton, which is
 * harmless only because its getters read {@code Messages.*} live rather than caching), and that its six getters
 * delegate to the matching {@code Messages.TIME_UNIT} constants.
 *
 * <p>{@code TimeMessages.instance} is a process-wide singleton with no public reset. Keystone's own
 * {@code StaticResets} uses the same reflection technique for its process statics (documentation/TESTING.md §4);
 * this class does the same, purely as test setup, and — critically — always leaves the singleton re-initialized
 * in {@code @AfterEach} so it never leaks a null instance into a later test class in this JVM.
 */
@DisplayName("TimeMessages")
class TimeMessagesTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void initSettings() {
		// getters_delegateToMessagesEnumConstants() below reaches GanglandChatUtil.color(), which unconditionally
		// reads Settings.getMoneySymbol() — see SettingsFixture's javadoc.
		SettingsFixture.initializeMinimal(tempDir);
	}

	@AfterEach
	void leaveInitializedForLaterTests() {
		TimeMessages.initialize();
	}

	private static void clearInstance() throws ReflectiveOperationException {
		Field field = TimeMessages.class.getDeclaredField("instance");
		field.setAccessible(true);
		field.set(null, null);
	}

	@Test
	@DisplayName("getInstance throws IllegalStateException before initialize has ever run")
	void getInstance_beforeInitialize_throws() throws ReflectiveOperationException {
		clearInstance();

		assertThrows(IllegalStateException.class, TimeMessages::getInstance);
	}

	@Test
	@DisplayName("initialize then getInstance returns a non-null singleton")
	void initialize_thenGetInstance_succeeds() throws ReflectiveOperationException {
		clearInstance();

		TimeMessages.initialize();

		assertNotNull(TimeMessages.getInstance());
	}

	@Test
	@DisplayName("initialize is a one-shot guard: a second call keeps the original instance")
	void initialize_isIdempotent() throws ReflectiveOperationException {
		clearInstance();
		TimeMessages.initialize();
		TimeMessages first = TimeMessages.getInstance();

		TimeMessages.initialize();

		assertSame(first, TimeMessages.getInstance(),
				"a reload's LanguageLoader.onLoaded callback re-runs initialize(), but the guard means the " +
						"singleton object never changes — harmless because its getters read Messages.* live");
	}

	@Test
	@DisplayName("the six getters delegate to the matching Messages.TIME_UNIT constants")
	void getters_delegateToMessagesEnumConstants() {
		Messages.init(new FakeMessageProvider()
				.withString("Time_Unit.Second", "sec")
				.withString("Time_Unit.Minute", "min")
				.withString("Time_Unit.Hour", "hr")
				.withString("Time_Unit.Day", "day")
				.withString("Time_Unit.Week", "wk")
				.withString("Time_Unit.Year", "yr"));
		TimeMessages.initialize();
		TimeMessages instance = TimeMessages.getInstance();

		assertEquals("sec", instance.getSecond());
		assertEquals("min", instance.getMinute());
		assertEquals("hr", instance.getHour());
		assertEquals("day", instance.getDay());
		assertEquals("wk", instance.getWeek());
		assertEquals("yr", instance.getYear());
	}
}
