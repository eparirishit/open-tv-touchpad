package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.Assert.*;

public class MouseReportTest {
    @Test public void payloadsHaveFiveBytesWithoutAnEmbeddedReportId() {
        assertEquals(1, MouseReport.ID);
        assertEquals(5, MouseReport.SIZE);
        assertArrayEquals(new byte[] {0, 0, 0, 0, 0}, MouseReport.neutral());
        assertArrayEquals(new byte[] {0, 12, -34, 0, 0}, MouseReport.move(12, -34));
        assertArrayEquals(new byte[] {0, 0, 0, 0, 0}, MouseReport.move(0, 0));
        assertArrayEquals(new byte[] {1, 0, 0, 0, 0}, MouseReport.clickDown());
        assertArrayEquals(new byte[] {0, 0, 0, 0, 0}, MouseReport.neutral());
    }

    @Test public void movementPreservesBothSignedEndpoints() {
        assertArrayEquals(new byte[] {0, -127, 127, 0, 0}, MouseReport.move(-127, 127));
        assertArrayEquals(new byte[] {0, 127, -127, 0, 0}, MouseReport.move(127, -127));
    }

    @Test public void movementRejectsOutOfRangeValuesBeforeByteConversion() {
        for (int value : new int[] {Integer.MIN_VALUE, -129, -128, 128, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> MouseReport.move(value, 0));
            assertThrows(IllegalArgumentException.class, () -> MouseReport.move(0, value));
        }
    }

    @Test public void eachScrollDirectionChangesOnlyItsAxisByOne() {
        assertArrayEquals(new byte[] {0, 0, 0, 1, 0}, MouseReport.scroll(MouseReport.Direction.UP));
        assertArrayEquals(new byte[] {0, 0, 0, -1, 0}, MouseReport.scroll(MouseReport.Direction.DOWN));
        assertArrayEquals(new byte[] {0, 0, 0, 0, -1}, MouseReport.scroll(MouseReport.Direction.LEFT));
        assertArrayEquals(new byte[] {0, 0, 0, 0, 1}, MouseReport.scroll(MouseReport.Direction.RIGHT));
        assertArrayEquals(new byte[] {0, 0, 0, 0, 0}, MouseReport.neutral());
    }

    @Test public void nullDirectionIsAnIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> MouseReport.scroll(null));
    }

    @Test public void allReturnedArraysAreIsolatedFromLaterCalls() {
        List<Supplier<byte[]>> reports = new ArrayList<>(List.of(
            MouseReport::descriptor, MouseReport::neutral, MouseReport::clickDown,
            () -> MouseReport.move(23, -45)));
        for (MouseReport.Direction direction : MouseReport.Direction.values()) {
            reports.add(() -> MouseReport.scroll(direction));
        }
        for (Supplier<byte[]> report : reports) {
            byte[] first = report.get();
            byte[] expected = first.clone();
            byte[] second = report.get();
            assertNotSame(first, second);
            Arrays.fill(first, (byte) 0x55);
            assertArrayEquals(expected, second);
            assertArrayEquals(expected, report.get());
        }
    }

    @Test public void getReportAcceptsUnspecifiedOrSufficientInputBuffer() {
        for (int size : new int[] {0, 5, 6, Integer.MAX_VALUE}) {
            assertTrue(MouseReport.acceptsGetReport(1, 1, size));
        }
    }

    @Test public void getReportRejectsNegativeAndShortBuffers() {
        for (int size : new int[] {Integer.MIN_VALUE, -1, 1, 2, 3, 4}) {
            assertFalse(MouseReport.acceptsGetReport(1, 1, size));
        }
    }

    @Test public void getReportRejectsEveryOtherTypeAndId() {
        for (int value : new int[] {Integer.MIN_VALUE, -1, 0, 2, 3, 255, 257, Integer.MAX_VALUE}) {
            for (int size : new int[] {0, 5, 6}) {
                assertFalse(MouseReport.acceptsGetReport(value, 1, size));
                assertFalse(MouseReport.acceptsGetReport(1, value, size));
            }
        }
    }

    @Test public void descriptorDeclaresThreeButtonsPaddingAndFourRelativeSignedAxes() {
        List<InputField> fields = readInputs(MouseReport.descriptor());
        assertEquals(4, fields.size());
        assertEquals(new InputField(1, 0, 1, 3, 0x09, 0, 1, 0x02,
            List.of(1, 2, 3)), fields.get(0));
        assertEquals(new InputField(1, 3, 5, 1, 0x09, 0, 1, 0x01,
            List.of()), fields.get(1));
        assertEquals(new InputField(1, 8, 8, 3, 0x01, -127, 127, 0x06,
            List.of(0x30, 0x31, 0x38)), fields.get(2));
        assertEquals(new InputField(1, 32, 8, 1, 0x0c, -127, 127, 0x06,
            List.of(0x0238)), fields.get(3));
        int bits = fields.stream().mapToInt(field -> field.width() * field.count()).sum();
        assertEquals(40, bits);
        assertEquals(MouseReport.neutral().length * 8, bits);
    }

    private record InputField(int id, int offset, int width, int count, int page,
            int min, int max, int flags, List<Integer> usages) { }

    // Only the short-item subset used by this mouse; unsupported items fail the test.
    private static List<InputField> readInputs(byte[] descriptor) {
        List<InputField> fields = new ArrayList<>();
        List<Integer> usages = new ArrayList<>();
        int page = 0, min = 0, max = 0, width = 0, count = 0, id = 0;
        int usageMin = -1, usageMax = -1, offset = 0, depth = 0, collections = 0;
        for (int cursor = 0; cursor < descriptor.length;) {
            int prefix = descriptor[cursor++] & 0xff;
            assertNotEquals("long item is not supported", 0xfe, prefix);
            int size = prefix & 3;
            if (size == 3) size = 4;
            assertTrue("truncated short item", cursor + size <= descriptor.length);
            int value = 0;
            for (int i = 0; i < size; i++) value |= (descriptor[cursor++] & 0xff) << (8 * i);
            int signed = size == 0 ? 0 : value << (32 - size * 8) >> (32 - size * 8);
            int type = (prefix >> 2) & 3;
            int tag = prefix >> 4;
            if (type == 1) {
                switch (tag) {
                    case 0: page = value; break;
                    case 1: min = signed; break;
                    case 2: max = min < 0 ? signed : value; break;
                    case 7: width = value; break;
                    case 8: id = value; assertEquals(1, id); break;
                    case 9: count = value; break;
                    default: fail("unsupported global tag " + tag);
                }
            } else if (type == 2) {
                switch (tag) {
                    case 0: usages.add(value); break;
                    case 1: usageMin = value; break;
                    case 2: usageMax = value; break;
                    default: fail("unsupported local tag " + tag);
                }
            } else if (type == 0) {
                switch (tag) {
                    case 8:
                        assertEquals("input must be inside mouse/pointer collections", 2, depth);
                        assertTrue(width > 0 && count > 0);
                        if (usageMin >= 0) {
                            assertTrue(usageMax >= usageMin && usageMax - usageMin < 32);
                            for (int usage = usageMin; usage <= usageMax; usage++) usages.add(usage);
                        }
                        fields.add(new InputField(id, offset, width, count, page, min, max,
                            value, List.copyOf(usages)));
                        offset += width * count;
                        break;
                    case 10:
                        assertTrue("only mouse and pointer collections", depth < 2);
                        assertEquals(0x01, page);
                        assertEquals(List.of(depth == 0 ? 0x02 : 0x01), usages);
                        assertEquals(depth == 0 ? 1 : 0, value);
                        depth++;
                        collections++;
                        break;
                    case 12:
                        assertTrue("unbalanced end collection", depth > 0);
                        depth--;
                        break;
                    default: fail("unsupported main tag " + tag);
                }
                usages.clear();
                usageMin = usageMax = -1;
            } else {
                fail("reserved item type");
            }
        }
        assertEquals("unclosed collection", 0, depth);
        assertEquals(2, collections);
        return fields;
    }
}
