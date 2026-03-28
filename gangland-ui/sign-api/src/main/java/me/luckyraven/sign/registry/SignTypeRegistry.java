package me.luckyraven.sign.registry;

import me.luckyraven.sign.SignType;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SignTypeRegistry {

	private final Map<String, SignTypeDefinition> definitionsByTyped     = new HashMap<>();
	private final Map<String, SignTypeDefinition> definitionsByGenerated = new HashMap<>();

	public void register(SignTypeDefinition definition) {
		SignType signType = definition.getSignType();

		String typedKey     = normalize(signType.typed());
		String generatedKey = normalize(signType.generated());

		definitionsByTyped.put(typedKey, definition);
		definitionsByGenerated.put(generatedKey, definition);
	}

	public Optional<SignTypeDefinition> findByLine(String line) {
		String normalized = normalize(line);

		SignTypeDefinition definition = definitionsByTyped.get(normalized);

		if (definition != null) {
			return Optional.of(definition);
		}

		definition = definitionsByGenerated.get(normalized);

		return Optional.ofNullable(definition);
	}

	public Optional<SignTypeDefinition> getDefinition(SignType signType) {
		String typed     = signType.typed();
		String normalize = normalize(typed);

		return Optional.ofNullable(definitionsByTyped.get(normalize));
	}

	public boolean isRegistered(String typedName) {
		String normalize = normalize(typedName);

		return definitionsByTyped.containsKey(normalize);
	}

	public Map<String, SignTypeDefinition> getDefinitions() {
		return new HashMap<>(definitionsByTyped);
	}

	private String normalize(String line) {
		return ChatColor.stripColor(line).toLowerCase().replaceAll("[\\[\\]]", "").trim();
	}

}
