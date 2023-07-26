import me.luckyraven.datastructure.LinkedList;

import java.util.Scanner;

public class LinkedListTester {

	public static void main(String[] args) {
		Menu menu = new Menu("Single Linked List");
		menu.addOption("Add an element");
		menu.addOption("Delete an element");
		menu.addOption("Print the list");
		menu.addOption("Clear the list");
		menu.addOption("Quit");

		LinkedList<String> list    = new LinkedList<>();
		boolean            exit    = false;
		Scanner            scanner = new Scanner(System.in);
		String             element;

		do {
			String prompt = "";
			menu.displayMenu();
			int option = menu.selectOption();
			switch (option) {
				case 0 -> {
					System.out.print("\nEnter a string: ");
					element = scanner.nextLine().trim();
					list.add(element);
					prompt = String.format("Inserted '%s' to the list", element);
				}
				case 1 -> {
					System.out.print("\nEnter a string: ");
					element = scanner.nextLine().trim();
					if (list.delete(element)) prompt = String.format("Deleted '%s' from the list", element);
					else prompt = String.format("Element '%s' is not in the list", element);
				}
				case 2 -> {
					System.out.println("Printing the list\n");
					list.display();
				}
				case 3 -> {
					list.clear();
					prompt = "Cleared the list";
				}
				case 4 -> {
					exit = true;
					prompt = "Exiting the program";
				}
				default -> prompt = "Invalid choice...Try again!";
			}
			if (!prompt.isEmpty()) System.out.println(prompt);
		}
		while (!exit);
	}

}
