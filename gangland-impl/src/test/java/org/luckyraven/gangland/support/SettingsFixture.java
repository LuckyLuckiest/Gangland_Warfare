package org.luckyraven.gangland.support;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Drives a real {@link Settings#initialize()} pass off a hand-written {@code settings.yml} fixture — the same
 * {@code FileHandler}/{@code FileManager} entry point {@code FileConfig} uses at bootstrap — so tests that call
 * through {@code GanglandChatUtil.color(...)} (directly, or transitively via {@code Messages}/{@code HelpInfo})
 * never see a {@code null Settings.getMoneySymbol()}. {@code String.replace(CharSequence, CharSequence)} calls
 * {@code replacement.toString()} unconditionally, so an uninitialized money symbol NPEs on <em>every</em>
 * {@code color(...)} call, not just ones containing the literal {@code %money_symbol%} token.
 *
 * <p>{@code Settings}' ~200 fields are process-wide statics with no reset hook (documentation/TESTING.md §4).
 * Every call here re-parses the fixture and re-assigns every static, so it is safe — and expected — to call this
 * from multiple test classes' {@code @BeforeAll}/{@code @BeforeEach} methods without regard to execution order.
 */
public final class SettingsFixture {

	private SettingsFixture() {
	}

	private static final String MINIMAL_YAML = """
			Money_Symbol: '$'
			Database:
			  Auto_Save:
			    Debug: false
			""";

	/**
	 * Initializes {@link Settings} with just enough YAML for the money-symbol/color() seam other test classes
	 * need — {@code Auto_Save.Debug} is pinned {@code false} so this fixture never flips
	 * {@code Settings.isAutoSaveDebug()} true for a later test class in the same JVM run.
	 */
	public static void initializeMinimal(Path tempDir) {
		try {
			write(tempDir, MINIMAL_YAML);
			initialize(tempDir);
		} catch (IOException e) {
			throw new IllegalStateException("failed to build the minimal Settings fixture", e);
		}
	}

	public static void write(Path tempDir, String yaml) throws IOException {
		Files.writeString(tempDir.resolve("settings.yml"), yaml, StandardCharsets.UTF_8);
	}

	/**
	 * Runs {@code new Settings(fileManager).initialize()} against a {@code settings.yml} already written to
	 * {@code tempDir} (see {@link #write(Path, String)}).
	 */
	public static void initialize(Path tempDir) {
		try {
			JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
			FileHandler handler     = new FileHandler(plugin, tempDir.resolve("settings.yml").toFile());
			FileManager fileManager = new FileManager(plugin);
			fileManager.addFile(handler, false);

			new Settings(fileManager).initialize();
		} catch (IOException e) {
			throw new IllegalStateException("failed to initialize Settings from fixture", e);
		}
	}
}
