package com.vmudrud.daftiescanner.notification.telegram;

import com.vmudrud.daftiescanner.common.listing.ListingResult;

import java.util.ArrayList;
import java.util.List;

public final class TelegramListingFormatter {

    private static final String DAFT_BASE = "https://www.daft.ie";
    private static final String PRICE_SUFFIX_PER_MONTH = " per month";
    private static final String PRICE_SUFFIX_SHORT = "/month";
    private static final String BED_SINGULAR_SUFFIX = " Bed";
    private static final String BED_PLURAL_SUFFIX = " Beds";
    private static final String LABEL_PRICE = "💶 Price";
    private static final String LABEL_TYPE = "🏠 Type";
    private static final String LABEL_BEDS = "🛏 Beds";
    // U+FE0F (VS16) forces emoji-style rendering for ⚡ so it occupies the same
    // visual cell-width as the other emojis inside the monospace code block.
    private static final String LABEL_BER = "⚡️ BER";
    private static final String COLUMN_SEPARATOR = " │ ";
    private static final String CODE_FENCE = "```";
    private static final String SHARE_LINK_LABEL = "🔗 Open / Share";
    private static final String BER_EXEMPT_PREFIX = "SI_";
    private static final String BER_EXEMPT_DISPLAY = "EXEMPT";

    private TelegramListingFormatter() {}

    public static String format(ListingResult l) {
        var sb = new StringBuilder();
        appendTitleLink(sb, l);
        appendTable(sb, l);
        appendShareLink(sb, l);
        return sb.toString();
    }

    private static void appendShareLink(StringBuilder sb, ListingResult l) {
        String url = DAFT_BASE + l.seoFriendlyPath();
        sb.append("\n\n[")
                .append(TelegramReplyFormatter.escapeMarkdownV2(SHARE_LINK_LABEL))
                .append("](")
                .append(TelegramReplyFormatter.escapeMarkdownV2(url))
                .append(")");
    }

    private static void appendTitleLink(StringBuilder sb, ListingResult l) {
        String url = DAFT_BASE + l.seoFriendlyPath();
        sb.append("*[")
                .append(TelegramReplyFormatter.escapeMarkdownV2(l.title()))
                .append("](")
                .append(TelegramReplyFormatter.escapeMarkdownV2(url))
                .append(")*\n\n");
    }

    private static void appendTable(StringBuilder sb, ListingResult l) {
        var rows = new ArrayList<String[]>();
        addRow(rows, LABEL_PRICE, priceShort(l.price()));
        addRow(rows, LABEL_TYPE, l.propertyType());
        addRow(rows, LABEL_BEDS, bedsValue(l));
        addRow(rows, LABEL_BER, berRating(l));
        if (rows.isEmpty()) {
            return;
        }
        int maxLabel = widestLabel(rows);
        sb.append(CODE_FENCE).append("\n");
        for (var r : rows) {
            sb.append(padRight(r[0], maxLabel))
                    .append(COLUMN_SEPARATOR)
                    .append(escapeForCodeBlock(r[1]))
                    .append("\n");
        }
        sb.append(CODE_FENCE);
    }

    private static void addRow(List<String[]> rows, String label, String value) {
        if (value != null && !value.isBlank()) {
            rows.add(new String[]{label, value});
        }
    }

    private static int widestLabel(List<String[]> rows) {
        int max = 0;
        for (var r : rows) {
            if (r[0].length() > max) {
                max = r[0].length();
            }
        }
        return max;
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        var sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String escapeForCodeBlock(String s) {
        return s.replace("\\", "\\\\").replace("`", "\\`");
    }

    private static String priceShort(String price) {
        if (price == null || price.isBlank()) {
            return null;
        }
        return price.endsWith(PRICE_SUFFIX_PER_MONTH)
                ? price.substring(0, price.length() - PRICE_SUFFIX_PER_MONTH.length()) + PRICE_SUFFIX_SHORT
                : price;
    }

    private static String bedsValue(ListingResult l) {
        String beds = l.numBedrooms();
        if (beds == null || beds.isBlank()) {
            return null;
        }
        if (beds.endsWith(BED_PLURAL_SUFFIX)) {
            return beds.substring(0, beds.length() - BED_PLURAL_SUFFIX.length());
        }
        if (beds.endsWith(BED_SINGULAR_SUFFIX)) {
            return beds.substring(0, beds.length() - BED_SINGULAR_SUFFIX.length());
        }
        return beds;
    }

    private static String berRating(ListingResult l) {
        var ber = l.ber();
        if (ber == null) {
            return null;
        }
        String rating = ber.rating();
        if (rating == null || rating.isBlank()) {
            return null;
        }
        // daft.ie returns "SI_*" codes for properties exempt from BER assessment.
        return rating.startsWith(BER_EXEMPT_PREFIX) ? BER_EXEMPT_DISPLAY : rating;
    }
}
