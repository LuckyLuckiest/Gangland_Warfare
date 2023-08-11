package me.luckyraven.datastructure;

import java.lang.reflect.Array;
import java.util.Map;

public class JsonFormatter {

	public String formatToJson(String format, String indent) {
		StringBuilder builder = new StringBuilder();
		int           index   = 0, indentLevel = 0;

		String modifiedFormat = format.replaceAll(": |, ", "").replaceAll("=", ":");
		while (index < modifiedFormat.length()) {
			char c = modifiedFormat.charAt(index);

			if (c == '{' || c == '[') {
				indentLevel++;
				builder.append(c).append("\n").append(getIndent(indent, indentLevel));
			} else if (c == '}' || c == ']') {
				indentLevel--;
				builder.append("\n").append(getIndent(indent, indentLevel)).append(c);
			} else if (c == ':') builder.append(c).append(" ");
			else if (c == ',') builder.append(c).append("\n").append(getIndent(indent, indentLevel));
			else builder.append(c);

			index++;
		}

		return builder.toString();
	}

	private String getIndent(String indent, int indentLevel) {
		return indent.repeat(Math.max(0, indentLevel));
	}

	public String createJson(Map<?, ?> map) {
		// need to check the instance of each value to map it correctly
		StringBuilder builder = new StringBuilder("{");

		int index = 0;
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			Object key   = entry.getKey();
			Object value = entry.getValue();

			// each key is a string so between \"key\"
			builder.append(stringDataType(key)).append(": ");

			// there are six data types

			// null
			if (value == null) {
				builder.append("null");
			}
			// string
			else if (value instanceof String) {
				builder.append(stringDataType(value));
			}
			// number
			// boolean
			else if (value instanceof Number || value instanceof Boolean) {
				builder.append(value);
			}
			// array
			else if (value.getClass().isArray()) {
				builder.append(arrayDataType(value));
			}
			// object (nested map)
			else if (value instanceof Map) {
				builder.append(createJson((Map<?, ?>) value));
			}

			if (index < map.size() - 1) builder.append(",");
			index++;
		}

		builder.append("}");

		return builder.toString();
	}

	private String arrayDataType(Object value) {
		StringBuilder arrayBuilder = new StringBuilder("[");
		int           length       = Array.getLength(value);

		for (int i = 0; i < length; i++) {
			Object element = Array.get(value, i);

			if (element == null) arrayBuilder.append("null");
			else if (element instanceof String) arrayBuilder.append(stringDataType(element));
			else arrayBuilder.append(element);

			if (i < length - 1) arrayBuilder.append(",");
		}

		arrayBuilder.append("]");
		return arrayBuilder.toString();
	}

	private String stringDataType(Object value) {
		return "\"" + value + "\"";
	}

}
