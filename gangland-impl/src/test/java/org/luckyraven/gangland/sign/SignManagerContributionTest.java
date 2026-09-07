package org.luckyraven.gangland.sign;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.sign.extension.SignTypeContribution;
import org.luckyraven.gangland.sign.handler.SignHandler;
import org.luckyraven.gangland.sign.model.SignFormat;
import org.luckyraven.gangland.sign.parser.SignParser;
import org.luckyraven.gangland.sign.registry.SignFormatRegistry;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.registry.SignTypeRegistry;
import org.luckyraven.gangland.sign.service.SignFormatterService;
import org.luckyraven.gangland.sign.service.SignInformation;
import org.luckyraven.gangland.sign.service.SignInteraction;
import org.luckyraven.gangland.sign.type.Sign;
import org.luckyraven.gangland.sign.validation.SignValidator;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.wearable.WearableService;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Pins the sign-extension seam end-to-end through {@link SignManager#setupSigns()} (gadget T4): a
 * {@link SignTypeContribution} bean present in the container gets appended to the definitions
 * {@code setupSigns()} builds, alongside the flip-stable core signs. **Containment assertions only, never a
 * total count** — flip 4 (weapon) removes six more core definitions and would turn a count assertion red with no
 * owning task in this checklist.
 */
@DisplayName("SignManager.setupSigns() — contributed sign types")
class SignManagerContributionTest {

	@Test
	@DisplayName("a stub SignTypeContribution's signs are appended, and the flip-stable core signs survive")
	void setupSigns_appendsContributedSigns_andKeepsCoreSigns() throws Exception {
		DependencyContainer container = new DependencyContainer();

		SignType stubType = new SignType("glw-stub-contrib", "STUB-CONTRIB");
		Sign     stubSign = new Sign() {
			@Override
			public SignTypeDefinition createDefinition() {
				return SignTypeDefinition.builder()
				                          .signType(stubType)
				                          .signValidator(mock(SignValidator.class))
				                          .signParser(mock(SignParser.class))
				                          .handler(mock(SignHandler.class))
				                          .build();
			}

			@Override
			public SignFormat createFormat() {
				return SignFormat.builder().formatName("stub-contrib").signTypePrefix(stubType.typed()).build();
			}
		};

		SignTypeContribution stubContribution = signPrefix -> List.of(stubSign);
		container.registerInstance(SignTypeContribution.class, stubContribution);

		SignFormatRegistry formatRegistry = new SignFormatRegistry();
		SignInteraction    signInteraction = new SignInteraction("glw", new SignTypeRegistry(),
		                                                         new SignFormatterService(formatRegistry),
		                                                         mock(SignInformation.class));

		@SuppressWarnings("unchecked")
		UserManager<org.bukkit.entity.Player> onlineUsers = mock(UserManager.class);
		@SuppressWarnings("unchecked")
		UserManager<org.bukkit.OfflinePlayer> offlineUsers = mock(UserManager.class);

		SignManager manager = new SignManager(mock(Gangland.class), "glw", new SignTypeRegistry(), signInteraction,
		                                      mock(WeaponService.class), mock(AmmunitionManager.class),
		                                      mock(UniqueItemAddon.class), onlineUsers, offlineUsers,
		                                      mock(WearableService.class), container);

		List<SignTypeDefinition> definitions = manager.setupSigns();

		Set<String> typedKeys = definitions.stream()
		                                   .map(definition -> definition.getSignType().typed())
		                                   .collect(Collectors.toSet());

		assertTrue(typedKeys.contains("glw-stub-contrib"), "the contributed sign must be present");
		assertTrue(typedKeys.contains("glw-buy"), "flip-stable core sign glw-buy must survive");
		assertTrue(typedKeys.contains("glw-sell"), "flip-stable core sign glw-sell must survive");
		assertTrue(typedKeys.contains("glw-view"), "flip-stable core sign glw-view must survive");
		assertTrue(typedKeys.contains("glw-wanted"), "flip-stable core sign glw-wanted must survive");
		assertTrue(typedKeys.contains("glw-bounty"), "flip-stable core sign glw-bounty must survive");
	}
}
