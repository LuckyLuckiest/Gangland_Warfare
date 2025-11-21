package me.luckyraven.sign.service;

import lombok.Getter;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.parser.SignParser;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.registry.SignTypeRegistry;
import me.luckyraven.sign.validation.SignValidationException;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

@Getter
public abstract class SignInteractionService {

	private final SignTypeRegistry registry;

	public SignInteractionService(SignTypeRegistry registry) {
		this.registry = registry;
	}

	// This method is called to be overridden if there is a specific logic
	public abstract boolean handlerInteraction(Player player, ParsedSign sign);

	public void validateSign(String[] lines) throws SignValidationException {
		if (lines == null || lines.length < 4) {
			throw new SignValidationException("The sign must have 4 lines!");
		}

		String title = lines[0];

		Optional<SignTypeDefinition> definition = registry.findByLine(title);

		if (definition.isEmpty()) {
			throw new SignValidationException("Unknown sign type: " + title);
		}

		SignTypeDefinition def = definition.get();

		def.getSignValidator().validate(lines);
	}

	public Optional<ParsedSign> parseSign(String[] lines, Location location) throws SignValidationException {
		Optional<SignTypeDefinition> definition = registry.findByLine(lines[0]);

		if (definition.isEmpty()) {
			return Optional.empty();
		}

		SignParser parser = definition.get().getSignParser();
		ParsedSign parsed = parser.parse(lines, location);

		return Optional.of(parsed);
	}

	public String[] formatForDisplay(String[] lines) {
		if (lines == null || lines.length < 4) {
			return lines;
		}

		Optional<SignTypeDefinition> defOpt = registry.findByLine(lines[0]);
		if (defOpt.isEmpty()) {
			return lines;
		}

		SignTypeDefinition definition = defOpt.get();

		// Format line 0 (sign type)
		String title     = definition.getDisplayFormat();
		String generated = definition.getSignType().generated();
		String type      = title.replace("{type}", generated);

		String[] formatted = new String[4];

		formatted[0] = ChatUtil.color(type);

		// Format other lines with default colors
		// content line
		formatted[1] = ChatUtil.color("&7" + lines[1]);

		// these are not always there
		String typed = definition.getSignType().typed();

		if (typed.equalsIgnoreCase("glw-view")) {
			formatted[2] = "";
			formatted[3] = "";
		} else {
			formatted[2] = ChatUtil.color("&5" + lines[2]);
			formatted[3] = ChatUtil.color("&a" + lines[3]);
		}

		return formatted;
	}

}
