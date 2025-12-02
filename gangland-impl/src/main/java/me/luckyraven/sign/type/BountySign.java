package me.luckyraven.sign.type;

import lombok.RequiredArgsConstructor;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.aspect.BountyAspect;
import me.luckyraven.sign.aspect.SignAspect;
import me.luckyraven.sign.handler.AspectBasedSignHandler;
import me.luckyraven.sign.handler.SignHandler;
import me.luckyraven.sign.model.SignFormat;
import me.luckyraven.sign.model.SignLineFormat;
import me.luckyraven.sign.parser.BountyParser;
import me.luckyraven.sign.parser.SignParser;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.validation.BountySignValidator;
import me.luckyraven.sign.validation.SignValidator;
import me.luckyraven.util.color.Color;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
