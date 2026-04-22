package editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GeminiModuleTest {

    @Test
    public void testGeminiModuleInstantiation() {
        GeminiModule module = new GeminiModule();
        assertNotNull(module);
    }
}
