package com.innercosmos.config.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Slow-letter timestamps are stored as UTC LocalDateTime for the existing schema. This serializer
 * makes that otherwise implicit contract explicit on the wire, so a browser in Asia/Shanghai (or
 * any other timezone) converts an absolute instant instead of reinterpreting UTC as local time.
 */
public final class UtcLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
    @Override
    public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider serializers)
            throws IOException {
        generator.writeString(value.toInstant(ZoneOffset.UTC).toString());
    }
}
