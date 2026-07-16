package quantumforge.com.math;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MatrixAndCalculatorTest {

    @Test
    void evaluatesFortranStyleAndScientificExpressions() {
        assertEquals(100.0, Calculator.expr("1.0d2"), 1.0e-12);
        assertEquals(5.0, Calculator.expr("sqrt(16) + sin(0) + 1"), 1.0e-12);
        assertThrows(NumberFormatException.class, () -> Calculator.expr("D+3"));
    }

    @Test
    void invertsGeneralThreeByThreeMatrix() {
        double[][] matrix = {
                {4.0, 7.0, 2.0},
                {3.0, 6.0, 1.0},
                {2.0, 5.0, 3.0}
        };
        assertEquals(9.0, Matrix3D.determinant(matrix), 1.0e-12);
        double[][] product = Matrix3D.mult(matrix, Matrix3D.inverse(matrix));
        assertArrayEquals(new double[] {1.0, 0.0, 0.0}, product[0], 1.0e-10);
        assertArrayEquals(new double[] {0.0, 1.0, 0.0}, product[1], 1.0e-10);
        assertArrayEquals(new double[] {0.0, 0.0, 1.0}, product[2], 1.0e-10);
    }
}
