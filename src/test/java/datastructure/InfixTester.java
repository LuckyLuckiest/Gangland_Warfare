package datastructure;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

public class InfixTester {

	public static void main(String[] args) {
		String formula = "sin(0.5) + 2 * cos(1.2) - ln(10) + logb(8, 2) + neg(4 + -2 - 41) + {level}";

		Function logb = new Function("logb", 2) {

			@Override
			public double apply(double... doubles) {
				return Math.log(doubles[0]) / Math.log(doubles[1]);
			}
		};

		Function neg = new Function("neg") {

			@Override
			public double apply(double... doubles) {
				return -doubles[0];
			}
		};

		Function ln = new Function("ln") {

			@Override
			public double apply(double... doubles) {
				return Math.log(doubles[0]);
			}
		};

		Function[] functions = {logb, neg, ln};

		Expression expression = new ExpressionBuilder(formula).variable("level")
		                                                      .functions(functions)
		                                                      .build()
		                                                      .setVariable("level", 2);

		System.out.printf("%s = %.2f", formula, expression.evaluate());
	}

}
