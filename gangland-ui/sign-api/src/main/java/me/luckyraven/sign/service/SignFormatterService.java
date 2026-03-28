package me.luckyraven.sign.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.sign.model.SignFormat;
import me.luckyraven.sign.model.SignLineFormat;
import me.luckyraven.sign.registry.SignFormatRegistry;
import me.luckyraven.sign.validation.SignValidationException;
import me.luckyraven.util.utilities.ChatUtil;

import java.util.Optional;

@Getter
@RequiredArgsConstructor
public class SignFormatterService {

	private final SignFormatRegistry formatRegistry;

	public String[] formatForDisplay(String[] lines, String moneySymbol) throws SignValidationException {
		if (lines == null || lines.length < 4) {
			throw new SignValidationException("The sign must have 4 lines!");
		}

		Optional<SignFormat> formatOpt = formatRegistry.getFormat(lines[0]);

		if (formatOpt.isEmpty()) {
			// try using the prefix
			Optional<SignFormat> formatByPrefix = formatRegistry.getFormatByPrefix(lines[0]);

			if (formatByPrefix.isPresent()) {
				formatOpt = formatByPrefix;
			}
		}

		if (formatOpt.isEmpty()) {
			throw new SignValidationException("Unknown sign format", 1, lines[0]);
		}

		validateRequiredLines(lines, formatOpt.get());

		SignFormat format    = formatOpt.get();
		String[]   formatted = new String[4];

		for (int i = 0; i < 4; i++) {
			formatted[i] = formatLine(format, lines, i, moneySymbol);
		}

		return formatted;
	}

	public void validateRequiredLines(String[] lines, SignFormat format) throws SignValidationException {
		for (int i = 0; i < 4; i++) {
			SignLineFormat lineFormat = format.getLineFormat(i);

			if (!lineFormat.isRequired()) continue;

			String line = lines[i];

			if (line == null || line.isEmpty()) {
				throw new SignValidationException("Required line " + (i + 1) + " is empty!", i, line);
			}

			for (int j = 0; j < i; j++) {
				if (!format.hasConditionalFormat(j, lines[j])) continue;

				var conditional = format.getConditionalFormat(j, lines[j]);

				if (!(conditional.isMakesNextLineRequired() && conditional.getTargetLineNumber() == i)) continue;

				if (line.trim().isEmpty()) {
					throw new SignValidationException(
							"Line " + (i + 1) + " is required when line " + (j + 1) + " contains '" +
							conditional.getTriggerValue() + "'");
				}
			}
		}
	}

	private String formatLine(SignFormat format, String[] lines, int lineNumber, String moneySymbol) throws
			SignValidationException {
		SignLineFormat lineFormat = format.getLineFormat(lineNumber);

		for (int i = 0; i < lineNumber; i++) {
			if (!format.hasConditionalFormat(i, lines[i])) continue;

			var conditional = format.getConditionalFormat(i, lines[i]);

			if (conditional.getTargetLineNumber() != lineNumber) continue;

			lineFormat = conditional.getFormat();
			break;
		}

		String content       = lines[lineNumber] != null ? lines[lineNumber] : "";
		String formattedLine = lineFormat.format(content, moneySymbol);

		return ChatUtil.color(formattedLine);
	}

}
