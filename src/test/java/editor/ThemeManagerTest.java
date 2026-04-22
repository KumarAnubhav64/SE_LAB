package editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ThemeManagerTest {

    @Test
    public void testInitialStateIsDark() {
        ThemeManager manager = new ThemeManager();
        assertTrue(manager.isDarkMode());
    }

    @Test
    public void testToggleTheme() {
        ThemeManager manager = new ThemeManager();
        
        manager.toggleTheme(null, null);
        assertFalse(manager.isDarkMode());
        
        manager.toggleTheme(null, null);
        assertTrue(manager.isDarkMode());
    }
}
