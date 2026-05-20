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

    @Test
    void load_multi_agent_v1_fanout_join() {
        WorkflowDef def = loader.load("multi_agent_v1");
        assertEquals("multi_agent_v1", def.getName());
        assertEquals(5, def.getNodes().size());

        // research 节点带 fanout
        WorkflowNode research = def.getNodes().stream()
                .filter(n -> "research".equals(n.getId())).findFirst().orElseThrow();
        assertNotNull(research.getFanout());
        assertEquals("${plan.subtopics}", research.getFanout().getFrom());
        assertEquals("all", research.getJoin());
        assertEquals("qwen-plus", research.getModel());
        assertTrue(research.getTools().contains("hybrid_search"));

        // critic 节点带 maxLoops + onNeedsRevision
        WorkflowNode critic = def.getNodes().stream()
                .filter(n -> "critic".equals(n.getId())).findFirst().orElseThrow();
        assertEquals(1, critic.getMaxLoops());
        assertEquals("write", critic.getOnNeedsRevision());
    }

    @Test
    void reload_clears_cache() {
        loader.load("researcher_only_v1");
        loader.reload();
        WorkflowDef def = loader.load("researcher_only_v1");
        assertNotNull(def);
    }
}
