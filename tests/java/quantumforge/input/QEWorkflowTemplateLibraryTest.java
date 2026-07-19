/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class QEWorkflowTemplateLibraryTest {

    @Test
    void curatedTemplatesAreWellFormed() {
        List<QEWorkflowTemplateLibrary.WorkflowTemplate> templates =
                QEWorkflowTemplateLibrary.templates();
        assertTrue(templates.size() >= 6, "A useful template set covers the core workflows");
        for (QEWorkflowTemplateLibrary.WorkflowTemplate template : templates) {
            assertFalse(template.getName().isBlank());
            assertFalse(template.getPurpose().isBlank(), template.getName());
            assertFalse(template.getPrerequisites().isBlank(),
                "Every template must name its prerequisites: " + template.getName());
            assertFalse(template.getPitfalls().isBlank(), template.getName());
        }
    }

    @Test
    void findIsCaseInsensitiveAndFailClosed() {
        assertTrue(QEWorkflowTemplateLibrary.find("SCF-BASIC").isPresent());
        assertEquals("vc-relax-crystal",
                QEWorkflowTemplateLibrary.find(" vc-relax-crystal ").orElseThrow().getName());
        assertTrue(QEWorkflowTemplateLibrary.find("phonon-gamma0").orElseThrow()
                .getPrerequisites().contains("1e-10"));
        assertTrue(QEWorkflowTemplateLibrary.find("made-up-flow").isEmpty(),
                "Unknown templates are refused, not improvised");
        assertTrue(QEWorkflowTemplateLibrary.find("").isEmpty());
        assertTrue(QEWorkflowTemplateLibrary.find(null).isEmpty());
    }
}
