package my.celium.org.betterpets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

class PetNameFormatTest {
    /** One entry per styled run: its text and style. */
    private record Run(String text, Style style) {
        int color() {
            return style.getColor() == null ? -1 : style.getColor().getValue();
        }
    }

    private static List<Run> runs(String raw) {
        MutableComponent parsed = PetNameFormat.parse(raw);
        List<Run> runs = new ArrayList<>();
        for (Component part : parsed.getSiblings()) {
            runs.add(new Run(part.getString(), part.getStyle()));
        }
        return runs;
    }

    @Test
    void plainNamesAreWhiteAndNotItalic() {
        List<Run> runs = runs("Rex");
        assertEquals(1, runs.size());
        assertEquals("Rex", runs.get(0).text());
        assertEquals(0xFFFFFF, runs.get(0).color());
        assertFalse(runs.get(0).style().isItalic());
        assertEquals("", PetNameFormat.parse("").getString());
        assertEquals("", PetNameFormat.parse(null).getString());
    }

    @Test
    void legacyColorsAndFormats() {
        List<Run> runs = runs("&6Sir &l&cBarks");
        assertEquals("Sir Barks", PetNameFormat.plain("&6Sir &l&cBarks"));
        assertEquals(0xFFAA00, runs.get(0).color());
        // A color code clears the earlier bold, like vanilla.
        assertEquals("Barks", runs.get(1).text());
        assertEquals(0xFF5555, runs.get(1).color());
        assertFalse(runs.get(1).style().isBold());

        List<Run> bold = runs("&c&lBarks");
        assertTrue(bold.get(0).style().isBold());
        assertEquals(0xFF5555, bold.get(0).color());
    }

    @Test
    void legacyHexFormsAndSectionSign() {
        assertEquals(0xFF55AA, runs("&#ff55aaPink").get(0).color());
        assertEquals(0xFF55AA, runs("{#FF55AA}Pink").get(0).color());
        assertEquals(0xFF55AA, runs("&x&f&f&5&5&a&aPink").get(0).color());
        assertEquals(0x55FF55, runs("§aGreen").get(0).color());
        assertEquals("Pink", PetNameFormat.plain("&x&f&f&5&5&a&aPink"));
    }

    @Test
    void legacyEscapesAndUnknownCodes() {
        assertEquals("Salt & Pepper", PetNameFormat.plain("Salt && Pepper"));
        assertEquals("A&zB", PetNameFormat.plain("A&zB"));
        assertEquals("A&", PetNameFormat.plain("A&"));
        assertEquals("&#zzzzzzX", PetNameFormat.plain("&#zzzzzzX"));
    }

    @Test
    void resetClearsEverything() {
        List<Run> runs = runs("&c&lHot&rDog");
        assertEquals("Dog", runs.get(1).text());
        assertEquals(0xFFFFFF, runs.get(1).color());
        assertFalse(runs.get(1).style().isBold());
        List<Run> mini = runs("<red><bold>Hot<reset>Dog");
        assertEquals(0xFFFFFF, mini.get(1).color());
        assertFalse(mini.get(1).style().isBold());
    }

    @Test
    void miniMessageColorsAndDecorations() {
        List<Run> runs = runs("<gold>Sir</gold> <b>Barks</b> <#ff55aa>Jr");
        assertEquals("Sir Barks Jr", PetNameFormat.plain("<gold>Sir</gold> <b>Barks</b> <#ff55aa>Jr"));
        assertEquals(0xFFAA00, runs.get(0).color());
        assertEquals(" ", runs.get(1).text());
        assertEquals(0xFFFFFF, runs.get(1).color());
        assertTrue(runs.get(2).style().isBold());
        assertEquals(0xFF55AA, runs.get(runs.size() - 1).color());
        assertEquals(0xFFAA00, runs("<color:gold>X").get(0).color());
        assertEquals(0xFFAA00, runs("<c:#ffaa00>X").get(0).color());
        assertTrue(runs("<italic>X").get(0).style().isItalic());
        assertTrue(runs("<u>X").get(0).style().isUnderlined());
        assertTrue(runs("<st>X").get(0).style().isStrikethrough());
        assertTrue(runs("<obf>X").get(0).style().isObfuscated());
        assertFalse(runs("<b>A<!b>B").get(1).style().isBold());
    }

    @Test
    void closingTagsAreOptionalAndForgiving() {
        // Unclosed tags run to the end; a stray closing tag is swallowed.
        assertEquals(0xFF5555, runs("<red>Rex").get(0).color());
        assertEquals("Rex", PetNameFormat.plain("</bold>Rex"));
        // </color> closes any color.
        List<Run> runs = runs("<red>A</color>B");
        assertEquals(0xFFFFFF, runs.get(1).color());
    }

    @Test
    void unknownTagsAndEscapesStayLiteral() {
        assertEquals("<3 Rex", PetNameFormat.plain("<3 Rex"));
        assertEquals("<hello>Rex", PetNameFormat.plain("<hello>Rex"));
        assertEquals("<red>Rex", PetNameFormat.plain("\\<red>Rex"));
        assertEquals("a < b", PetNameFormat.plain("a < b"));
    }

    @Test
    void gradientRunsEndToEnd() {
        List<Run> runs = runs("<gradient:#ff0000:#0000ff>Rex</gradient>!");
        assertEquals(4, runs.size());
        assertEquals(0xFF0000, runs.get(0).color());
        assertEquals(0x800080, runs.get(1).color());
        assertEquals(0x0000FF, runs.get(2).color());
        assertEquals("!", runs.get(3).text());
        assertEquals(0xFFFFFF, runs.get(3).color());
        // Three stops and named colors.
        List<Run> three = runs("<gradient:red:gold:green>abcde");
        assertEquals(0xFF5555, three.get(0).color());
        assertEquals(0xFFAA00, three.get(2).color());
        assertEquals(0x55FF55, three.get(4).color());
    }

    @Test
    void gradientKeepsDecorationsInside() {
        List<Run> runs = runs("<gradient:red:blue><b>Rex");
        assertTrue(runs.get(0).style().isBold());
        assertTrue(runs.get(2).style().isBold());
        assertNotEquals(runs.get(0).color(), runs.get(2).color());
    }

    @Test
    void rainbowCyclesAndReverses() {
        List<Run> runs = runs("<rainbow>abcdef");
        assertEquals(6, runs.size());
        assertEquals(0xFF0000, runs.get(0).color());
        assertEquals(0x00FFFF, runs.get(3).color());
        List<Run> reversed = runs("<rainbow:!>abcdef");
        assertNotEquals(runs.get(1).color(), reversed.get(1).color());
    }

    @Test
    void mixingBothFormats() {
        List<Run> runs = runs("&l<gradient:gold:red>Fire&r Dog");
        assertEquals("Fire Dog", PetNameFormat.plain("&l<gradient:gold:red>Fire&r Dog"));
        assertTrue(runs.get(0).style().isBold());
        assertEquals(0xFFAA00, runs.get(0).color());
        Run last = runs.get(runs.size() - 1);
        assertEquals(" Dog", last.text());
        assertFalse(last.style().isBold());
    }

    @Test
    void hueWheel() {
        assertEquals(0xFF0000, PetNameFormat.hue(0));
        assertEquals(0x00FF00, PetNameFormat.hue(1.0 / 3));
        assertEquals(0x0000FF, PetNameFormat.hue(2.0 / 3));
        assertEquals(0xFF0000, PetNameFormat.hue(1));
    }
}
