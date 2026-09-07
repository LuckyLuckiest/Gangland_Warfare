package org.luckyraven.gangland.sign.handler;

import org.bukkit.entity.Player;
import org.luckyraven.gangland.sign.aspect.AspectResult;
import org.luckyraven.gangland.sign.aspect.SignAspect;
import org.luckyraven.gangland.sign.model.ParsedSign;

import java.util.ArrayList;
import java.util.List;

public class AspectBasedSignHandler implements SignHandler {

	private final List<SignAspect> aspects;

	public AspectBasedSignHandler(List<SignAspect> aspects) {
		this.aspects = new ArrayList<>(aspects);
	}

	@Override
	public List<AspectResult> handle(Player player, ParsedSign sign) {
		List<AspectResult> results = new ArrayList<>();

		for (SignAspect aspect : aspects) {
			if (!aspect.canExecute(player, sign)) {
				results.add(AspectResult.failure(aspect.getName() + ": Preconditions not met"));
				break;
			}

			AspectResult result = aspect.execute(player, sign);

			results.add(result);

			if (!result.isContinueExecution()) {
				break;
			}
		}

		return results;
	}

	@Override
	public boolean canHandle(Player player, ParsedSign sign) {
		return aspects.stream().allMatch(aspect -> aspect.canExecute(player, sign));
	}
}
