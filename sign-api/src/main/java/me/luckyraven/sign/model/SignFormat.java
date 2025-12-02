package me.luckyraven.sign.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class SignFormat {

	private final String formatName;
	private final String signTypePrefix;

	@Singular
	private final List<SignLineFormat> lineFormats;

	@Singular("conditionalLine")
	private final Map<String, ConditionalLineFormat> conditionalLines;

	public SignLineFormat getLineFormat(int lineNumber) {
		if (lineNumber < 0 || lineNumber >= lineFormats.size()) {
			return SignLineFormat.empty(lineNumber);
		}

		return lineFormats.get(lineNumber);
	}

	public boolean hasConditionalFormat(int lineNumber, String triggerValue) {
		String key = lineNumber + ":" + triggerValue.toLowerCase();

		return conditionalLines.containsKey(key);
	}

	public ConditionalLineFormat getConditionalFormat(int lineNumber, String triggerValue) {
		String key = lineNumber + ":" + triggerValue.toLowerCase();

		return conditionalLines.get(key);
	}

	@Getter
	@Builder
	public static class ConditionalLineFormat {
		private final int            triggerLineNumber;
		private final String         triggerValue;
		private final int            targetLineNumber;
		private final SignLineFormat format;
		private final boolean        makesNextLineRequired;
	}

}
