package editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StatisticsModuleTest {

    private final StatisticsModule module = new StatisticsModule();

    @Test
    public void testComputeEmpty() {
        StatisticsModule.Stats stats = module.compute("");
        assertEquals(0, stats.words());
        assertEquals(0, stats.lines());
        assertEquals(0, stats.chars());
        assertEquals(0, stats.paragraphs());
        assertEquals(0, stats.tags());
    }

    @Test
    public void testComputeBasicStats() {
        String input = "Hello world\nThis is a test.\n\n<p>With tags</p>";
        StatisticsModule.Stats stats = module.compute(input);

        assertEquals(8, stats.words()); // Hello, world, This, is, a, test., <p>With, tags</p>
        assertEquals(4, stats.lines());
        assertTrue(stats.chars() > 0);
        assertEquals(2, stats.paragraphs()); // split by double newline
        assertEquals(2, stats.tags());       // <p> and </p> - just counts '<'
    }

    @Test
    public void testFormat() {
        StatisticsModule.Stats stats = new StatisticsModule.Stats(1, 2, 3, 4, 5);
        String formatted = module.format(stats);
        assertEquals("Words: 1 | Lines: 2 | Chars: 3 | Paragraphs: 4 | Tags: 5", formatted);
    }
}
