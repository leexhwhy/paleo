/*
 * Copyright 2015 Rahel Lüthy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.netzwerg.paleo.io;

import ch.netzwerg.paleo.*;
import ch.netzwerg.paleo.io.Parser;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.time.*;
import java.util.Arrays;
import java.util.function.Function;

import static ch.netzwerg.paleo.ColumnIds.*;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.*;

public class ParserTest {

    public static final String CONTENTS = "Name\tAge\tHeight\tVegetarian\tDate Of Birth\tGender\n" +
            "String\tInt\tDouble\tBoolean\tTimestamp\tCategory\n" +
            "Ada\t42\t1.74\ttrue\t19750826050916\tFemale\n" +
            "Homer\t99\t1.20\tF\t20060108050916\tMale\n" +
            "Hillary\t67\t1.70\t1\t19471026050916\tFemale\n";

    @Test
    public void parseTabDelimited() throws IOException {
        StringReader reader = new StringReader(CONTENTS);

        DataFrame df = Parser.parseTabDelimited(reader, "yyyyMMddHHmmss");
        assertEquals(6, df.getColumnCount());
        assertEquals(Arrays.asList("Name", "Age", "Height", "Vegetarian", "Date Of Birth", "Gender" ), df.getColumnNames());

        StringColumnId nameColumnId = df.getColumnId(0, ColumnType.STRING);
        StringColumn nameColumn = df.getColumn(nameColumnId);
        assertEquals(asList("Ada", "Homer", "Hillary" ), nameColumn.getValues().collect(toList()));

        IntColumnId ageColumnId = df.getColumnId(1, ColumnType.INT);
        IntColumn ageColumn = df.getColumn(ageColumnId);
        assertArrayEquals(new int[]{42, 99, 67}, ageColumn.getValues().toArray());

        DoubleColumnId heightColumnId = df.getColumnId(2, ColumnType.DOUBLE);
        DoubleColumn heightColumn = df.getColumn(heightColumnId);
        assertArrayEquals(new double[]{1.74, 1.20, 1.70}, heightColumn.getValues().toArray(), 0.01);

        BooleanColumnId vegetarianColumnId = df.getColumnId(3, ColumnType.BOOLEAN);
        BooleanColumn vegetarianColumn = df.getColumn(vegetarianColumnId);
        assertArrayEquals(new Boolean[]{true, false, false}, vegetarianColumn.getValues().toArray());

        TimestampColumnId dateOfBirthColumnId = df.getColumnId(4, ColumnType.TIMESTAMP);
        TimestampColumn dateOfBirthColumn = df.getColumn(dateOfBirthColumnId);
        Function<? super Instant, Month> toMonth = instant -> instant.atZone(ZoneId.from(ZoneOffset.UTC)).getMonth();
        assertEquals(asList(Month.AUGUST, Month.JANUARY, Month.OCTOBER), dateOfBirthColumn.getValues().map(toMonth).collect(toList()));

        CategoryColumnId genderColumnId = df.getColumnId(5, ColumnType.CATEGORY);
        CategoryColumn genderColumn = df.getColumn(genderColumnId);
        assertEquals(ImmutableSet.of("Female", "Male"), genderColumn.getCategories());

        // typed random access for String values
        String stringValue = df.getValueAt(0, nameColumnId);
        assertEquals("Ada", stringValue);

        // typed random access for primitive ints
        int intValue = df.getValueAt(1, ageColumnId);
        assertEquals(99, intValue);

        // typed random access for primitive doubles
        double doubleValue = df.getValueAt(0, heightColumnId);
        assertEquals(1.74, doubleValue, 0.01);

        // typed random access for booleans
        boolean booleanValue = df.getValueAt(2, vegetarianColumnId);
        assertFalse(booleanValue);

        // typed random access for categories
        String categoryValue = df.getValueAt(2, genderColumnId);
        assertEquals("Female", categoryValue);
    }

}