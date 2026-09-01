package org.luckyraven.gangland.sign.type;

import lombok.RequiredArgsConstructor;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.color.Color;
import org.luckyraven.gangland.gadget.car.CarManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.aspect.SignAspect;
import org.luckyraven.gangland.sign.aspect.ViewInventoryAspect;
import org.luckyraven.gangland.sign.handler.AspectBasedSignHandler;
import org.luckyraven.gangland.sign.handler.SignHandler;
import org.luckyraven.gangland.sign.model.SignFormat;
import org.luckyraven.gangland.sign.model.SignLineFormat;
import org.luckyraven.gangland.sign.parser.SignParser;
import org.luckyraven.gangland.sign.parser.ViewSignParser;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.validation.SignValidator;
import org.luckyraven.gangland.sign.validation.ViewSignValidator;
import org.luckyraven.gangland.weapon.WeaponService;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.wearable.WearableService;

import java.util.List;

@RequiredArgsConstructor
public class ViewSign implements Sign {

	private final Gangland          gangland;
	private final WeaponService     weaponService;
	private final AmmunitionManager ammunitionManager;
	private final CarManager        carManager;
	private final WearableService   wearableService;
	private final UniqueItemAddon   uniqueItemAddon;
	private final SignType          signType;

	@Override
	public SignTypeDefinition createDefinition() {
		// Create validator & parser
		SignValidator validator = new ViewSignValidator(signType, weaponService, ammunitionManager);
		SignParser    parser    = new ViewSignParser(signType);

		// aspect
		SignAspect viewAspect = new ViewInventoryAspect(gangland, weaponService, ammunitionManager, carManager,
		                                                wearableService, uniqueItemAddon);

		// handler
		List<SignAspect> aspects = List.of(viewAspect);
		SignHandler      handler = new AspectBasedSignHandler(aspects);

		var definition = SignTypeDefinition.builder()
		                                   .signType(signType)
		                                   .signValidator(validator)
		                                   .signParser(parser)
		                                   .handler(handler)
		                                   .build();

		definition.addAllAspects(aspects);

		return definition;
	}

	@Override
	public SignFormat createFormat() {
		String generated = signType.generated();
		var    builder   = SignFormat.builder().formatName(generated.toLowerCase()).signTypePrefix(signType.typed());

		var line1 = SignLineFormat.builder()
		                          .lineNumber(0)
		                          .required(true)
		                          .contentType(SignLineFormat.LineContentType.TITLE)
		                          .formatter(s -> "&8[&3" + generated + "&8]")
		                          .build();

		var line2 = SignLineFormat.builder()
		                          .lineNumber(1)
		                          .required(true)
		                          .defaultColor(Color.GRAY)
		                          .contentType(SignLineFormat.LineContentType.CUSTOM_TEXT)
		                          .formatter(s -> "&7" + s)
		                          .build();

		return builder.lineFormats(List.of(line1, line2))
		              .lineFormat(SignLineFormat.empty(2))
		              .lineFormat(SignLineFormat.empty(3))
		              .build();
	}

}
