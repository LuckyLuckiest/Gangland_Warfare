package datastructure;

import me.luckyraven.datastructure.JsonFormatter;

import java.util.Map;
import java.util.TreeMap;

public class JsonFormatTester {

	public static void main(String[] args) {
		String input = "{key1:value1, key2:{key3:value3, key4:{key5:value5}, key6:value6}, key7:[value7,value8,value9]}";

		JsonFormatter format = new JsonFormatter();
		// Format the input string to resemble JSON indentation
		String formattedString = format.formatToJson(input, "\t");

		// Print the formatted string
		System.out.println(formattedString);

		// Custom json
		Map<Object, Object> dataMap = new TreeMap<>();
		dataMap.put("name", "John");
		dataMap.put("age", 30);
		dataMap.put("isStudent", false);
		dataMap.put("number", null);

		Map<Object, Object> addressMap = new TreeMap<>();
		addressMap.put("street", "123 Main St");
		addressMap.put("city", "Exampleville");
		addressMap.put("postalCode", "12345");
		dataMap.put("address", addressMap);

		String[] hobbies = {"Reading", "Hiking", "Cooking"};
		dataMap.put("hobbies", hobbies);

		// json values
		String json = format.createJson(dataMap);
		System.out.println(json);

		// using formatToJson
		System.out.println(format.formatToJson(json, "\t"));
	}

}
