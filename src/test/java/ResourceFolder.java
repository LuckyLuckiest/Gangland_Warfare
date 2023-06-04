import me.luckyraven.Gangland;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ResourceFolder {

	public static void main(String[] args) {
		String path = Gangland.class.getProtectionDomain().getCodeSource().getLocation().getPath().substring(1);
		System.out.println(URLDecoder.decode(path, StandardCharsets.UTF_8));

		Scanner scanner = new Scanner(System.in);
//		jarFileMessage(scanner.nextLine());
		filesInDirectory(scanner.nextLine());
	}

	public static void jarFileMessage(String location) {
		try (JarFile jar = new JarFile(location)) {
			Iterator<JarEntry> entries = jar.stream().iterator();
			int                i       = 0;
			while (entries.hasNext()) {
				JarEntry entry = entries.next();
				if (!entry.getName().startsWith("message/")) continue;
				String name = entry.getName();
				if (i++ != 0) System.out.println(name.substring(name.lastIndexOf("/") + 1));
			}
		} catch (IOException exception) {
			System.out.println("File not found!");
		}
	}

	public static void filesInDirectory(String location) {
		File   directory = new File(location);
		File[] con       = directory.listFiles();

		Set<String> messages = new HashSet<>();

		if (con != null) for (File file : con) {
			messages.add(file.getName());
		}
		StringBuilder languages = new StringBuilder();

		String[] nam = messages.toArray(new String[0]);
		for (int i = 0; i < messages.size(); i++) {
			String file = nam[i];
			languages.append(file, file.lastIndexOf("_") + 1, file.lastIndexOf("."));
			if (i < messages.size() - 1) languages.append(", ");
		}

		System.out.println(languages);
	}


}
