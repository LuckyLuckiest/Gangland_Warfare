public class GeneralTester {

	public static void main(String[] args) {
		System.out.println(GeneralTester.class.getProtectionDomain().getCodeSource().getLocation().getPath());
	}

}
