package com.leo.enterpriseinertraining.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowLoaderTest {

    private final WorkflowLoader loader = new WorkflowLoader();

    @Test
    void load_researcher_only_v1() {
        WorkflowDef def = loader.load("researcher_only_v1");
        assertEquals("researcher_only_v1", def.getName());
        assertEquals(1, def.getVersion());
        assertEquals(1, def.getNodes().size());

        WorkflowNode n = def.getNodes().get(0);
        assertEquals("research", n.getId());
        assertEquals("Researcher", n.getAgent());
        assertEquals("researcher_prompt@v1", n.getPrompt());
        assertEquals(1, n.getTools().size());
        assertEquals("hybrid_search", n.getTools().get(0));
    }

    @Test
    void load_unknown_throws() {
        assertThrows(RuntimeException.class, () -> loader.load("no_such_workflow"));
    }
}
