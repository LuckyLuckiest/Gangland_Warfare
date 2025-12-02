package me.luckyraven.sign.type;

import lombok.RequiredArgsConstructor;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.aspect.MoneyAspect;
import me.luckyraven.sign.aspect.SignAspect;
import me.luckyraven.sign.aspect.WantedAspect;
import me.luckyraven.sign.handler.AspectBasedSignHandler;
import me.luckyraven.sign.handler.SignHandler;
import me.luckyraven.sign.model.SignFormat;
import me.luckyraven.sign.model.SignLineFormat;
import me.luckyraven.sign.parser.SignParser;
import me.luckyraven.sign.parser.WantedParser;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.validation.SignValidator;
import me.luckyraven.sign.validation.WantedSignValidator;
import me.luckyraven.util.color.Color;
import org.bukkit.entity.Player;

import java.util.List;

@RequiredArgsConstructor
public class WantedSign implements Sign {

	private final UserManager<Player> userManager;
	private final SignType            signType;

	@Override
	public SignTypeDefinition createDefinition() {
		// validator & parser
		SignValidator validator = new WantedSignValidator(signType);
		SignParser    parser    = new WantedParser(signType);

		// aspect
		SignAspect wantedAspect = new WantedAspect(userManager);
		SignAspect moneyAspect  = new MoneyAspect(userManager, MoneyAspect.TransactionType.WITHDRAW);

		// handler
		List<SignAspect> aspects = List.of(moneyAspect, wantedAspect);
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
								  .formatter(s -> "&8[&c" + generated + "&8]")
								  .build();

		var line2 = SignLineFormat.builder()
								  .lineNumber(1)
								  .required(true)
								  .defaultColor(Color.GRAY)
								  .contentType(SignLineFormat.LineContentType.CUSTOM_TEXT)
								  .formatter(s -> "&7" + s)
								  .build();

		var line3 = SignLineFormat.builder()
								  .lineNumber(2)
								  .required(false)
								  .defaultColor(Color.YELLOW)
								  .contentType(SignLineFormat.LineContentType.CUSTOM_TEXT)
								  .formatter(s -> "&b" + s)
								  .build();

		var line4 = SignLineFormat.builder()
								  .lineNumber(3)
								  .required(false)
								  .defaultColor(Color.LIME)
								  .contentType(SignLineFormat.LineContentType.PRICE)
								  .formatter(s -> "&a" + s)
								  .build();

		var starsLine = SignLineFormat.builder()
									  .required(true)
									  .defaultColor(Color.ORANGE)
									  .contentType(SignLineFormat.LineContentType.CUSTOM_TEXT)
									  .formatter(s -> "&e" + s)
									  .build();

		// add line
		String increase = WantedType.INCREASE.name().toLowerCase();
		var conditionalAdd = SignFormat.ConditionalLineFormat.builder()
															 .triggerLineNumber(2)
															 .triggerValue(increase)
															 .targetLineNumber(3)
															 .makesNextLineRequired(true)
															 .format(starsLine)
															 .build();

		// remove line
		String remove = WantedType.REMOVE.name().toLowerCase();
		var conditionalRemove = SignFormat.ConditionalLineFormat.builder()
																.triggerLineNumber(2)
																.triggerValue(remove)
																.targetLineNumber(3)
																.makesNextLineRequired(true)
																.format(starsLine)
																.build();

		return builder.lineFormats(List.of(line1, line2, line3, line4))
					  .conditionalLine("2:" + increase, conditionalAdd)
					  .conditionalLine("2:" + remove, conditionalRemove)
					  .build();
	}

	public enum WantedType {
		INCREASE,
		REMOVE,
		CLEAR;
	}

}
