package me.luckyraven.sign.registry;

import lombok.Builder;
import lombok.Getter;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.aspect.SignAspect;
import me.luckyraven.sign.handler.SignHandler;
import me.luckyraven.sign.parser.SignParser;
import me.luckyraven.sign.validation.SignValidator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@Builder
public class SignTypeDefinition {

	private final SignType      signType;
	private final SignValidator signValidator;
	private final SignParser    signParser;
	private final SignHandler   handler;

	@Builder.Default
	private final List<SignAspect> aspects = new ArrayList<>();

	@Builder.Default
	private final String displayFormat = "&8[&r{type}&8]";

	private final List<Integer> linesToRemove;

	public List<SignAspect> getSortedAspects() {
		List<SignAspect> sorted = new ArrayList<>(aspects);

		sorted.sort(Comparator.comparingInt(SignAspect::getPriority).reversed());

		return sorted;
	}

	public void addAspect(SignAspect aspect) {
		aspects.add(aspect);
	}

}
