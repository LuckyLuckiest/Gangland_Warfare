import java.util.Date;

public class GeneralTester {

	public static void main(String[] args) {
		Date date = new Date();
		date.setTime(20240123070819L);
		System.out.println(date.toInstant().toString());
	}

}
