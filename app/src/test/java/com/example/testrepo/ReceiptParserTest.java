package com.example.testrepo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public class ReceiptParserTest {
    private final ReceiptParser parser = new ReceiptParser();

    @Test
    public void keepsQuantityMarkerButUsesFinalRowTotal() {
        ReceiptParser.ReceiptItem item =
                parser.parseReceiptItem("Avfallsp\u00e5se dragsn 2st*20,90 41,80");

        assertNotNull(item);
        assertEquals("Avfallsp\u00e5se dragsn 2st*", item.getName());
        assertEquals("41,80", item.getPrice());
    }

    @Test
    public void parsesPlainItemRowsWithoutQuantityColumn() {
        ReceiptParser.ReceiptItem item =
                parser.parseReceiptItem("Basmatiris 39,95");

        assertNotNull(item);
        assertEquals("Basmatiris", item.getName());
        assertEquals("39,95", item.getPrice());
    }

    @Test
    public void ignoresDiscountRowsEvenWhenTheyEndWithAPrice() {
        ReceiptParser.ReceiptItem item =
                parser.parseReceiptItem("Rabatt: Havrefras, R 2f69kr -12,00");

        assertNull(item);
    }

    @Test
    public void mergesDiscountIntoPreviousQuantityItem() {
        ArrayList<String> rows = new ArrayList<>(Arrays.asList(
                "*Havrekuddar Kakao 2st*40,50 81,00",
                "Rabatt: Havrefras, R 2f69kr -12,00",
                "*Mannafrutti Hallon 4st*12,95 51,80",
                "Rabatt: Ris-, mannam 4f40kr -11,80"
        ));

        ArrayList<ReceiptParser.ReceiptItem> items = parser.extractReceiptItems(rows);

        assertEquals(2, items.size());
        assertEquals("Havrekuddar Kakao 2st*", items.get(0).getName());
        assertEquals("69,00", items.get(0).getPrice());
        assertEquals("Mannafrutti Hallon 4st*", items.get(1).getName());
        assertEquals("40,00", items.get(1).getPrice());
    }

    @Test
    public void combinesNameOnlyRowWithFollowingPriceOnlyRow() {
        ArrayList<String> rows = new ArrayList<>(Arrays.asList(
                "Salt Fint med jod",
                "13,95"
        ));

        ArrayList<ReceiptParser.ReceiptItem> items = parser.extractReceiptItems(rows);

        assertEquals(1, items.size());
        assertEquals("Salt Fint med jod", items.get(0).getName());
        assertEquals("13,95", items.get(0).getPrice());
    }

    @Test
    public void combinesNameOnlyRowWithFollowingQuantityAndTotalRow() {
        ArrayList<String> rows = new ArrayList<>(Arrays.asList(
                "Proteinshake chokl",
                "2st*38,95 77,90"
        ));

        ArrayList<ReceiptParser.ReceiptItem> items = parser.extractReceiptItems(rows);

        assertEquals(1, items.size());
        assertEquals("Proteinshake chokl 2st*", items.get(0).getName());
        assertEquals("77,90", items.get(0).getPrice());
    }

    @Test
    public void ignoresQuantityOnlyRowsWithoutItemDescription() {
        ReceiptParser.ReceiptItem item = parser.parseReceiptItem("2st*38,95 77,90");

        assertNull(item);
    }

    @Test
    public void detectsReceiptFromExampleStyleRows() {
        ArrayList<String> rows = new ArrayList<>(Arrays.asList(
                "Avfallsp\u00e5se dragsn 2st*20,90 41,80",
                "Basmatiris 39,95",
                "Deo RO 48h Epic Fr 32,95",
                "Eko Standmj 3% ESL 22,95",
                "*Havrekuddar Kakao 2st*40,50 81,00",
                "Rabatt: Havrefras, R 2f69kr -12,00",
                "*Mannafrutti Hallon 4st*12,95 51,80",
                "Rabatt: Ris-, mannam 4f40kr -11,80",
                "N\u00f6tf\u00e4rs 12% 154,00",
                "Paprika R\u00f6d 40,66",
                "Peppar R\u00f6d 1,29",
                "Summa 308,34"
        ));

        ArrayList<ReceiptParser.ReceiptItem> items = parser.extractReceiptItems(rows);

        assertEquals(9, items.size());
        assertTrue(parser.isReceiptDetected(rows, items));
        assertEquals("69,00", items.get(4).getPrice());
        assertEquals("40,00", items.get(5).getPrice());
    }
}
