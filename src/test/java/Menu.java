import me.luckyraven.datastructure.LinkedList;

import java.util.Scanner;

public class Menu {

	private final LinkedList<String> menu;
	private final String             title;

	/**
	 * Instantiates a new Menu.
	 *
	 * @param title the title of the menu
	 */
	public Menu(String title) {
		menu = new LinkedList<>();
		this.title = title;
	}

	/**
	 * Display the menu.
	 */
	public void displayMenu() {
		System.out.println("\n" + title);
		System.out.println("-".repeat(title.length()));
		System.out.println("Select one option:");
		menu.display();
	}

	/**
	 * Adds a new menu option.
	 *
	 * @param str option description
	 */
	public void addOption(String str) {
		menu.add(String.format("\t%d- %s.", menu.getSize() + 1, str.replace(".", "")));
	}

	/**
	 * Prompts the user to enter an integer value from the menu provided.
	 *
	 * @return the available option from the menu or returns -1 if not available
	 */
	public int selectOption() {
		System.out.print("--> ");
		Scanner option = new Scanner(System.in);
		if (!option.hasNextInt()) return -1;
		int val = option.nextInt() - 1;
		if (val > menu.getSize()) return -1;
		return val;
	}

}
