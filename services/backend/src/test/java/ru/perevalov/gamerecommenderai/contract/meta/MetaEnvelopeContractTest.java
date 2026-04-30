package ru.perevalov.gamerecommenderai.contract.meta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

class MetaEnvelopeContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static JsonSchema schema;

    @BeforeAll
    static void setUp() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream schemaStream = MetaEnvelopeContractTest.class
                .getResourceAsStream("/schema/meta-envelope.schema.json")) {
            assertThat(schemaStream).as("schema file").isNotNull();
            schema = factory.getSchema(schemaStream);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load meta-envelope schema", ex);
        }
    }

    @Test
    void validEnvelope_isAccepted() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "type": "reply",
                  "payload": { "text": "hello" }
                }
                """;

        assertThat(validate(json)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidSchemaVersionPayloads")
    void invalidSchemaVersion_isRejected(String json) throws Exception {
        assertThat(validate(json)).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidTypePayloads")
    void invalidType_isRejected(String json) throws Exception {
        assertThat(validate(json)).isNotEmpty();
    }

    @Test
    void missingPayload_isRejected() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "type": "reply"
                }
                """;

        assertThat(validate(json)).isNotEmpty();
    }

    @Test
    void emptyPayload_isAccepted() throws Exception {
        String json = """
                {
                  "schemaVersion": 1,
                  "type": "reply",
                  "payload": {}
                }
                """;

        assertThat(validate(json)).isEmpty();
    }

    private static Set<ValidationMessage> validate(String json) throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree(json);
        return schema.validate(node);
    }

    private static Stream<String> invalidSchemaVersionPayloads() {
        return Stream.of(
                """
                {
                  "type": "reply",
                  "payload": {}
                }
                """,
                """
                {
                  "schemaVersion": null,
                  "type": "reply",
                  "payload": {}
                }
                """,
                """
                {
                  "schemaVersion": 0,
                  "type": "reply",
                  "payload": {}
                }
                """,
                """
                {
                  "schemaVersion": "1",
                  "type": "reply",
                  "payload": {}
                }
                """
        );
    }

    private static Stream<String> invalidTypePayloads() {
        return Stream.of(
                """
                {
                  "schemaVersion": 1,
                  "payload": {}
                }
                """,
                """
                {
                  "schemaVersion": 1,
                  "type": "",
                  "payload": {}
                }
                """,
                """
                {
                  "schemaVersion": 1,
                  "type": "   ",
                  "payload": {}
                }
                """,
                """
                {
                  "schemaVersion": 1,
                  "type": "Reply",
                  "payload": {}
                }
                """
        );
    }
}
