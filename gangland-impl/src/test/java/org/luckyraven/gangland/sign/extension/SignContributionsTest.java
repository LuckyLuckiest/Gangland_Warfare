package org.luckyraven.gangland.sign.extension;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.model.SignFormat;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.type.Sign;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Pins the sign-extension seam added for the gadget module split (T4): {@link SignContributions#createSigns} keeps
 * registration order and {@link SignContributions#openView} short-circuits on the first provider that claims a
 * name, never consulting later providers. {@link SignContributions#none()} stays inert (used when a server runs
 * with zero modules installed).
 */
@DisplayName("SignContributions — sign-extension seam")
class SignContributionsTest {

	private static Sign fakeSign(String label) {
		Sign sign = mock(Sign.class);
		org.mockito.Mockito.when(sign.createDefinition()).thenReturn(mock(SignTypeDefinition.class));
		org.mockito.Mockito.when(sign.createFormat()).thenReturn(mock(SignFormat.class));
		return sign;
	}

	@Test
	@DisplayName("createSigns returns every contribution's signs, in registration order")
	void createSigns_returnsContributionsInRegistrationOrder() {
		Sign firstSign  = fakeSign("first");
		Sign secondSign = fakeSign("second");

		SignTypeContribution first  = signPrefix -> List.of(firstSign);
		SignTypeContribution second = signPrefix -> List.of(secondSign);

		SignContributions contributions = new SignContributions(List.of(first, second), List.of());

		assertEquals(List.of(firstSign, secondSign), contributions.createSigns("glw-"));
	}

	@Test
	@DisplayName("openView returns true on the first provider that claims the name and never consults later ones")
	void openView_firstMatchingProviderWins_laterProvidersNeverConsulted() {
		SignViewProvider first  = (player, content) -> true;
		SignViewProvider second = (player, content) -> {
			throw new AssertionError("second provider must never be consulted once the first claims the name");
		};

		SignContributions contributions = new SignContributions(List.of(), List.of(first, second));

		assertTrue(contributions.openView(mock(Player.class), "anything"));
	}

	@Test
	@DisplayName("openView returns false when no provider claims the name")
	void openView_noProviderClaims_returnsFalse() {
		SignViewProvider first = (player, content) -> false;

		SignContributions contributions = new SignContributions(List.of(), List.of(first));

		assertFalse(contributions.openView(mock(Player.class), "anything"));
	}

	@Test
	@DisplayName("none() is inert: no signs, no view opened")
	void none_isInert() {
		SignContributions contributions = SignContributions.none();

		assertEquals(List.of(), contributions.createSigns("glw-"));
		assertFalse(contributions.openView(mock(Player.class), "anything"));
	}
}
