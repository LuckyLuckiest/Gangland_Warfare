package org.luckyraven.gangland.gang.file;

import org.luckyraven.gangland.gang.GangLevels;
import lombok.CustomLog;
import lombok.Getter;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Gameplay rules read from {@code gang/gang_rules.yml} (0.17.0). Read once per load: the name pattern is compiled, the
 * rank preset is cleaned up, and any bad value falls back to its default with one warning. The preset permissions are
 * read on each call.
 */
@CustomLog
public class GangRules implements FileInitializer {

	static final String       DEFAULT_NAME_REGEX = "[A-Za-z0-9_]{3,16}";
	static final List<String> DEFAULT_PRESET     = List.of("member", "officer", "owner");

	private static final String FILE_NAME        = "gang_rules";
	private static final String NAME_PATTERN_KEY = "Name_Pattern";
	private static final String PRESET_KEY       = "Ranks.Preset";
	private static final String PERMISSIONS_KEY  = "Ranks.Preset_Permissions.";
	private static final String MAX_LEVEL_KEY    = "Progression.Max_Level";
	private static final String BASE_XP_KEY      = "Progression.Base_Experience";
	private static final int    DEFAULT_MAX_LEVEL = 20;
	private static final double DEFAULT_BASE_XP  = 200.0;

	private final FileHandler fileHandler;

	@Getter
	private Pattern      namePattern;
	@Getter
	private List<String> rankPreset;
	@Getter
	private int          maxLevel;
	@Getter
	private double       baseExperience;

	public GangRules(FileManager fileManager) {
		this.fileHandler = fileManager.getFile(FILE_NAME);
		initialize();
	}

	@Override
	public void initialize() {
		var config = fileHandler.getFileConfiguration();

		String regex = config.getString(NAME_PATTERN_KEY, DEFAULT_NAME_REGEX);
		try {
			namePattern = Pattern.compile(regex);
		} catch (PatternSyntaxException exception) {
			log.warn("gang_rules.yml {} is not a valid regular expression ({}); using {}", NAME_PATTERN_KEY,
			         exception.getDescription(), DEFAULT_NAME_REGEX);
			namePattern = Pattern.compile(DEFAULT_NAME_REGEX);
		}

		Set<String> cleaned = new LinkedHashSet<>();
		for (String rank : config.getStringList(PRESET_KEY)) {
			if (rank != null && !rank.isBlank()) {
				cleaned.add(rank.trim().toLowerCase(Locale.ROOT));
			}
		}
		if (cleaned.size() < 2) {
			log.warn("gang_rules.yml {} needs at least two ranks; using {}", PRESET_KEY, DEFAULT_PRESET);
			rankPreset = DEFAULT_PRESET;
		} else {
			rankPreset = List.copyOf(cleaned);
		}

		maxLevel       = Math.max(1, config.getInt(MAX_LEVEL_KEY, DEFAULT_MAX_LEVEL));
		baseExperience = Math.max(1.0, config.getDouble(BASE_XP_KEY, DEFAULT_BASE_XP));
		GangLevels.bind(maxLevel, baseExperience);
	}

	/** Permission nodes a preset rank gets on a fresh install, trimmed; empty when the rank has none listed. */
	public List<String> getPresetPermissions(String rank) {
		List<String> nodes = fileHandler.getFileConfiguration()
				.getStringList(PERMISSIONS_KEY + rank.toLowerCase(Locale.ROOT));
		List<String> trimmed = new ArrayList<>(nodes.size());
		for (String node : nodes) {
			if (node != null && !node.isBlank()) {
				trimmed.add(node.trim());
			}
		}
		return trimmed;
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

}
