package org.luckyraven.gangland.sign.service;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.handler.SignHandler;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.registry.SignTypeRegistry;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SignInteraction - a refused interaction says why")
class SignInteractionTest {

	private final Player            player      = mock(Player.class);
	private final ParsedSign        sign        = mock(ParsedSign.class);
	private final SignHandler       handler     = mock(SignHandler.class);
	private final SignInformation   information = mock(SignInformation.class);
	private final SignInteraction   interaction;

	SignInteractionTest() {
		SignTypeRegistry   registry   = mock(SignTypeRegistry.class);
		SignTypeDefinition definition = mock(SignTypeDefinition.class);
		when(registry.getDefinition(any())).thenReturn(Optional.of(definition));
		when(definition.getHandler()).thenReturn(handler);
		when(handler.canHandle(player, sign)).thenReturn(false);

		interaction = new SignInteraction("", registry, mock(SignFormatterService.class), information);
	}

	@Test
	@DisplayName("the blocking aspect's reason is sent instead of the generic fallback")
	void refused_sendsTheHandlersReason() {
		when(handler.failureReason(player, sign)).thenReturn("You don't have enough stone!");

		assertFalse(interaction.handlerInteraction(player, sign));

		verify(information).sendError(player, "You don't have enough stone!");
	}

	@Test
	@DisplayName("with no reason available the generic fallback is kept")
	void refused_withoutReason_keepsFallback() {
		assertFalse(interaction.handlerInteraction(player, sign));

		verify(information).sendError(player, "Might be missing something!");
	}
}
