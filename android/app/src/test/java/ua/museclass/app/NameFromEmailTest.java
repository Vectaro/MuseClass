package ua.museclass.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameFromEmailTest {
    @Test
    public void suggestsNameFromAddress() {
        assertEquals("Taras Uchen", Names.fromEmail("taras.uchen@dev.museclass"));
        assertEquals("Oksana", Names.fromEmail("oksana_77@gmail.com"));
        assertEquals("", Names.fromEmail("@x"));
        assertEquals("Vlad", Names.fromEmail("vlad"));
    }
}
