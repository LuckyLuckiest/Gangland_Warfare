package org.luckyraven.gangland.copsncrooks.wanted.hud;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HudSettings;
import org.luckyraven.gangland.copsncrooks.wanted.hud.TitleCue;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shared test inputs of the HUD tests: real {@link WantedMessages} over a bare file, and a fixed chase config. */
public final class HudFixtures {

	private HudFixtures() {
	}

	/** Colouring a line reads {@code Settings.moneySymbol}, which only a loaded settings.yml sets. */
	public static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		if (field.get(null) == null) {
			field.set(null, "$");
		}
	}

	/** Messages with every line at its in-code fallback. */
	public static WantedMessages messages(Path dir) throws IOException {
		Path file = dir.resolve("wanted_messages.yml");
		Files.writeString(file, "Crimes:\n   Assault_Cop: \"Assault on an officer\"\n   Unknown_Crime: \"Reported crime\"\n", StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(dir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new WantedMessages(fileManager);
	}

	/** A loader answering the shipped config with {@code hud} swapped in. */
	public static ChaseConfigLoader loader(HudSettings hud) {
		ChaseConfig       d      = ChaseConfig.DEFAULT;
		ChaseConfigLoader loader = mock(ChaseConfigLoader.class);
		when(loader.get()).thenReturn(new ChaseConfig(d.heat(), d.evasion(), hud, d.chargeSheet()));
		return loader;
	}

	/** The shipped HUD with each switch given. */
	public static HudSettings hud(boolean bossBar, boolean starCard, boolean title, boolean siren, boolean zoneRing,
	                              boolean compass) {
		TitleCue gain    = TitleCue.DEFAULT;
		TitleCue escaped = TitleCue.ESCAPED;
		return cues(bossBar, starCard, title ? gain : muted(gain), title ? gain : muted(gain),
		            title ? escaped : muted(escaped), siren, zoneRing, compass);
	}

	private static TitleCue muted(TitleCue cue) {
		return new TitleCue(false, cue.title(), cue.subtitle(), cue.fadeIn(), cue.stay(), cue.fadeOut());
	}

	/** The shipped HUD with the three title cues given. */
	public static HudSettings cues(boolean starCard, TitleCue gain, TitleCue lost, TitleCue escaped) {
		return cues(true, starCard, gain, lost, escaped, true, true, true);
	}

	private static HudSettings cues(boolean bossBar, boolean starCard, TitleCue gain, TitleCue lost, TitleCue escaped,
	                                boolean siren, boolean zoneRing, boolean compass) {
		HudSettings d = HudSettings.DEFAULT;
		return new HudSettings(bossBar, starCard, gain, lost, escaped, siren, d.sirenSound(), d.sirenVolume(),
		                       d.sirenPitch(), zoneRing, d.zoneParticle(), d.zonePoints(), compass);
	}
}
