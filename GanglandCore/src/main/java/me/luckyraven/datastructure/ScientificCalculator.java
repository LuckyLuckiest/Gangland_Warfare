package me.luckyraven.datastructure;

import lombok.Getter;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;
import net.objecthunter.exp4j.operator.Operator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScientificCalculator {

	@Getter private final String     formula;
	private final         Expression expression;

	public ScientificCalculator(String expression, @Nullable Map<String, Double> variables) {
		this.formula = expression;
		ExpressionBuilder builder = new ExpressionBuilder(formula);

		if (!(variables == null || variables.isEmpty())) builder.variables(variables.keySet())
																.operator(operators())
																.functions(functions());

		this.expression = builder.build();

		if (!(variables == null || variables.isEmpty())) this.expression.setVariables(variables);
	}

	public ScientificCalculator(String expression) {
		this(expression, null);
	}

	public double evaluate() {
		return expression.evaluate();
	}

	private List<Function> functions() {
		List<Function> functions = new ArrayList<>();

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

		functions.add(logb);
		functions.add(neg);
		functions.add(ln);

		return functions;
	}

	private List<Operator> operators() {
		List<Operator> operators = new ArrayList<>();

		Operator factorial = new Operator("!", 1, true, Operator.PRECEDENCE_POWER + 1) {

			@Override
			public double apply(double... args) {
				final int arg = (int) args[0];

				if ((double) arg != args[0])
					throw new IllegalArgumentException("Operand for factorial has to be an integer");

				if (arg < 0)
					throw new IllegalArgumentException("The operand of the factorial can not be less than zero");

				double result = 1;

				for (int i = 1; i <= arg; i++)
					 result *= i;

				return result;
			}
		};

		operators.add(factorial);

		return operators;
	}

}
