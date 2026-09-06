package dev.bokukoha.mcstoragemanager.platform.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimpleJsonTest {
    @Test
    void parsesNullValuesInObjectsAndArrays() {
        Object parsed = SimpleJson.parse("{\"missing\":null,\"values\":[null,{\"nested\":null}]}");

        assertEquals(null, ((Map<?, ?>) parsed).get("missing"));
        List<?> values = (List<?>) ((Map<?, ?>) parsed).get("values");
        assertEquals(2, values.size());
        assertEquals(null, values.getFirst());
        assertEquals(null, ((Map<?, ?>) values.get(1)).get("nested"));
    }

    @Test
    void parsedObjectsAndArraysRemainImmutable() {
        Map<?, ?> parsed = (Map<?, ?>) SimpleJson.parse("{\"value\":null,\"items\":[null]}");

        assertThrows(UnsupportedOperationException.class, () -> ((Map<String, Object>) parsed).put("other", null));
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) parsed.get("items")).add(null));
    }
}
