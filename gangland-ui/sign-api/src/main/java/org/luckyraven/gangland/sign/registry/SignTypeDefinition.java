package org.luckyraven.gangland.sign.registry;

import lombok.Builder;
import lombok.Getter;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.aspect.SignAspect;
import org.luckyraven.gangland.sign.bulk.BulkSignHandler;
import org.luckyraven.gangland.sign.handler.SignHandler;
import org.luckyraven.gangland.sign.parser.SignParser;
import org.luckyraven.gangland.sign.validation.SignValidator;

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

	/**
	 * Optional handler for shift-click bulk confirmations. {@code null} means bulk is not supported.
	 */
	private final BulkSignHandler bulkHandler;

	@Builder.Default
	private final List<SignAspect> aspects = new ArrayList<>();

	public List<SignAspect> getSortedAspects() {
		List<SignAspect> sorted = new ArrayList<>(aspects);

		sorted.sort(Comparator.comparingInt(SignAspect::getPriority).reversed());

		return sorted;
	}

	public void addAspect(SignAspect aspect) {
		aspects.add(aspect);
	}

	public void addAllAspects(List<SignAspect> aspects) {
		this.aspects.addAll(aspects);
	}

}
