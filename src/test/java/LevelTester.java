import me.luckyraven.level.Level;

import java.util.Scanner;

public class LevelTester {

	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);

		int   option;
		Level level = new Level(25, 1000);
		level.setFormula("base * level ^ 1.5");

		do {
			System.out.println("=== Level Tester ===");
			System.out.println(" 1) Add level");
			System.out.println(" 2) Remove level");
			System.out.println(" 3) Add experience");
			System.out.println(" 4) Remove experience");
			System.out.println(" 5) Next level experience");
			System.out.println(" 6) Stat");
			System.out.println(" 0) Exit");

			System.out.print("-> ");
			try {
				option = Integer.parseInt(scanner.nextLine().trim());
			} catch (NumberFormatException exception) {
				option = 0;
			}

			switch (option) {
				case 0 -> {
					System.out.println("Exiting the program...");
				}
				case 1 -> {
					System.out.print("Amount: ");
					Scanner addScanner = new Scanner(System.in);

					int amount;
					try {
						amount = Integer.parseInt(addScanner.nextLine().trim());
					} catch (NumberFormatException exception) {
						System.out.println("Need to input a number");
						amount = 0;
					}

					System.out.printf("Added %d experience level.\n", level.addLevels(amount));
				}
				case 2 -> {
					System.out.print("Amount: ");
					Scanner addScanner = new Scanner(System.in);

					int amount;
					try {
						amount = Integer.parseInt(addScanner.nextLine().trim());
					} catch (NumberFormatException exception) {
						System.out.println("Need to input a number");
						amount = 0;
					}

					level.removeLevels(amount);
					System.out.printf("Removed %d experience level.\n", amount);

				}
				case 3 -> {
					System.out.print("Amount: ");
					Scanner addScanner = new Scanner(System.in);

					double amount;
					try {
						amount = Double.parseDouble(addScanner.nextLine().trim());
					} catch (NumberFormatException exception) {
						System.out.println("Need to input a number");
						amount = 0D;
					}

					level.addExperience(amount, null);
					System.out.printf("Added %.2f experience level.\n", amount);

				}
				case 4 -> {
					System.out.print("Amount: ");
					Scanner addScanner = new Scanner(System.in);

					double amount;
					try {
						amount = Double.parseDouble(addScanner.nextLine().trim());
					} catch (NumberFormatException exception) {
						System.out.println("Need to input a number");
						amount = 0D;
					}

					level.removeExperience(amount);
					System.out.printf("Removed %.2f experience level.\n", amount);

				}
				case 5 -> {
					System.out.println("Amount of experience to complete the level");

					double amount = level.experienceCalculation(level.nextLevel());
					System.out.printf("To complete: %.2f\n", amount - level.getExperience());
					System.out.printf("Next level: %.2f\n", amount);
				}
				case 6 -> {
					System.out.println();
					System.out.println("Current stats");
					System.out.println("Level: " + level.getLevelValue());
					System.out.println("Experience: " + level.getExperience());
					System.out.println("Formula: " + level.getFormula());
					System.out.println();
				}
			}
		}
		while (option != 0);

		scanner.close();
	}

}
