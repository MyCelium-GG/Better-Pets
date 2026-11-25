package my.celium.org.betterpets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import my.celium.org.betterpets.talk.ParrotTalk;
import org.junit.jupiter.api.Test;

class ParrotTalkTest {
    @Test
    void shortLinesComeBackWhole() {
        assertEquals("hello!", ParrotTalk.phrase("hello", false));
        assertEquals("found diamonds!", ParrotTalk.phrase("  found   diamonds!!  ", false));
        assertEquals("found diamonds, diamonds!", ParrotTalk.phrase("found diamonds", true));
    }

    @Test
    void longLinesKeepTheirLastWords() {
        assertEquals("...the nether fortress!", ParrotTalk.phrase("hey can someone help me i am lost in the nether fortress", false));
        assertEquals("", ParrotTalk.phrase("   ", false));
        String one = ParrotTalk.phrase("supercalifragilisticexpialidocious_and_more_letters", false);
        assertEquals("...", one.substring(0, 3));
    }
}
