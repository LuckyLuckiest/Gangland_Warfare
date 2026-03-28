package me.luckyraven.sign.service;

import me.luckyraven.sign.aspect.AspectResult;
import me.luckyraven.sign.handler.SignHandler;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.registry.SignTypeRegistry;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class SignInteraction extends SignInteractionService {

	private final SignTypeRegistry registry;
	private final SignInformation  information;

	public SignInteraction(String prefix, SignTypeRegistry registry, SignFormatterService formatterService,
						   SignInformation information) {
		super(prefix, registry, formatterService);

		this.registry    = registry;
		this.information = information;
	}

	@Override
	public boolean handlerInteraction(Player player, ParsedSign sign) {
		Optional<SignTypeDefinition> definition = registry.getDefinition(sign.getSignType());

		if (definition.isEmpty()) {
			information.sendError(player, "Invalid sign type!");
			return false;
		}

		SignTypeDefinition def     = definition.get();
		SignHandler        handler = def.getHandler();

		if (!handler.canHandle(player, sign)) {
			information.sendError(player, "Might be missing something!");
			return false;
		}

		List<AspectResult> results = handler.handle(player, sign);

		boolean overallSuccess = true;
		for (AspectResult result : results) {
			if (!result.isSuccess()) {
				information.sendError(player, result.getMessage());
				overallSuccess = false;
				break;
			} else if (result.getMessage() != null && !result.getMessage().isEmpty()) {
				information.sendSuccess(player, result.getMessage());
			}
		}

		return overallSuccess;
	}

}
