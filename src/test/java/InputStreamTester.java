import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Scanner;

public class InputStreamTester {

	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);
		try (InputStream data = new FileInputStream(scanner.nextLine())) {
			YamlConfiguration yamlConfiguration = processData(data);
			System.out.println(yamlConfiguration.get("Commands.Prefix"));
		} catch (IOException exception) {
			System.out.println("File not found!");
		} catch (InvalidConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

	public static YamlConfiguration processData(InputStream data) throws IOException, InvalidConfigurationException {
		YamlConfiguration yamlConfig = new YamlConfiguration();
		yamlConfig.load(new InputStreamReader(data));
		return yamlConfig;
	}

}
