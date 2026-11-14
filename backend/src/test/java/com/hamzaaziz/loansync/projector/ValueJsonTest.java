package com.hamzaaziz.loansync.projector;

import com.daml.ledger.javaapi.data.DamlEnum;
import com.daml.ledger.javaapi.data.DamlRecord;
import com.daml.ledger.javaapi.data.Date;
import com.daml.ledger.javaapi.data.Int64;
import com.daml.ledger.javaapi.data.Numeric;
import com.daml.ledger.javaapi.data.Party;
import com.daml.ledger.javaapi.data.Text;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValueJsonTest {

    @Test
    void decimalsBecomeExactStringsWithoutTrailingZeros() {
        var record = new DamlRecord(
                new DamlRecord.Field("amount", new Numeric(new BigDecimal("55000000.0000000000"))),
                new DamlRecord.Field("drawn", new Numeric(new BigDecimal("0E-10"))),
                new DamlRecord.Field("share", new Numeric(new BigDecimal("16.5000000000"))));
        assertEquals("{\"amount\":\"55000000\",\"drawn\":\"0\",\"share\":\"16.5\"}", ValueJson.toJson(record));
    }

    @Test
    void recordsKeepTheirFieldNamesOrderAndNesting() {
        var terms = new DamlRecord(
                new DamlRecord.Field("facilityId", new Text("FAC-1")),
                new DamlRecord.Field("currency", new DamlEnum("GBP")),
                new DamlRecord.Field("marginBps", new Int64(250)),
                new DamlRecord.Field("maturity", new Date((int) LocalDate.of(2031, 12, 31).toEpochDay())));
        var facility = new DamlRecord(
                new DamlRecord.Field("borrower", new Party("Borrower::1220ab")),
                new DamlRecord.Field("terms", terms));
        assertEquals("{\"borrower\":\"Borrower::1220ab\",\"terms\":{\"facilityId\":\"FAC-1\",\"currency\":\"GBP\","
                        + "\"marginBps\":250,\"maturity\":\"2031-12-31\"}}",
                ValueJson.toJson(facility));
    }

    @Test
    void unlabelledFieldsAreNamedByPosition() {
        var record = new DamlRecord(new DamlRecord.Field(new Text("first")), new DamlRecord.Field(new Text("second")));
        assertEquals("{\"_0\":\"first\",\"_1\":\"second\"}", ValueJson.toJson(record));
    }
}
