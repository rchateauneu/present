package paquetage;

import org.junit.Assert;
import org.junit.Test;

public class CreateTreeModelTest {
    @Test
    public void CreateTreeModelTest() throws Exception {
        CreateTreeModel treeModel = new CreateTreeModel("CreateTreeModelTest.xml");
        int expected_size = treeModel.model.size();
        Assert.assertEquals(expected_size, 2);
    }
}