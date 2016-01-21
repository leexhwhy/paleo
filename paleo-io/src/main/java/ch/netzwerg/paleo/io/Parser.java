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
import ch.netzwerg.paleo.schema.Field;
import ch.netzwerg.paleo.schema.Schema;
import com.google.common.collect.ImmutableList;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

public final class Parser {

    public static DataFrame parseTabDelimited(Reader in) throws IOException {
        return parseTabDelimited(in, Optional.empty(), Collections.emptyMap());
    }

    public static DataFrame parseTabDelimited(Reader in, String timestampPattern) throws IOException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(timestampPattern);
        return parseTabDelimited(in, Optional.of(formatter), Collections.emptyMap());
    }

    public static DataFrame parseTabDelimited(Reader in, Optional<DateTimeFormatter> formatter, Map<String, ColumnBuilderFactory> columnBuilderFactories) throws IOException {
        Iterator<CSVRecord> it = CSVFormat.TDF.parse(in).iterator();

        CSVRecord columnNames = it.next();
        CSVRecord columnTypes = it.next();

        List<FromStringColumnBuilder<?>> columnBuilders = createColumnBuilders(columnNames, columnTypes, formatter, columnBuilderFactories);

        return parseDataFrame(it, columnBuilders, 2);
    }

    public static DataFrame parseTabDelimited(Schema schema, File parentDir) throws IOException {
        try (FileReader fileReader = new FileReader(new File(parentDir, schema.getDataFileName()))) {
            return parseTabDelimited(schema, fileReader);
        }
    }

    public static DataFrame parseTabDelimited(Schema schema) throws IOException {
        try (InputStream inputStream = Parser.class.getResourceAsStream(schema.getDataFileName());
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
            return parseTabDelimited(schema, inputStreamReader);
        }
    }

    private static DataFrame parseTabDelimited(Schema schema, Reader reader) throws IOException {
        Iterator<CSVRecord> it = CSVFormat.TDF.parse(reader).iterator();
        List<FromStringColumnBuilder<?>> columnBuilders = createColumnBuilders(schema.getFields());
        return parseDataFrame(it, columnBuilders, 0);
    }

    private static DataFrame parseDataFrame(Iterator<CSVRecord> it, List<FromStringColumnBuilder<?>> columnBuilders, int rowOffset) {
        int rowCount = 0;
        while (it.hasNext()) {
            rowCount++;
            CSVRecord row = it.next();

            if (row.size() != columnBuilders.size()) {
                String msgFormat = "Row '%s' contains '%s' values (but should match column count '%s')";
                String msg = String.format(msgFormat, rowCount + rowOffset, row.size(), columnBuilders.size());
                throw new IllegalArgumentException(msg);
            }

            Iterator<String> valueIt = row.iterator();
            Iterator<FromStringColumnBuilder<?>> columnBuildersIt = columnBuilders.iterator();
            while (valueIt.hasNext()) {
                columnBuildersIt.next().add(valueIt.next());
            }
        }

        ImmutableList.Builder<Column<?>> columns = ImmutableList.builder();
        columnBuilders.forEach(columnBuilder -> columns.add(columnBuilder.build()));
        return new DataFrame(columns.build());
    }

    private static List<FromStringColumnBuilder<?>> createColumnBuilders(CSVRecord columnNames, CSVRecord columnTypes, Optional<DateTimeFormatter> formatter, Map<String, ColumnBuilderFactory> columnBuilderFactories) {

        if (columnNames.size() != columnTypes.size()) {
            String msg = String.format(
                    "Number of column names (%s) must match number of column types (%s)",
                    columnNames.size(),
                    columnTypes.size());
            throw new IllegalArgumentException(msg);
        }

        ImmutableList.Builder<FromStringColumnBuilder<?>> resultBuilder = ImmutableList.builder();

        Iterator<String> nameIt = columnNames.iterator();
        Iterator<String> typeIt = columnTypes.iterator();
        while (nameIt.hasNext()) {
            String name = nameIt.next();
            String type = typeIt.next();

            resultBuilder.add(createColumnBuilder(name, type, formatter, columnBuilderFactories));
        }
        return resultBuilder.build();
    }

    private static List<FromStringColumnBuilder<?>> createColumnBuilders(List<Field> fields) {
        ImmutableList.Builder<FromStringColumnBuilder<?>> result = ImmutableList.builder();
        for (Field field : fields) {
            ColumnType<?> type = field.getType();
            Optional<DateTimeFormatter> formatter = field.getFormat().map(DateTimeFormatter::ofPattern);
            FromStringColumnBuilder<?> columnBuilder = createColumnBuilder(field.getName(), formatter, type);
            columnBuilder.putAllMetaData(field.getMetaData());
            result.add(columnBuilder);
        }
        return result.build();
    }

    private static FromStringColumnBuilder<?> createColumnBuilder(String name, String typeDesc, Optional<DateTimeFormatter> formatter, Map<String, ColumnBuilderFactory> columnBuilderFactories) {
        if (columnBuilderFactories.containsKey(typeDesc)) {
            return columnBuilderFactories.get(typeDesc).create(name, typeDesc);
        } else {
            ColumnType<?> type = ColumnType.getByDescriptionOrDefault(typeDesc, ColumnType.STRING);
            return createColumnBuilder(name, formatter, type);
        }
    }

    private static FromStringColumnBuilder<?> createColumnBuilder(String name, Optional<DateTimeFormatter> formatter, ColumnType<?> type) {
        if (ColumnType.INT.equals(type)) {
            return intColumnBuilder(name);
        } else if (ColumnType.DOUBLE.equals(type)) {
            return doubleColumnBuilder(name);
        } else if (ColumnType.BOOLEAN.equals(type)) {
            return booleanColumnBuilder(name);
        } else if (ColumnType.TIMESTAMP.equals(type)) {
            return timestampColumnBuilder(name, formatter);
        } else if (ColumnType.CATEGORY.equals(type)) {
            return categoryColumnBuilder(name);
        } else {
            return stringColumnBuilder(name);
        }
    }

    public interface FromStringColumnBuilder<C extends Column<?>> extends Column.Builder<C> {
        FromStringColumnBuilder<C> add(String stringValue);
    }

    private static class GenericFromStringColumnBuilder<C extends Column<?>> implements FromStringColumnBuilder<C> {

        private final Column.Builder<C> delegateBuilder;
        private final Consumer<String> valueAccepter;

        private GenericFromStringColumnBuilder(Column.Builder<C> delegateBuilder, Consumer<String> valueAccepter) {
            this.delegateBuilder = delegateBuilder;
            this.valueAccepter = valueAccepter;
        }

        @Override
        public FromStringColumnBuilder<C> add(String stringValue) {
            this.valueAccepter.accept(stringValue);
            return this;
        }

        @Override
        public FromStringColumnBuilder<C> putMetaData(String key, String value) {
            delegateBuilder.putMetaData(key, value);
            return this;
        }

        @Override
        public FromStringColumnBuilder<C> putAllMetaData(Map<String, String> metaData) {
            delegateBuilder.putAllMetaData(metaData);
            return this;
        }

        public C build() {
            return this.delegateBuilder.build();
        }

    }

    private static FromStringColumnBuilder<IntColumn> intColumnBuilder(String name) {
        IntColumn.Builder builder = IntColumn.builder(ColumnIds.intCol(name));
        Consumer<String> valueAccepter = stringValue -> builder.add(Integer.parseInt(stringValue));
        return new GenericFromStringColumnBuilder<>(builder, valueAccepter);
    }

    private static FromStringColumnBuilder<DoubleColumn> doubleColumnBuilder(String name) {
        DoubleColumn.Builder builder = DoubleColumn.builder(ColumnIds.doubleCol(name));
        Consumer<String> valueAccepter = stringValue -> builder.add(Double.parseDouble(stringValue));
        return new GenericFromStringColumnBuilder<>(builder, valueAccepter);
    }

    private static FromStringColumnBuilder<BooleanColumn> booleanColumnBuilder(String name) {
        BooleanColumn.Builder builder = BooleanColumn.builder(ColumnIds.booleanCol(name));
        Consumer<String> valueAccepter = stringValue -> builder.add(Boolean.parseBoolean(stringValue));
        return new GenericFromStringColumnBuilder<>(builder, valueAccepter);
    }

    private static FromStringColumnBuilder<StringColumn> stringColumnBuilder(String name) {
        StringColumn.Builder builder = StringColumn.builder(ColumnIds.stringCol(name));
        Consumer<String> valueAccepter = builder::add;
        return new GenericFromStringColumnBuilder<>(builder, valueAccepter);
    }

    private static FromStringColumnBuilder<CategoryColumn> categoryColumnBuilder(String name) {
        CategoryColumn.Builder builder = CategoryColumn.builder(ColumnIds.categoryCol(name));
        Consumer<String> valueAccepter = builder::add;
        return new GenericFromStringColumnBuilder<>(builder, valueAccepter);
    }

    private static FromStringColumnBuilder<TimestampColumn> timestampColumnBuilder(String name, Optional<DateTimeFormatter> formatter) {
        TimestampColumn.Builder builder = TimestampColumn.builder(ColumnIds.timestampCol(name));
        Consumer<String> valueAccepter = stringValue -> {
            Instant instant;
            if (formatter.isPresent()) {
                LocalDateTime dateTime = LocalDateTime.from(formatter.get().parse(stringValue));
                instant = dateTime.atZone(ZoneId.systemDefault()).toInstant();
            } else {
                instant = Instant.parse(stringValue);
            }
            builder.add(instant);
        };
        return new GenericFromStringColumnBuilder<>(builder, valueAccepter);
    }

    public interface ColumnBuilderFactory {
        FromStringColumnBuilder<?> create(String name, String typeDescription);
    }

}