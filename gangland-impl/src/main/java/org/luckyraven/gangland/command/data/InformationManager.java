package org.luckyraven.gangland.command.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.CustomLog;
import lombok.Getter;
import org.luckyraven.gangland.Gangland;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The {@code /glw} help index: one flat map from command key ({@code gang_invite_player}) to usage + description.
 * The core's {@code commands.json} is loaded by {@link #processCommands()}; each runtime module that ships its
 * own {@code commands.json} is folded in through {@link #merge(String, byte[])}, so help for a module's commands
 * exists exactly when the module is installed.
 */
@CustomLog
@Getter
public final class InformationManager {

	/** Jar-root resource name of the help fragment, for both the core jar and module jars. */
	public static final String COMMANDS_RESOURCE = "commands.json";

	private final Map<String, CommandInformation> commands;

	public InformationManager() {
		commands = new HashMap<>();
	}

	/** Load the core's bundled {@code commands.json} (read through the plugin classloader). */
	public void processCommands() {
		InputStream stream = Objects.requireNonNull(Gangland.class.getResourceAsStream("/" + COMMANDS_RESOURCE),
		                                            COMMANDS_RESOURCE + " is missing from the core jar");
		int added = merge("core", new InputStreamReader(stream, StandardCharsets.UTF_8));
		log.debug("Help index: {} core command(s)", added);
	}

	/**
	 * Fold a module's {@code commands.json} into the index. Keys already present are overwritten, so a module can
	 * also refine a core entry. Malformed input is logged and skipped rather than aborting the bootstrap.
	 *
	 * @return the number of entries read from {@code json}.
	 */
	public int merge(String source, byte[] json) {
		return merge(source, new InputStreamReader(new ByteArrayInputStream(json), StandardCharsets.UTF_8));
	}

	public int merge(String source, Reader json) {
		try {
			JsonElement root = JsonParser.parseReader(json);
			if (!root.isJsonObject()) {
				log.warn("Help index: {} {} is not a JSON object; skipped", source, COMMANDS_RESOURCE);
				return 0;
			}

			JsonObject object = root.getAsJsonObject();
			int        added  = 0;
			for (String key : object.keySet()) {
				JsonObject entry = object.get(key).getAsJsonObject();
				commands.put(key, new CommandInformation(entry.get("usage").getAsString(),
				                                         entry.get("description").getAsString()));
				added++;
			}
			if (!"core".equals(source)) {
				log.debug("Help index: {} command(s) merged from module {}", added, source);
			}
			return added;
		} catch (RuntimeException exception) {
			log.warn("Help index: {} {} could not be parsed: {}", source, COMMANDS_RESOURCE, exception.getMessage());
			return 0;
		}
	}

}
