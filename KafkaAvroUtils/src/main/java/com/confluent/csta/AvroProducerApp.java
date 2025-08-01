package com.confluent.csta;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.Conversions;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.data.TimeConversions;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Future;

public class AvroProducerApp {

    private static final Random random = new Random();

    public static void main(String[] args) throws IOException {
        Properties producerProps;
        if (args.length > 0) {
            // Read from the file path provided as a command-line argument
            String configFilePath = args[0];
            System.out.println("Reading configuration from command-line argument file: " + configFilePath);
            producerProps = loadConfigFromFile(configFilePath);
        } else {
            // Read from the default resource file
            String defaultConfigFile = "producer.properties";
            System.out.println("No configuration file provided. Reading from default resource file: " + defaultConfigFile);
            producerProps = loadConfigFromResource(defaultConfigFile);
        }

        String topic = producerProps.getProperty("topic.name");
        String schemaFilePath = producerProps.getProperty("schema.file.path");
        int numRecords = Integer.parseInt(producerProps.getProperty("num.records"));

        // Reads the schema from a file
        File schemaFile = new File(schemaFilePath);
        if (!schemaFile.exists()) {
            throw new IOException("Schema file not found at: " + schemaFile.getAbsolutePath());
        }
        Schema schema = new Schema.Parser().parse(schemaFile);

        GenericData model = new GenericData();
        model.addLogicalTypeConversion(new Conversions.DecimalConversion());
        model.addLogicalTypeConversion(new TimeConversions.DateConversion());
        model.addLogicalTypeConversion(new TimeConversions.TimestampMicrosConversion());
        model.addLogicalTypeConversion(new TimeConversions.TimeMicrosConversion());

        // Set serializers
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());

        try (KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(producerProps)) {
            System.out.println("Starting to send " + numRecords + " Avro records to topic '" + topic + "'...");

            for (int i = 0; i < numRecords; i++) {
                GenericRecord record = generateRandomRecord(schema, model);
                ProducerRecord<String, GenericRecord> producerRecord = new ProducerRecord<>(topic, "key-" + i, record);
                Future<RecordMetadata> future = producer.send(producerRecord);

                if (i % 100 == 0) {
                    System.out.println("Sent record " + i);
                }
            }

            producer.flush();
            System.out.println("Finished sending records. Flushed producer.");

        } catch (Exception e) {
            System.err.println("Error while producing records: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Properties loadConfigFromResource(String configFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream props = AvroProducerApp.class.getClassLoader().getResourceAsStream(configFile)) {
            if (props == null) {
                throw new IOException("Default resource file not found: " + configFile);
            }
            properties.load(props);
        }
        return properties;
    }

    private static Properties loadConfigFromFile(String configFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream props = new FileInputStream(configFile)) {
            properties.load(props);
        }
        return properties;
    }

    private static GenericRecord generateRandomRecord(Schema schema, GenericData model) {
        GenericRecord record = new GenericData.Record(schema);
        for (Schema.Field field : schema.getFields()) {
            record.put(field.name(), generateRandomValue(field.schema(), model));
        }
        return record;
    }

    private static Object generateRandomValue(Schema schema, GenericData model) {
        if (schema.getLogicalType() != null) {
            if (schema.getLogicalType() instanceof LogicalTypes.Decimal) {
                LogicalTypes.Decimal decimalType = (LogicalTypes.Decimal) schema.getLogicalType();
                int precision = decimalType.getPrecision();
                int scale = decimalType.getScale();

                BigDecimal bigDecimal = new BigDecimal(random.nextDouble() * Math.pow(10, precision - scale))
                        .setScale(scale, RoundingMode.HALF_UP);

                return model.getConversionFor(decimalType).toBytes(bigDecimal, schema, decimalType);
            }
            if (schema.getLogicalType() instanceof LogicalTypes.TimestampMicros) {
                return Instant.now().plusSeconds(random.nextInt(100000)).toEpochMilli() * 1000L;
            }
            if (schema.getLogicalType() instanceof LogicalTypes.TimeMicros) {
                return random.nextLong() % (24 * 60 * 60 * 1000000L);
            }
            if (schema.getLogicalType() instanceof LogicalTypes.Date) {
                return (int) (System.currentTimeMillis() / (24 * 60 * 60 * 1000L)) + random.nextInt(365);
            }
        }
        switch (schema.getType()) {
            case RECORD:
                return generateRandomRecord(schema, model);
            case UNION:
                if (schema.getTypes().size() == 2 && schema.getTypes().get(0).getType() == Schema.Type.NULL) {
                    return random.nextDouble() > 0.1 ? generateRandomValue(schema.getTypes().get(1), model) : null;
                }
                for (Schema subSchema : schema.getTypes()) {
                    if (subSchema.getType() != Schema.Type.NULL) {
                        return generateRandomValue(subSchema, model);
                    }
                }
                return null;
            case STRING:
                return "random_string_" + random.nextInt(10000);
            case INT:
                return random.nextInt(1000);
            case LONG:
                return random.nextLong();
            case DOUBLE:
                return random.nextDouble() * 1000.0;
            case BYTES:
                byte[] bytes = new byte[10];
                random.nextBytes(bytes);
                return ByteBuffer.wrap(bytes);
            default:
                return null;
        }
    }
}