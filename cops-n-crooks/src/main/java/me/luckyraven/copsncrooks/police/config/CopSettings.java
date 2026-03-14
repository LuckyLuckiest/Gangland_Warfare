package me.luckyraven.copsncrooks.police.config;

/**
 * Provides the cop count per wanted level, driven by either a formula or static parameters configured in
 * {@code settings.yml}.
 * <p>
 * Implementations live in {@code gangland-impl} and delegate to {@code me.luckyraven.file.configuration.SettingAddon},
 * keeping {@code cops-n-crooks} fully decoupled from the main plugin's file-loading infrastructure.
 */
public interface CopSettings {

	/**
	 * Returns how many cops should spawn for the given wanted level.
	 *
	 * @param level wanted level (1-based)
	 *
	 * @return cop count, always ≥ 1
	 */
	int getCountForLevel(int level);

	/**
	 * Returns the maximum wanted level. {@link YamlCopConfigProvider} generates one entry for every level from 1 to
	 * this value.
	 */
	int getMaxWantedLevel();
}
