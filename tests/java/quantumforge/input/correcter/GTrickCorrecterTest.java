package quantumforge.input.correcter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

import quantumforge.input.QESCFInput;
import quantumforge.input.card.QEKPoints;

class GTrickCorrecterTest {
    @Test
    void gammaCenteringChangesOffsetsWithoutChangingGrid() {
        QESCFInput input = new QESCFInput();
        QEKPoints points = input.getCard(QEKPoints.class);
        points.setAutomatic();
        points.setKGrid(new int[] {5, 7, 9});
        points.setKOffset(new int[] {1, 1, 1});

        GTrickCorrecter correcter = new GTrickCorrecter(input);
        correcter.setGTrickEnabled(true);
        correcter.correctInput();

        assertArrayEquals(new int[] {5, 7, 9}, points.getKGrid());
        assertArrayEquals(new int[] {0, 0, 0}, points.getKOffset());
    }
}
