package com.hamzaaziz.loansync.projector;

import com.daml.ledger.javaapi.data.DamlEnum;
import com.daml.ledger.javaapi.data.DamlRecord;
import com.daml.ledger.javaapi.data.Date;
import com.daml.ledger.javaapi.data.Int64;
import com.daml.ledger.javaapi.data.Numeric;
import com.daml.ledger.javaapi.data.Party;
import com.daml.ledger.javaapi.data.Text;
import com.daml.ledger.javaapi.data.Value;
import org.springframework.boot.json.JsonWriter;

import java.util.LinkedHashMap;

/** Converts a contract's create arguments to JSON. Decimals become strings so no precision is lost. */
final class ValueJson {

    private static final JsonWriter<Object> JSON = JsonWriter.standard();

    private ValueJson() {
    }

    static String toJson(DamlRecord record) {
        return JSON.writeToString(convert(record));
    }

    static Object convert(Value value) {
        return switch (value) {
            case DamlRecord r -> {
                var fields = new LinkedHashMap<String, Object>();
                int position = 0;
                for (var field : r.getFields()) {
                    fields.put(field.getLabel().orElse("_" + position), convert(field.getValue()));
                    position++;
                }
                yield fields;
            }
            case Party p -> p.getValue();
            case Text t -> t.getValue();
            case Numeric n -> n.getValue().stripTrailingZeros().toPlainString();
            case Int64 n -> n.getValue();
            case Date d -> d.getValue().toString();
            case DamlEnum e -> e.getConstructor();
            default -> value.toString();
        };
    }
}
