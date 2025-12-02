package me.luckyraven.sign.type;

import lombok.RequiredArgsConstructor;
import me.luckyraven.Gangland;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.aspect.SignAspect;
import me.luckyraven.sign.aspect.ViewInventoryAspect;
import me.luckyraven.sign.handler.AspectBasedSignHandler;
import me.luckyraven.sign.handler.SignHandler;
import me.luckyraven.sign.model.SignFormat;
import me.luckyraven.sign.model.SignLineFormat;
import me.luckyraven.sign.parser.SignParser;
import me.luckyraven.sign.parser.ViewSignParser;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.validation.SignValidator;
import me.luckyraven.sign.validation.ViewSignValidator;
import me.luckyraven.util.color.Color;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;

import java.util.List;

@RequiredArgsConstructor
public class ViewSign implements Sign {

	private final Gangland        gangland;
	private final WeaponService   weaponService;
	private final AmmunitionAddon ammunitionAddon;
	private final SignType        signType;

	@Override
	public SignTypeDefinition createDefinition() {
		// Create validator & parser
		SignValidator validator = new ViewSignValidator(signType, weaponService, ammunitionAddon);
		SignParser    parser    = new ViewSignParser(signType);

		// aspect
		SignAspect viewAspect = new ViewInventoryAspect(gangland, weaponService, ammunitionAddon);

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
