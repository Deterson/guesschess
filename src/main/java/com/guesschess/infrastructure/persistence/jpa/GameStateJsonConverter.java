package com.guesschess.infrastructure.persistence.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.ObjectMapper;

/**
 * Jackson 3 (tools.jackson.*), pas la classique com.fasterxml.jackson.databind :
 * c'est ce que Spring Boot 4.1 embarque par defaut. Les exceptions Jackson 3 sont
 * non-checked, pas besoin de try/catch pour les convertir.
 */
@Converter
class GameStateJsonConverter implements AttributeConverter<GameStateJson, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(GameStateJson attribute) {
        return MAPPER.writeValueAsString(attribute);
    }

    @Override
    public GameStateJson convertToEntityAttribute(String dbData) {
        return MAPPER.readValue(dbData, GameStateJson.class);
    }
}
