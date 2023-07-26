public class GeneralTester {

	public static void main(String[] args) {
		int maxSlots = 54;
		int size     = 0;

		int inventorySize = Math.min((int) Math.ceil((double) size / 9) * 9, maxSlots - 9);

		System.out.println(inventorySize);
	}

}
