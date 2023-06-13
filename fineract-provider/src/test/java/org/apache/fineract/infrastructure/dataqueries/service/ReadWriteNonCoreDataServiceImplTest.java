/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.dataqueries.service;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.stream.Stream;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.infrastructure.dataqueries.data.ResultsetColumnHeaderData;
import org.apache.fineract.infrastructure.security.utils.SQLInjectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;

public class ReadWriteNonCoreDataServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private GenericDataService genericDataService;

    @Mock
    private DatabaseTypeResolver databaseTypeResolver;

    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;

    @InjectMocks
    private ReadWriteNonCoreDataServiceImpl underTest;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSqlInjectionCaughtQueryDataTable() {
        assertThrows(SQLInjectionException.class, () -> {
            underTest.queryDataTable("table", "cf1", "vf1", "' or 1=1");
        });
    }

    @Test
    public void testSqlInjectionCaughtQueryDataTable2() {
        assertThrows(SQLInjectionException.class, () -> {
            underTest.queryDataTable("table", "cf1", "vf1", "1; DROP TABLE m_loan; SELECT");
        });
    }

    @Test
    public void testQueryDataTableSuccess() {
        SqlRowSet sqlRS = Mockito.mock(SqlRowSet.class);
        when(jdbcTemplate.queryForRowSet(eq("select rc1,rc2 from table where cf1 = ?"), any(Object.class))).thenReturn(sqlRS);
        when(sqlRS.next()).thenReturn(true).thenReturn(false);
        when(sqlRS.getObject(anyString())).thenReturn("value1").thenReturn("value2");
        when(sqlGenerator.escape(anyString())).thenReturn("rc1").thenReturn("rc2").thenReturn("table").thenReturn("cf1");
        when(databaseTypeResolver.isPostgreSQL()).thenReturn(true);

        ResultsetColumnHeaderData cf1 = ResultsetColumnHeaderData.detailed("cf1", "text", 10L, false, false, emptyList(), null, false,
                false);
        ResultsetColumnHeaderData rc1 = ResultsetColumnHeaderData.detailed("rc1", "text", 10L, false, false, emptyList(), null, false,
                false);
        ResultsetColumnHeaderData rc2 = ResultsetColumnHeaderData.detailed("rc2", "text", 10L, false, false, emptyList(), null, false,
                false);
        when(genericDataService.fillResultsetColumnHeaders("table")).thenReturn(List.of(cf1, rc1, rc2));

        List<JsonObject> results = underTest.queryDataTable("table", "cf1", "vf1", "rc1,rc2");

        assertEquals("value1", results.get(0).get("rc1").getAsString());
        assertEquals("value2", results.get(0).get("rc2").getAsString());
    }

    @Test
    public void testQueryDataTableValidationError() {
        when(genericDataService.fillResultsetColumnHeaders("table")).thenReturn(emptyList());
        assertThrows(PlatformApiDataValidationException.class, () -> underTest.queryDataTable("table", "cf1", "vf1", "rc1,rc2"));
    }

    @ParameterizedTest
    @MethodSource("provideParameters")
    public void testQueryDataTableInvalidParameterError(String columnType, String errorMessage) {
        ResultsetColumnHeaderData cf1 = ResultsetColumnHeaderData.detailed("cf1", columnType, 10L, false, false, emptyList(), null, false,
                false);
        ResultsetColumnHeaderData rc1 = ResultsetColumnHeaderData.detailed("rc1", "text", 10L, false, false, emptyList(), null, false,
                false);
        ResultsetColumnHeaderData rc2 = ResultsetColumnHeaderData.detailed("rc2", "text", 10L, false, false, emptyList(), null, false,
                false);
        when(genericDataService.fillResultsetColumnHeaders("table")).thenReturn(List.of(cf1, rc1, rc2));

        PlatformApiDataValidationException thrown = assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.queryDataTable("table", "cf1", "vf1", "rc1,rc2"));

        assertEquals(1, thrown.getErrors().size());
        assertEquals(errorMessage, thrown.getErrors().get(0).getUserMessageGlobalisationCode());
    }

    private static Stream<Arguments> provideParameters() {
        return Stream.of(Arguments.of("timestamp without time zone", "validation.msg.invalid.dateFormat.format"),
                Arguments.of("INTEGER", "validation.msg.invalid.integer.format"),
                Arguments.of("BOOLEAN", "validation.msg.invalid.boolean.format"));
    }
}
