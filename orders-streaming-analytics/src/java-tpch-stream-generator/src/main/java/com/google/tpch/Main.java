/*
 * Copyright 2025 Google LLC
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
package com.google.tpch;

import io.trino.tpch.Order;
import io.trino.tpch.TpchTable;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

@Command(
    name = "app",
    mixinStandardHelpOptions = true,
    description = "TPC-H Orders Kafka Producer"
)
public final class Main implements Callable<Integer> {

    private static final LocalDate TPCH_MIN_DATE = LocalDate.of(1992, 1, 1);
    private static final LocalDate TPCH_MAX_DATE = LocalDate.of(1998, 8, 2);
    private static final long TPCH_MIN_EPOCH = TPCH_MIN_DATE.toEpochDay();
    private static final long TPCH_SPAN_DAYS = TPCH_MAX_DATE.toEpochDay() - TPCH_MIN_EPOCH;

    @Option(
        names = "--local",
        description = "Use local Kafka without authentication"
    )
    private boolean localMode;

    @Option(
        names = "--max-messages",
        required = true,
        description = "Maximum number of messages to publish"
    )
    private long maxMessages;

    @Option(
        names = "--target-throughput",
        required = true,
        description = "Target throughput in messages per second. Use 0 for unlimited."
    )
    private int targetThroughput;

    @Option(
        names = "--bootstrap-servers",
        required = true,
        description = "Kafka bootstrap servers"
    )
    private String bootstrapServers;

    @Option(
        names = "--topic",
        required = true,
        description = "Kafka topic"
    )
    private String topic;

    @Option(
        names = "--date-from",
        description = "Start date range (e.g., '1995-01-01', '1 month ago', '7 days ago')"
    )
    private String dateFromRaw;

    @Option(
        names = "--date-to",
        description = "End date range (e.g., '1995-12-31', 'today'). Defaults to today if date-from is specified."
    )
    private String dateToRaw;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        validateArguments();

        LocalDate dateFrom = parseDateOption(dateFromRaw, "--date-from");
        LocalDate dateTo = parseDateOption(dateToRaw, "--date-to");

        // Fill defaults if only one bound is provided
        if (dateFrom != null && dateTo == null) {
            dateTo = LocalDate.now();
        } else if (dateFrom == null && dateTo != null) {
            dateFrom = TPCH_MIN_DATE;
        }

        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ParameterException(
                new CommandLine(this),
                "--date-from (" + dateFrom + ") cannot be after --date-to (" + dateTo + ")"
            );
        }

        // Auto-detect rebasing: enable if either date falls outside standard TPC-H 1992-1998 bounds
        boolean rebaseDates = dateFrom != null && dateTo != null && (
            dateFrom.isBefore(TPCH_MIN_DATE) || dateFrom.isAfter(TPCH_MAX_DATE) ||
                dateTo.isBefore(TPCH_MIN_DATE) || dateTo.isAfter(TPCH_MAX_DATE)
        );

        run(new Configuration(
            localMode,
            maxMessages,
            targetThroughput,
            bootstrapServers,
            topic,
            dateFrom,
            dateTo,
            rebaseDates
        ));

        return 0;
    }

    private LocalDate parseDateOption(String raw, String optionName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return parseDateExpression(raw);
        } catch (IllegalArgumentException e) {
            throw new ParameterException(
                new CommandLine(this),
                "Invalid value for " + optionName + ": " + e.getMessage(),
                e
            );
        }
    }

    private static LocalDate parseDateExpression(String input) {
        String trimmed = input.trim().toLowerCase();

        if ("today".equals(trimmed) || "now".equals(trimmed)) {
            return LocalDate.now();
        }
        if ("yesterday".equals(trimmed)) {
            return LocalDate.now().minusDays(1);
        }

        Pattern pattern = Pattern.compile("^(\\d+)\\s+(day|week|month|year)s?\\s+ago$");
        Matcher matcher = pattern.matcher(trimmed);

        if (matcher.matches()) {
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            return switch (unit) {
                case "day" -> LocalDate.now().minusDays(amount);
                case "week" -> LocalDate.now().minusWeeks(amount);
                case "month" -> LocalDate.now().minusMonths(amount);
                case "year" -> LocalDate.now().minusYears(amount);
                default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
            };
        }

        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "Could not parse date '" + input + "'. Expected format YYYY-MM-DD or relative like '1 month ago'."
            );
        }
    }

    private void validateArguments() {
        CommandLine commandLine = new CommandLine(this);

        if (maxMessages <= 0) {
            throw new ParameterException(
                commandLine,
                "--max-messages must be greater than zero."
            );
        }

        if (targetThroughput < 0) {
            throw new ParameterException(
                commandLine,
                "--target-throughput cannot be negative."
            );
        }

        if (bootstrapServers.isBlank()) {
            throw new ParameterException(
                commandLine,
                "--bootstrap-servers cannot be blank."
            );
        }

        if (topic.isBlank()) {
            throw new ParameterException(
                commandLine,
                "--topic cannot be blank."
            );
        }
    }

    private static void run(Configuration configuration) {
        Properties properties = createProducerProperties(configuration);

        System.out.printf(
            "Configuration:%n" +
                "  Kafka mode:       %s%n" +
                "  Bootstrap server: %s%n" +
                "  Topic:            %s%n" +
                "  Max messages:     %d%n" +
                "  Throughput:       %s%n" +
                "  Date Range:       %s to %s%n" +
                "  Date Strategy:    %s%n",
            configuration.localMode()
                ? "local, unauthenticated"
                : "GCP Managed Kafka",
            configuration.bootstrapServers(),
            configuration.topic(),
            configuration.maxMessages(),
            configuration.targetThroughput() == 0
                ? "unlimited"
                : configuration.targetThroughput() + " msg/sec",
            configuration.dateFrom() != null ? configuration.dateFrom() : "unconstrained",
            configuration.dateTo() != null ? configuration.dateTo() : "unconstrained",
            configuration.rebaseDates() ? "Rebased automatically" : "Filtered natively"
        );

        AtomicReference<Exception> sendFailure = new AtomicReference<>();

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {

            double scaleFactor = 0.1;

            Iterator<Order> orderIterator =
                TpchTable.ORDERS
                    .createGenerator(scaleFactor, 1, 1)
                    .iterator();

            long messagesSent = 0;
            long startTimeNs = System.nanoTime();

            while (messagesSent < configuration.maxMessages() && orderIterator.hasNext()) {

                Exception previousFailure = sendFailure.get();
                if (previousFailure != null) {
                    throw new IllegalStateException(
                        "A Kafka send operation failed.",
                        previousFailure
                    );
                }

                Order order = orderIterator.next();
                LocalDate originalOrderDate = LocalDate.ofEpochDay(order.orderDate());
                String recordPayload = order.toLine();

                if (configuration.dateFrom() != null && configuration.dateTo() != null) {
                    if (configuration.rebaseDates()) {
                    // Project TPC-H epoch progress linearly into [dateFrom, dateTo]
                    long targetSpanDays = configuration.dateTo().toEpochDay() - configuration.dateFrom().toEpochDay();
                    double progress = (double) (order.orderDate() - TPCH_MIN_EPOCH) / TPCH_SPAN_DAYS;
                    long rebasedEpochDay = configuration.dateFrom().toEpochDay() + (long) (progress * targetSpanDays);

                    LocalDate rebasedDate = LocalDate.ofEpochDay(rebasedEpochDay);
                    recordPayload = replaceOrderDateInPayload(recordPayload, rebasedDate);
                    } else {
                        // Native filtering for dates within 1992-1998
                        if (originalOrderDate.isBefore(configuration.dateFrom()) ||
                            originalOrderDate.isAfter(configuration.dateTo())) {
                            continue;
                        }
                    }
                }

                // Bit-reverse the sequential orderKey to prevent write hotspots in distributed targets
                long distributedOrderKey = bitReverse(order.orderKey());

                // Replace O_ORDERKEY (field 0) in the pipe-delimited payload
                recordPayload = replaceOrderKeyInPayload(recordPayload, distributedOrderKey);


                ProducerRecord<String, String> record =
                    new ProducerRecord<>(
                        configuration.topic(),
                        String.valueOf(distributedOrderKey),
                        recordPayload
                    );

                producer.send(
                    record,
                    (metadata, exception) -> {
                        if (exception != null) {
                            sendFailure.compareAndSet(
                                null,
                                exception
                            );
                        }
                    });

                messagesSent++;

                throttle(
                    messagesSent,
                    configuration.targetThroughput(),
                    startTimeNs
                );
            }

            producer.flush();

            Exception finalFailure = sendFailure.get();
            if (finalFailure != null) {
                throw new IllegalStateException(
                    "At least one Kafka record could not be published.",
                    finalFailure
                );
            }

            long elapsedNs = System.nanoTime() - startTimeNs;
            double elapsedSeconds = elapsedNs / 1_000_000_000.0;
            double actualThroughput = elapsedSeconds == 0.0
                ? messagesSent
                : messagesSent / elapsedSeconds;

            System.out.printf(
                "Completed successfully. Published %d records in %.2f seconds (%.2f msg/sec).%n",
                messagesSent,
                elapsedSeconds,
                actualThroughput
            );
        }
    }

    /**
     * Bit-reverses a 64-bit integer while preserving it as a strictly positive number (> 0).
     * This mimics Cloud Spanner's bit_reversed_positive sequence behavior.
     */
    private static long bitReverse(long value) {
        return Long.reverse(value) >>> 1;
    }

    private static String replaceOrderKeyInPayload(String rawPayload, long newOrderKey) {
        String[] fields = rawPayload.split("\\|", -1);
        if (fields.length > 0) {
            fields[0] = Long.toString(newOrderKey); // Field index 0 is O_ORDERKEY
        }
        return String.join("|", fields);
    }

    private static String replaceOrderDateInPayload(String rawPayload, LocalDate newDate) {
        String[] fields = rawPayload.split("\\|", -1);
        if (fields.length > 4) {
            fields[4] = newDate.toString(); // Field index 4 is O_ORDERDATE
        }
        return String.join("|", fields);
    }

    private static Properties createProducerProperties(Configuration configuration) {
        Properties properties = new Properties();

        properties.put(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            configuration.bootstrapServers()
        );

        properties.put(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class.getName()
        );

        properties.put(
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class.getName()
        );

        properties.put(
            ProducerConfig.LINGER_MS_CONFIG,
            "50"
        );

        properties.put(
            ProducerConfig.ACKS_CONFIG,
            "all"
        );

        properties.put(
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
            "true"
        );

        properties.put(
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
            "120000"
        );

        properties.put(
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
            "30000"
        );

        if (!configuration.localMode()) {
            configureGoogleManagedKafkaAuthentication(properties);
        }

        return properties;
    }

    private static void configureGoogleManagedKafkaAuthentication(Properties properties) {
        properties.put("security.protocol", "SASL_SSL");
        properties.put("sasl.mechanism", "OAUTHBEARER");
        properties.put(
            "sasl.login.callback.handler.class",
            "com.google.cloud.hosted.kafka.auth.GcpLoginCallbackHandler"
        );
        properties.put(
            "sasl.jaas.config",
            "org.apache.kafka.common.security.oauthbearer." +
                "OAuthBearerLoginModule required;"
        );
    }

    private static void throttle(
        long messagesSent,
        int targetThroughput,
        long startTimeNs) {
        if (targetThroughput == 0) {
            return;
        }

        long expectedElapsedNs = (messagesSent * 1_000_000L) / targetThroughput * 1_000L;
        long actualElapsedNs = System.nanoTime() - startTimeNs;
        long delayNs = expectedElapsedNs - actualElapsedNs;

        if (delayNs <= 0) {
            return;
        }

        long delayMillis = delayNs / 1_000_000L;
        int additionalNanos = (int) (delayNs % 1_000_000L);

        try {
            Thread.sleep(delayMillis, additionalNanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                "Producer was interrupted while throttling.",
                exception
            );
        }
    }

    private record Configuration(
        boolean localMode,
        long maxMessages,
        int targetThroughput,
        String bootstrapServers,
        String topic,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean rebaseDates) {
    }
}
