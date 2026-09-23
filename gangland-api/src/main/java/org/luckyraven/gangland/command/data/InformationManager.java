package org.luckyraven.gangland.command.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.CustomLog;
import lombok.Getter;

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
		InputStream stream = Objects.requireNonNull(InformationManager.class.getResourceAsStream("/" + COMMANDS_RESOURCE),
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
			// Gson 2.8.0 API on purpose: Spigot 1.16.5 (the compile floor) bundles that Gson, where the static
			// JsonParser.parseReader (2.8.6+) and JsonObject.keySet (2.8.1+) do not exist yet.
			JsonElement root = new JsonParser().parse(json);
			if (!root.isJsonObject()) {
				log.warn("Help index: {} {} is not a JSON object; skipped", source, COMMANDS_RESOURCE);
				return 0;
			}

			JsonObject object = root.getAsJsonObject();
			int        added  = 0;
			for (Map.Entry<String, JsonElement> member : object.entrySet()) {
				String     key   = member.getKey();
				JsonObject entry = member.getValue().getAsJsonObject();
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
