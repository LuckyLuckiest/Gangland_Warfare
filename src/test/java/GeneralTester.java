import me.luckyraven.data.user.User;

import java.util.HashSet;
import java.util.Set;

public class GeneralTester {

	public static void main(String[] args) {
		Set<User<Integer>> integers = new HashSet<>();
		integers.add(new User<>(1));
		integers.add(new User<>(2));
		integers.add(new User<>(1));
		System.out.println(integers);
	}

}
