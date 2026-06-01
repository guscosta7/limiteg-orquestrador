package br.com.itau.limiteg.orquestrador.domain;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;

public class InstantAsStringConverter implements AttributeConverter<Instant> {

    @Override
    public AttributeValue transformFrom(Instant instant) {
        return AttributeValue.fromS(instant.toString());
    }

    @Override
    public Instant transformTo(AttributeValue value) {
        return Instant.parse(value.s());
    }

    @Override
    public EnhancedType<Instant> type() {
        return EnhancedType.of(Instant.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
