/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.streams.errors.TopologyException;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.internals.KStreamImpl;
import org.apache.kafka.test.KStreamTestDriver;
import org.apache.kafka.test.MockProcessorSupplier;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StreamsBuilderTest {

    private final StreamsBuilder builder = new StreamsBuilder();

    private KStreamTestDriver driver = null;

    @After
    public void cleanup() {
        if (driver != null) {
            driver.close();
        }
        driver = null;
    }

    @Test(expected = TopologyException.class)
    public void testFrom() {
        builder.stream("topic-1", "topic-2");

        builder.build().addSource(KStreamImpl.SOURCE_NAME + "0000000000", "topic-3");
    }

    @Test
    public void shouldProcessingFromSinkTopic() {
        final KStream<String, String> source = builder.stream("topic-source");
        source.to("topic-sink");

        final MockProcessorSupplier<String, String> processorSupplier = new MockProcessorSupplier<>();

        source.process(processorSupplier);

        driver = new KStreamTestDriver(builder);
        driver.setTime(0L);

        driver.process("topic-source", "A", "aa");

        // no exception was thrown
        assertEquals(Utils.mkList("A:aa"), processorSupplier.processed);
    }

    @Test
    public void shouldProcessViaThroughTopic() {
        final KStream<String, String> source = builder.stream("topic-source");
        final KStream<String, String> through = source.through("topic-sink");

        final MockProcessorSupplier<String, String> sourceProcessorSupplier = new MockProcessorSupplier<>();
        final MockProcessorSupplier<String, String> throughProcessorSupplier = new MockProcessorSupplier<>();

        source.process(sourceProcessorSupplier);
        through.process(throughProcessorSupplier);

        driver = new KStreamTestDriver(builder);
        driver.setTime(0L);

        driver.process("topic-source", "A", "aa");

        assertEquals(Utils.mkList("A:aa"), sourceProcessorSupplier.processed);
        assertEquals(Utils.mkList("A:aa"), throughProcessorSupplier.processed);
    }

    @Test
    public void testMerge() {
        final String topic1 = "topic-1";
        final String topic2 = "topic-2";

        final KStream<String, String> source1 = builder.stream(topic1);
        final KStream<String, String> source2 = builder.stream(topic2);
        final KStream<String, String> merged = builder.merge(source1, source2);

        final MockProcessorSupplier<String, String> processorSupplier = new MockProcessorSupplier<>();
        merged.process(processorSupplier);

        driver = new KStreamTestDriver(builder);
        driver.setTime(0L);

        driver.process(topic1, "A", "aa");
        driver.process(topic2, "B", "bb");
        driver.process(topic2, "C", "cc");
        driver.process(topic1, "D", "dd");

        assertEquals(Utils.mkList("A:aa", "B:bb", "C:cc", "D:dd"), processorSupplier.processed);
    }

    @Test(expected = TopologyException.class)
    public void shouldThrowExceptionWhenNoTopicPresent() throws Exception {
        builder.stream();
    }

    @Test(expected = NullPointerException.class)
    public void shouldThrowExceptionWhenTopicNamesAreNull() throws Exception {
        builder.stream(Serdes.String(), Serdes.String(), null, null);
    }

}