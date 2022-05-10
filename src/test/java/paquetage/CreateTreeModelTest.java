package paquetage;

import org.junit.Assert;
import org.junit.Test;

public class CreateTreeModelTest {
    @Test
    public void CreateTreeModelTest() throws Exception {
        int expected_size = CreateTreeModel.create_stuff();
        Assert.assertEquals(expected_size, 2);
    }
}