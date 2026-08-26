package cn.slainc.tenant.resolver;

public final class TenantIds {

    private TenantIds() {
    }

    public static Long parse(String value, TenantLookup lookup) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        Long numeric = parseNumeric(text);
        if (numeric != null) {
            return numeric;
        }
        return lookup == null ? null : lookup.findTenantIdByCode(text);
    }

    public static Long parseNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return null;
            }
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
