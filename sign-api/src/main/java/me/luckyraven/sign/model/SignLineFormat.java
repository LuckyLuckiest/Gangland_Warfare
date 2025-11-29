package me.luckyraven.sign.model;

import lombok.Builder;
import lombok.Getter;
import me.luckyraven.sign.validation.SignValidationException;
import me.luckyraven.util.color.Color;

import java.util.function.Function;

@Getter
@Builder
public class SignLineFormat {

	private final int     lineNumber;
	private final boolean required;
	private final Color   defaultColor;
	private final String  prefix;
	private final String  suffix;

	private final Function<String, String> formatter;
	private final LineContentType          contentType;

	public static SignLineFormat empty(int lineNumber) {
		return SignLineFormat.builder()
							 .lineNumber(lineNumber)
							 .required(false)
							 .contentType(LineContentType.EMPTY)
							 .build();
	}

	public String format(String content, String moneySymbol) throws SignValidationException {
		if (contentType == LineContentType.EMPTY) {
			return "";
		}

		StringBuilder result = new StringBuilder();

		if (defaultColor != null) {
			result.append(defaultColor.getColorCode());
		}

		if (prefix != null) {
			result.append(prefix);
		}

		if (formatter == null) {
			throw new SignValidationException("No formatter set!", lineNumber, content);
		}

		String formattedContent = content;

		if (formattedContent != null && !formattedContent.isEmpty()) {
			formattedContent = formatter.apply(content);

			if (contentType == LineContentType.PRICE && moneySymbol != null) {
				formattedContent = Color.LIME.getColorCode() + moneySymbol + formattedContent;
			}
		}

		result.append(formattedContent);

		if (suffix != null) {
			result.append(suffix);
		}

		return result.toString();
	}

	public enum LineContentType {
		TITLE,
		PRICE,
		QUANTITY,
		CUSTOM_TEXT,
		EMPTY
	}

}
