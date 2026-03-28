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
        assertEquals(2, item.getSplitQuantity());
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
    public void parsesTabularRowsFromSharedPdfReceipts() {
        ReceiptParser.ReceiptItem item = parser.parseReceiptItem(
                "Avfallsp\u00e5se dragsn 2099950 20,90 2,00 st 41,80"
        );

        assertNotNull(item);
        assertEquals("Avfallsp\u00e5se dragsn", item.getName());
        assertEquals("41,80", item.getPrice());
        assertEquals(2, item.getSplitQuantity());
    }

    @Test
    public void expandsDiscreteQuantityRowsIntoIndividualItems() {
        ReceiptParser.ReceiptItem item = parser.parseReceiptItem(
                "Risifrutti Hallon 2118781 12,95 4,00 st 51,80"
        );

        assertNotNull(item);

        ArrayList<ReceiptParser.ReceiptItem> expandedItems =
                parser.expandDiscreteQuantityItems(new ArrayList<>(Arrays.asList(item)));

        assertEquals(4, expandedItems.size());
        assertEquals("Risifrutti Hallon", expandedItems.get(0).getName());
        assertEquals("12,95", expandedItems.get(0).getPrice());
        assertEquals("12,95", expandedItems.get(1).getPrice());
        assertEquals("12,95", expandedItems.get(2).getPrice());
        assertEquals("12,95", expandedItems.get(3).getPrice());
    }

    @Test
    public void formatsGroupedDisplayNameWithQuantityPrefix() {
        ReceiptParser.ReceiptItem item = parser.parseReceiptItem(
                "Risifrutti Hallon 2118781 12,95 4,00 st 51,80"
        );

        assertNotNull(item);
        assertEquals("(4) Risifrutti Hallon", parser.getGroupedDisplayName(item));
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
        assertEquals(4, items.get(1).getSplitQuantity());
    }

    @Test
    public void expandsDiscountAdjustedQuantityItemUsingFinalTotal() {
        ArrayList<String> rows = new ArrayList<>(Arrays.asList(
                "*Mannafrutti Hallon 4st*12,95 51,80",
                "Rabatt: Ris-, mannam 4f40kr -11,80"
        ));

        ArrayList<ReceiptParser.ReceiptItem> items = parser.extractReceiptItems(rows);
        ArrayList<ReceiptParser.ReceiptItem> expandedItems = parser.expandDiscreteQuantityItems(items);

        assertEquals(4, expandedItems.size());
        assertEquals("Mannafrutti Hallon", expandedItems.get(0).getName());
        assertEquals("10,00", expandedItems.get(0).getPrice());
        assertEquals("10,00", expandedItems.get(1).getPrice());
        assertEquals("10,00", expandedItems.get(2).getPrice());
        assertEquals("10,00", expandedItems.get(3).getPrice());
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

    @Test
    public void parsesKivraTableSectionAndCombinesDiscountContinuationRows() {
        ArrayList<String> rows = new ArrayList<>(Arrays.asList(
                "ICA STORMARKNAD G\u00d6TEBORG",
                "TEL : 031 - 746 61 00",
                "Beskrivning Artikelnummer Pris M\u00e4ngd Summa(SEK)",
                "Avfallsp\u00e5se dragsn 2099950 20,90 2,00 st 41,80",
                "Basmatiris 1331022 39,95 1,00 st 39,95",
                "Deo RO 48h Epic Fr 2130281 32,95 1,00 st 32,95",
                "Eko Standmj 3% ESL 2095219 22,95 1,00 st 22,95",
                "*Havrekuddar Kakao 2105691 40,50 2,00 st 81,00",
                "Havrefras, R 2f69kr -12,00",
                "*Mannafrutti Hallon 2118781 12,95 4,00 st 51,80",
                "Ris-, mannam 4f40kr -11,80",
                "N\u00f6tf\u00e4rs 12% 2117200 154,00 1,00 st 154,00",
                "Olivolja Extra Vir 1327232 71,95 1,00 st 71,95",
                "Paprika R\u00f6d 1217425 64,95 1,00 kg 40,66",
                "Peppar R\u00f6d 1218001 129,00 1,00 kg 1,29",
                "Proteinshake chokl 2348289 38,95 2,00 st 77,90",
                "Salt Fint med jod 2127888 13,95 1,00 st 13,95",
                "Svartpeppar Grovma 2023201 79,95 1,00 st 79,95",
                "Teriyaki Sauce 1004151 43,95 1,00 st 43,95",
                "Betalat 730,30",
                "Moms % Moms Netto Brutto",
                "Kort 730,30"
        ));

        ArrayList<ReceiptParser.ReceiptItem> items = parser.extractReceiptItems(rows);

        assertEquals(14, items.size());
        assertEquals("Avfallsp\u00e5se dragsn", items.get(0).getName());
        assertEquals("41,80", items.get(0).getPrice());
        assertEquals("Basmatiris", items.get(1).getName());
        assertEquals("39,95", items.get(1).getPrice());
        assertEquals("Havrekuddar Kakao Havrefras", items.get(4).getName());
        assertEquals("69,00", items.get(4).getPrice());
        assertEquals("Mannafrutti Hallon Ris", items.get(5).getName());
        assertEquals("40,00", items.get(5).getPrice());
        assertEquals("Teriyaki Sauce", items.get(13).getName());
        assertEquals("43,95", items.get(13).getPrice());
        assertTrue(parser.isReceiptDetected(rows, items));
    }
}
