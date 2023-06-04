package me.luckyraven.command.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import me.luckyraven.Gangland;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class InformationManager {

	private @Getter
	final Map<String, CommandInformation> commands;

	public InformationManager() {
		commands = new HashMap<>();
	}

	public void processCommands() {
		JsonElement jsonElement = JsonParser.parseReader(
				new InputStreamReader(Objects.requireNonNull(Gangland.class.getResourceAsStream("/commands.json"))));
		JsonObject jsonObject = jsonElement.getAsJsonObject();
		JsonObject ind;
		for (String property : jsonObject.keySet()) {
			ind = jsonObject.get(property).getAsJsonObject();
			commands.put(property,
			             new CommandInformation(ind.get("usage").getAsString(), ind.get("description").getAsString()));
		}
	}

}
