package editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UIControllerTest {

    @Test
    public void testControllerInstantiation() {
        UIController controller = new UIController();
        assertNotNull(controller);
    }
}
