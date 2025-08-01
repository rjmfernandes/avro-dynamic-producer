# Avro Dynamic Producer

Just an example of an avro producer leveraging a schema to generate random messages.

- [Avro Dynamic Producer](#avro-dynamic-producer)
  - [Disclaimer](#disclaimer)
  - [Setup](#setup)
    - [Start Docker Compose](#start-docker-compose)
    - [Check Control Center](#check-control-center)
  - [Let's Produce](#lets-produce)
  - [Cleanup](#cleanup)

## Disclaimer

The code and/or instructions here available are **NOT** intended for production usage. 
It's only meant to serve as an example or reference and does not replace the need to follow actual and official documentation of referenced products.

## Setup

### Start Docker Compose

```bash
docker compose up -d
```

### Check Control Center

Open http://localhost:9021 and check cluster is healthy including Kafka Connect.

## Let's Produce

Run Maven install:

```shell
cd KafkaAvroUtils
mvn install
cd ..
```

Create topic test-topic:

```shell
kafka-topics --bootstrap-server localhost:9091 --topic test-topic --create --partitions 2 --replication-factor 1
```

Register the schema:

```shell
curl -X POST -H "Content-Type:application/json" --data "`jq '. | {schema: tojson}' account.avsc`" http://localhost:8081/subjects/test-topic-value/versions
```

If you need change the producer.properties as per your environment and the schema account.avsc as well.

Run our producer (notice that you can run multiple times this in different shells to increase parallel load):

```shell
java -cp KafkaAvroUtils/target/kafka-avro-utils-1.0-SNAPSHOT-jar-with-dependencies.jar com.confluent.csta.AvroProducerApp producer.properties 
```

For reading from the topic we can now run:

```shell
kafka-avro-console-consumer --topic test-topic \
--bootstrap-server 127.0.0.1:9091 \
--property schema.registry.url=http://localhost:8081 \
--property print.key=true \
--property print.value=true \
--value-deserializer io.confluent.kafka.serializers.KafkaAvroDeserializer \
--key-deserializer org.apache.kafka.common.serialization.StringDeserializer \
--from-beginning
```

## Cleanup

```bash
docker compose down -v
```