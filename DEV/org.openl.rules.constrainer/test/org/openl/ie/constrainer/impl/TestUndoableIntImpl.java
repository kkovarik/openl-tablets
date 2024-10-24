package org.openl.ie.constrainer.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.openl.ie.constrainer.Constrainer;
import org.openl.ie.constrainer.Undo;

/**
 * <p>
 * Title:
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2002
 * </p>
 * <p>
 * Company: Exigen Group, Inc.
 * </p>
 *
 * @author unascribed
 * @version 1.0
 */

public class TestUndoableIntImpl {
    private final Constrainer C = new Constrainer("TestUndoableIntImpl");

    @Test
    public void testCreateUndo() {
        UndoableIntImpl undoableInt = new UndoableIntImpl(C, 10);
        Undo[] stages = new Undo[100];
        for (int i = 1; i <= 100; i++) {
            stages[i - 1] = undoableInt.createUndo();
            stages[i - 1].undoable(undoableInt);
            undoableInt.setValue(10 + i);
            assertEquals(10 + i, undoableInt.value());
        }

        // ascending order
        for (int i = 0; i < 100; i++) {
            stages[i].undo();
            assertEquals(10 + i, undoableInt.value());
        }

        // descending order
        for (int i = 99; i >= 0; i--) {
            stages[i].undo();
            assertEquals(10 + i, undoableInt.value());
        }
    }

}