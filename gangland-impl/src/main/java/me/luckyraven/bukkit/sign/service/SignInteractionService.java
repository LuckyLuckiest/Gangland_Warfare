package me.luckyraven.bukkit.sign.service;

import me.luckyraven.bukkit.sign.aspect.AspectResult;
import me.luckyraven.bukkit.sign.handler.SignHandler;
import me.luckyraven.bukkit.sign.model.ParsedSign;
import me.luckyraven.bukkit.sign.parser.SignParser;
import me.luckyraven.bukkit.sign.registry.SignTypeDefinition;
import me.luckyraven.bukkit.sign.registry.SignTypeRegistry;
import me.luckyraven.bukkit.sign.validation.SignValidationException;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public record SignInteractionService(SignTypeRegistry registry) {

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

	public boolean handlerInteraction(Player player, ParsedSign sign) {
		Optional<SignTypeDefinition> definition = registry.getDefinition(sign.getSignType());

		if (definition.isEmpty()) {
			player.sendMessage(ChatUtil.errorMessage("Invalid sign type!"));
			return false;
		}

		SignTypeDefinition def     = definition.get();
		SignHandler        handler = def.getHandler();

		if (!handler.canHandle(player, sign)) {
			player.sendMessage(ChatUtil.errorMessage("You cannot use this sign right now!"));
			return false;
		}

		List<AspectResult> results = handler.handle(player, sign);

		boolean overallSuccess = true;
		for (AspectResult result : results) {
			if (!result.isSuccess()) {
				player.sendMessage(ChatUtil.errorMessage(result.getMessage()));
				overallSuccess = false;
				break;
			} else if (result.getMessage() != null && !result.getMessage().isEmpty()) {
				player.sendMessage(ChatUtil.prefixMessage(result.getMessage()));
			}
		}

		return overallSuccess;
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

		String[] formatted = new String[4];

		// Format line 0 (sign type)
		String title     = definition.getDisplayFormat();
		String generated = definition.getSignType().generated();
		String type      = title.replace("{type}", generated);

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
