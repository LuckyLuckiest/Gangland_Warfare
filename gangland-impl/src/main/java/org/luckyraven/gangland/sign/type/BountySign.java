package org.luckyraven.gangland.sign.type;

import lombok.RequiredArgsConstructor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.color.Color;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.aspect.BountyAspect;
import org.luckyraven.gangland.sign.aspect.SignAspect;
import org.luckyraven.gangland.sign.handler.AspectBasedSignHandler;
import org.luckyraven.gangland.sign.handler.SignHandler;
import org.luckyraven.gangland.sign.model.SignFormat;
import org.luckyraven.gangland.sign.model.SignLineFormat;
import org.luckyraven.gangland.sign.parser.BountyParser;
import org.luckyraven.gangland.sign.parser.SignParser;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.validation.BountySignValidator;
import org.luckyraven.gangland.sign.validation.SignValidator;

import java.util.List;

@RequiredArgsConstructor
public class BountySign implements Sign {

	private final JavaPlugin                 plugin;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final UserManager<Player>        userManager;
	private final SignType                   signType;

	@Override
	public SignTypeDefinition createDefinition() {
		// validator & parser
		SignValidator validator = new BountySignValidator(signType);
		SignParser    parser    = new BountyParser(signType);

		// aspect
		SignAspect wantedAspect = new BountyAspect(plugin, offlineUserManager, userManager);

		// handler
		List<SignAspect> aspects = List.of(wantedAspect);
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

		return builder.lineFormats(List.of(line1, line2)).build();
	}

	public enum BountyType {
		VIEW,
		CLEAR;
	}

}
