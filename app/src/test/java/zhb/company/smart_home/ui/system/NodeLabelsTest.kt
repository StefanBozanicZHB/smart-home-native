package zhb.company.smart_home.ui.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeLabelsTest {
    @Test
    fun `only the nodes in use are active`() {
        assertEquals(listOf("node1", "node2", "node3", "node4", "node5", "node6", "node8"), NodeLabels.ACTIVE)
        assertTrue(NodeLabels.isActive("node4"))
        assertFalse(NodeLabels.isActive("testnode5")) // old bench board, retained status left on the broker
        assertFalse(NodeLabels.isActive("node0")) // retired
        assertFalse(NodeLabels.isActive("node7")) // not in use
    }
}
