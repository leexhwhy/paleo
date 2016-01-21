/*
 * Copyright 2016 Rahel Lüthy
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

package ch.netzwerg.paleo;

import com.google.common.collect.ImmutableMap;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.IntStream;

import static ch.netzwerg.paleo.ColumnIds.IntColumnId;

public final class IntColumn implements Column<IntColumnId> {

    private final IntColumnId id;
    private final int[] values;
    private final Map<String, String> metaData;

    public IntColumn(IntColumnId id, IntStream values) {
        this(id, values, Collections.emptyMap());
    }

    public IntColumn(IntColumnId id, IntStream values, Map<String, String> metaData) {
        this.id = id;
        this.values = values.toArray();
        this.metaData = ImmutableMap.copyOf(metaData);
    }

    public static Builder builder(IntColumnId id) {
        return new Builder(id);
    }

    @Override
    public IntColumnId getId() {
        return this.id;
    }

    @Override
    public int getRowCount() {
        return this.values.length;
    }

    @Override
    public Map<String, String> getMetaData() {
        return this.metaData;
    }

    public int getValueAt(int index) {
        return this.values[index];
    }

    public IntStream getValues() {
        return Arrays.stream(this.values);
    }

    public static final class Builder implements Column.Builder<IntColumn> {

        private final IntColumnId id;
        private final IntStream.Builder valueBuilder;
        private final ImmutableMap.Builder<String, String> metaDataBuilder;

        public Builder(IntColumnId id) {
            this.id = id;
            this.valueBuilder = IntStream.builder();
            this.metaDataBuilder = ImmutableMap.builder();
        }

        public Builder addAll(int... values) {
            for (int value : values) {
                add(value);
            }
            return this;
        }

        public Builder add(int value) {
            this.valueBuilder.add(value);
            return this;
        }

        @Override
        public Builder putMetaData(String key, String value) {
            metaDataBuilder.put(key, value);
            return this;
        }

        @Override
        public Builder putAllMetaData(Map<String, String> metaData) {
            metaDataBuilder.putAll(metaData);
            return this;
        }

        @Override
        public IntColumn build() {
            return new IntColumn(id, valueBuilder.build(), metaDataBuilder.build());
        }

    }

}