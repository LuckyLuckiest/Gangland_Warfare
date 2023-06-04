public class RankTester {

	public static void main(String[] args) {
		double fv = 20000;
		double pv = 10000;
		double n  = 5;
		double r  = (Math.pow(fv / pv, 1 / n) - 1) / 100;
		System.out.println(r);
	}

}
