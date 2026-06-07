package com.innercosmos.ai.perception.dto;

/**
 * Lightweight reverse-geocoded location.
 * <p>
 * Fields are best-effort: any of them can be null if Nominatim did not provide
 * a value (e.g. the user is in the middle of the ocean or behind a strict
 * privacy policy). When everything is missing the service falls back to
 * city = "未知" so downstream code can rely on a non-null label.
 */
public record LocationInfo(String city, String street, String country, Double lat, Double lon) {

    /**
     * Human-readable label combining city and street when available.
     * Format: "北京 · 中关村大街" / "北京" / "未知".
     */
    public String label() {
        boolean hasStreet = street != null && !street.isBlank();
        boolean hasCity = city != null && !city.isBlank();
        if (hasStreet && hasCity) return city + " · " + street;
        if (hasCity) return city;
        if (hasStreet) return street;
        return "未知";
    }

    /**
     * City-only label, never null (empty string when missing).
     * Useful for the request layer when we only want the city tier.
     */
    public String cityOnly() {
        return city == null ? "" : city;
    }
}
