package me.luckyraven.sign.service;

import me.luckyraven.sign.aspect.AspectResult;
import me.luckyraven.sign.handler.SignHandler;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.registry.SignTypeRegistry;
import me.luckyraven.util.ChatUtil;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class SignInteraction extends SignInteractionService {

	private final SignTypeRegistry registry;

	public SignInteraction(SignTypeRegistry registry) {
		super(registry);

		this.registry = registry;
	}

	@Override
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

}
