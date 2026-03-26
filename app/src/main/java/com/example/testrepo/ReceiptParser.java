package com.example.testrepo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ReceiptParser {
    private static final Pattern TRAILING_TOTAL_PATTERN = Pattern.compile(
            "([\\p{Sc}]?\\s*[-+]?\\d+(?:[\\.,]\\d{2})?\\s*(?:kr|sek|usd|eur)?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "[\\p{Sc}]?\\s*[-+]?\\d+(?:[\\.,]\\d{2})?\\s*(?:kr|sek|usd|eur)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern QUANTITY_WITH_UNIT_PRICE_PATTERN = Pattern.compile(
            "^(.*?)(?:\\s+)(\\d+\\s*(?:st|st\\.|stk|x|pack|pkt)?\\s*[*x])\\s*\\d+(?:[\\.,]\\d{2})?\\s*(?:kr|sek)?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRAILING_ARTICLE_NUMBER_PATTERN = Pattern.compile(
            "^(.*?)\\s+\\d{5,}$"
    );
    private static final Pattern TRAILING_UNIT_PRICE_PATTERN = Pattern.compile(
            "^(.*?)\\s+[-+]?\\d+(?:[\\.,]\\d{2})?$"
    );
    private static final Pattern TRAILING_QUANTITY_PATTERN = Pattern.compile(
            "^(.*?)\\s+\\d+(?:[\\.,]\\d+)?\\s*(?:st|st\\.|stk|x|pack|pkt|kg|hg|g|mg|l|cl|dl|ml)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PROMOTION_MARKER_PATTERN = Pattern.compile(
            "\\b\\d+\\s*f\\s*\\d+(?:[\\.,]\\d{2})?\\s*(?:kr|sek)?\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRAILING_SHORT_MARKER_PATTERN = Pattern.compile(
            "[,\\s]+\\p{L}{1,2}$"
    );
    private static final Pattern LEADING_MARKERS_PATTERN = Pattern.compile("^[*+~#]+\\s*");
    private static final Pattern TRAILING_PUNCTUATION_PATTERN = Pattern.compile("[\\s:;,.]+$");
    private static final Pattern LETTER_PATTERN = Pattern.compile(".*\\p{L}.*");
    private static final Pattern SUBSTANTIAL_WORD_PATTERN = Pattern.compile("\\p{L}{3,}");

    private static final String[] RECEIPT_KEYWORDS = {
            "total", "subtotal", "tax", "vat", "moms", "summa", "receipt",
            "cash", "card", "payment", "change", "att betala", "rabatt",
            "kvitto", "beskrivning"
    };
    private static final String[] DISCOUNT_KEYWORDS = {
            "discount", "rabatt", "bonus", "kupong", "erbjud"
    };
    private static final String[] IGNORED_LINE_KEYWORDS = {
            "total", "subtotal", "tax", "vat", "moms", "summa", "att betala",
            "change", "cash", "card", "visa", "mastercard", "payment", "balance",
            "discount", "rabatt", "saving", "savings", "receipt", "date", "time",
            "invoice", "amount", "terminal", "store", "bank", "tender", "kassa",
            "kort", "orgnr", "bonus", "medlemspris", "betalat", "betalningsinformation",
            "netto", "brutto"
    };

    ArrayList<ReceiptItem> extractReceiptItems(List<String> rows) {
        ArrayList<String> relevantRows = narrowToItemSection(rows);
        ArrayList<MutableReceiptItem> parsedItems = new ArrayList<>();
        String pendingDescription = null;

        for (String rawRow : relevantRows) {
            String normalizedRow = normalizeWhitespace(rawRow);
            if (normalizedRow.isEmpty()) {
                continue;
            }

            ParsedRow parsedRow = parseRow(normalizedRow);
            if (parsedRow == null && pendingDescription != null) {
                parsedRow = parseRow(pendingDescription + " " + normalizedRow);
                if (parsedRow != null && parsedRow.kind == RowKind.ITEM) {
                    pendingDescription = null;
                }
            }

            if (parsedRow == null) {
                pendingDescription = looksLikePendingDescription(normalizedRow)
                        ? normalizedRow
                        : null;
                continue;
            }

            if (parsedRow.kind == RowKind.ITEM) {
                parsedItems.add(new MutableReceiptItem(parsedRow.name, parsedRow.amountCents));
                pendingDescription = null;
            } else if (parsedRow.kind == RowKind.DISCOUNT && !parsedItems.isEmpty()) {
                MutableReceiptItem previousItem = parsedItems.get(parsedItems.size() - 1);
                previousItem.amountCents += parsedRow.amountCents;
                previousItem.appendNameFragment(parsedRow.name);
                pendingDescription = null;
            } else {
                pendingDescription = null;
            }
        }

        ArrayList<ReceiptItem> result = new ArrayList<>();
        for (MutableReceiptItem parsedItem : parsedItems) {
            result.add(new ReceiptItem(parsedItem.name, parsedItem.amountCents));
        }
        return result;
    }

    boolean isReceiptDetected(List<String> rows, List<ReceiptItem> detectedItems) {
        if (detectedItems.size() >= 2) {
            return true;
        }

        boolean hasReceiptKeyword = false;
        int priceRowCount = 0;
        for (String rawRow : rows) {
            String row = normalizeWhitespace(rawRow);
            if (containsReceiptKeyword(row)) {
                hasReceiptKeyword = true;
            }
            if (hasTerminalPrice(row)) {
                priceRowCount++;
            }
        }

        return (!detectedItems.isEmpty() && hasReceiptKeyword)
                || (detectedItems.size() == 1 && priceRowCount >= 2);
    }

    @Nullable
    ReceiptItem parseReceiptItem(String rawRow) {
        ParsedRow parsedRow = parseRow(rawRow);
        if (parsedRow == null || parsedRow.kind != RowKind.ITEM) {
            return null;
        }
        return new ReceiptItem(parsedRow.name, parsedRow.amountCents);
    }

    @Nullable
    Integer parseEnteredPriceToCents(String rawPrice) {
        return parsePriceToCents(rawPrice);
    }

    @NonNull
    String formatAmount(int amountCents) {
        return formatCents(amountCents);
    }

    @Nullable
    private ParsedRow parseRow(String rawRow) {
        String row = normalizeWhitespace(rawRow);
        if (row.isEmpty() || !hasTerminalPrice(row)) {
            return null;
        }

        Matcher totalMatcher = TRAILING_TOTAL_PATTERN.matcher(row);
        if (!totalMatcher.find()) {
            return null;
        }

        String trailingAmount = normalizeWhitespace(totalMatcher.group(1));
        Integer amountCents = parsePriceToCents(trailingAmount);
        if (amountCents == null) {
            return null;
        }

        String leftSide = normalizeWhitespace(row.substring(0, totalMatcher.start()));
        if (leftSide.isEmpty()) {
            return null;
        }

        if (shouldTreatAsDiscountRow(leftSide, amountCents)) {
            return new ParsedRow(RowKind.DISCOUNT, extractDiscountNameFragment(leftSide), amountCents);
        }

        String itemName = extractDisplayName(leftSide);
        if (!looksLikeItemName(itemName)) {
            return null;
        }

        return new ParsedRow(RowKind.ITEM, itemName, amountCents);
    }

    private boolean hasTerminalPrice(String row) {
        return TRAILING_TOTAL_PATTERN.matcher(row).find();
    }

    private boolean containsReceiptKeyword(String row) {
        String lowered = row.toLowerCase(Locale.US);
        for (String keyword : RECEIPT_KEYWORDS) {
            if (lowered.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeDiscountRow(String leftSide) {
        String lowered = leftSide.toLowerCase(Locale.US);
        for (String keyword : DISCOUNT_KEYWORDS) {
            if (lowered.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String extractDisplayName(String leftSide) {
        String cleanedLeftSide = cleanText(leftSide);
        String tabularDescription = stripTrailingTableColumns(cleanedLeftSide);
        if (!tabularDescription.equals(cleanedLeftSide)) {
            return tabularDescription;
        }

        Matcher quantityMatcher = QUANTITY_WITH_UNIT_PRICE_PATTERN.matcher(cleanedLeftSide);
        if (!quantityMatcher.matches()) {
            return cleanedLeftSide;
        }

        String baseName = cleanText(quantityMatcher.group(1));
        String quantityMarker = normalizeWhitespace(quantityMatcher.group(2))
                .replaceAll("\\s+", "");

        if (baseName.isEmpty()) {
            return quantityMarker;
        }
        return baseName + " " + quantityMarker;
    }

    private ArrayList<String> narrowToItemSection(List<String> rows) {
        int sectionStartIndex = -1;
        for (int index = 0; index < rows.size(); index++) {
            String normalizedRow = normalizeWhitespace(rows.get(index));
            if (looksLikeItemSectionHeader(normalizedRow)) {
                sectionStartIndex = index + 1;
                break;
            }
        }

        if (sectionStartIndex < 0) {
            return new ArrayList<>(rows);
        }

        ArrayList<String> sectionRows = new ArrayList<>();
        for (int index = sectionStartIndex; index < rows.size(); index++) {
            String normalizedRow = normalizeWhitespace(rows.get(index));
            if (normalizedRow.isEmpty()) {
                continue;
            }
            if (looksLikeItemSectionFooter(normalizedRow)) {
                break;
            }
            sectionRows.add(normalizedRow);
        }
        return sectionRows.isEmpty() ? new ArrayList<>(rows) : sectionRows;
    }

    private boolean looksLikeItemSectionHeader(String row) {
        String lowered = row.toLowerCase(Locale.US);
        return lowered.contains("beskrivning")
                && (lowered.contains("summa")
                || lowered.contains("artikelnummer")
                || lowered.contains("pris"));
    }

    private boolean looksLikeItemSectionFooter(String row) {
        String lowered = row.toLowerCase(Locale.US);
        return lowered.contains("betalat")
                || lowered.contains("betalningsinformation")
                || lowered.contains("moms")
                || lowered.contains("netto")
                || lowered.contains("brutto")
                || lowered.contains("avrundning")
                || lowered.contains("kort");
    }

    private boolean shouldTreatAsDiscountRow(String leftSide, int amountCents) {
        if (looksLikeDiscountRow(leftSide)) {
            return true;
        }
        if (amountCents >= 0) {
            return false;
        }
        if (containsPromotionMarker(leftSide)) {
            return true;
        }
        return !looksLikeTabularItemRow(leftSide);
    }

    private boolean containsPromotionMarker(String value) {
        return PROMOTION_MARKER_PATTERN.matcher(value).find();
    }

    private boolean looksLikeTabularItemRow(String leftSide) {
        String stripped = stripTrailingTableColumns(cleanText(leftSide));
        return !stripped.equals(cleanText(leftSide));
    }

    private String stripTrailingTableColumns(String value) {
        String strippedQuantity = stripTrailingPattern(value, TRAILING_QUANTITY_PATTERN);
        if (strippedQuantity == null) {
            return value;
        }

        String strippedUnitPrice = stripTrailingPattern(strippedQuantity, TRAILING_UNIT_PRICE_PATTERN);
        if (strippedUnitPrice == null) {
            return value;
        }

        String strippedArticleNumber = stripTrailingPattern(
                strippedUnitPrice,
                TRAILING_ARTICLE_NUMBER_PATTERN
        );
        String candidateName = strippedArticleNumber == null
                ? strippedUnitPrice
                : strippedArticleNumber;
        return cleanText(candidateName);
    }

    @Nullable
    private String stripTrailingPattern(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        return normalizeWhitespace(matcher.group(1));
    }

    private String extractDiscountNameFragment(String leftSide) {
        String cleaned = cleanText(leftSide);
        cleaned = PROMOTION_MARKER_PATTERN.matcher(cleaned).replaceAll("");
        int commaIndex = cleaned.indexOf(',');
        if (commaIndex >= 0) {
            cleaned = cleaned.substring(0, commaIndex);
        }
        cleaned = TRAILING_SHORT_MARKER_PATTERN.matcher(cleaned).replaceFirst("");
        cleaned = cleanText(cleaned).replaceAll("[-/]+$", "");
        cleaned = cleanText(cleaned);
        return looksLikeItemName(cleaned) ? cleaned : "";
    }

    private boolean looksLikeItemName(String itemName) {
        if (itemName.length() < 2
                || !LETTER_PATTERN.matcher(itemName).matches()
                || !SUBSTANTIAL_WORD_PATTERN.matcher(itemName).find()) {
            return false;
        }

        String lowered = itemName.toLowerCase(Locale.US);
        for (String keyword : IGNORED_LINE_KEYWORDS) {
            if (lowered.contains(keyword)) {
                return false;
            }
        }

        return true;
    }

    private boolean looksLikePendingDescription(String row) {
        if (row.isEmpty() || hasTerminalPrice(row) || looksLikeDiscountRow(row)) {
            return false;
        }

        String cleaned = cleanText(row);
        if (!SUBSTANTIAL_WORD_PATTERN.matcher(cleaned).find()) {
            return false;
        }

        String lowered = cleaned.toLowerCase(Locale.US);
        for (String keyword : IGNORED_LINE_KEYWORDS) {
            if (lowered.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static Integer parsePriceToCents(String rawPrice) {
        String cleaned = normalizeWhitespace(rawPrice)
                .toLowerCase(Locale.US)
                .replace("sek", "")
                .replace("kr", "")
                .replaceAll("[\\p{Sc}\\s]", "")
                .replace(',', '.');

        if (cleaned.isEmpty()) {
            return null;
        }

        boolean negative = cleaned.startsWith("-");
        if (negative || cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1);
        }

        String[] parts = cleaned.split("\\.");
        if (parts.length == 0 || parts.length > 2) {
            return null;
        }

        int whole;
        int fraction = 0;
        try {
            whole = Integer.parseInt(parts[0]);
            if (parts.length == 2) {
                String fractionText = parts[1];
                if (fractionText.length() == 1) {
                    fractionText = fractionText + "0";
                } else if (fractionText.length() > 2) {
                    fractionText = fractionText.substring(0, 2);
                }
                fraction = Integer.parseInt(fractionText);
            }
        } catch (NumberFormatException exception) {
            return null;
        }

        int cents = whole * 100 + fraction;
        return negative ? -cents : cents;
    }

    private static String formatCents(int amountCents) {
        int absolute = Math.abs(amountCents);
        int whole = absolute / 100;
        int fraction = absolute % 100;
        String formatted = String.format(Locale.US, "%d,%02d", whole, fraction);
        return amountCents < 0 ? "-" + formatted : formatted;
    }

    private static String cleanText(String value) {
        String cleaned = normalizeWhitespace(value);
        cleaned = LEADING_MARKERS_PATTERN.matcher(cleaned).replaceFirst("");
        cleaned = TRAILING_PUNCTUATION_PATTERN.matcher(cleaned).replaceFirst("");
        return normalizeWhitespace(cleaned);
    }

    private static String normalizeWhitespace(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private enum RowKind {
        ITEM,
        DISCOUNT
    }

    private static final class ParsedRow {
        private final RowKind kind;
        private String name;
        private final int amountCents;

        private ParsedRow(RowKind kind, String name, int amountCents) {
            this.kind = kind;
            this.name = name;
            this.amountCents = amountCents;
        }
    }

    private static final class MutableReceiptItem {
        private String name;
        private int amountCents;

        private MutableReceiptItem(String name, int amountCents) {
            this.name = name;
            this.amountCents = amountCents;
        }

        private void appendNameFragment(String fragment) {
            String normalizedFragment = normalizeWhitespace(fragment);
            if (normalizedFragment.isEmpty()) {
                return;
            }

            String loweredName = name.toLowerCase(Locale.US);
            String loweredFragment = normalizedFragment.toLowerCase(Locale.US);
            if (loweredName.contains(loweredFragment)) {
                return;
            }

            name = normalizeWhitespace(name + " " + normalizedFragment);
        }
    }

    static final class ReceiptItem {
        private String name;
        private int amountCents;
        private final Set<String> selectedParticipantKeys = new HashSet<>();

        ReceiptItem(String name, int amountCents) {
            this.name = name;
            this.amountCents = amountCents;
        }

        @NonNull
        String getName() {
            return name;
        }

        void setName(@NonNull String name) {
            this.name = name;
        }

        @NonNull
        String getPrice() {
            return formatCents(amountCents);
        }

        int getAmountCents() {
            return amountCents;
        }

        void setAmountCents(int amountCents) {
            this.amountCents = amountCents;
        }

        boolean isParticipantSelected(String participantKey) {
            return selectedParticipantKeys.contains(participantKey);
        }

        void selectParticipant(String participantKey) {
            selectedParticipantKeys.add(participantKey);
        }

        void deselectParticipant(String participantKey) {
            selectedParticipantKeys.remove(participantKey);
        }

        void toggleParticipantSelection(String participantKey) {
            if (selectedParticipantKeys.contains(participantKey)) {
                selectedParticipantKeys.remove(participantKey);
            } else {
                selectedParticipantKeys.add(participantKey);
            }
        }
    }
}
