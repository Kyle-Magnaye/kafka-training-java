package com.training.kafka.Day01Foundation;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Day 1: Basic Topic Operations
 *
 * This class demonstrates basic Kafka topic operations using the AdminClient.
 * It shows how to create, list, describe, and delete topics programmatically.
 */
public class BasicTopicOperations {
    private static final Logger logger = LoggerFactory.getLogger(BasicTopicOperations.class);

    private final AdminClient adminClient;

    public BasicTopicOperations(String bootstrapServers) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.CLIENT_ID_CONFIG, "basic-topic-operations");

        this.adminClient = AdminClient.create(props);
    }

    private void createTopic(String topicName, int numPartitions, short replicationFactor, Map<String, String> configs) {
        try {
            NewTopic newTopic = new NewTopic(topicName, numPartitions, replicationFactor);

            if (configs != null && !configs.isEmpty()) {
                newTopic.configs(configs);
            }

            CreateTopicsResult result = adminClient.createTopics(Collections.singletonList(newTopic));

            result.all().get();
            logger.info("Topic '{}' created successfully.", topicName);

        } catch (ExecutionException e) {
            if (e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException) {
                logger.warn("Topic '{}' already exists.", topicName);
            } else {
                logger.error("Failed to create topic '{}'", topicName, e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Operation interrupted while creating topic '{}'", topicName, e);
        }
    }

    public void createTopic(String topicName, int numPartitions, short replicationFactor) {
        Map<String, String> defaultConfigs = new HashMap<>();
        defaultConfigs.put("retention.ms", "86400000"); 
        defaultConfigs.put("compression.type", "snappy");
        createTopic(topicName, numPartitions, replicationFactor, defaultConfigs);
    }

    /**
     * List all topics in the cluster
     */
    public Set<String> listTopics() {
        try {
            ListTopicsResult result = adminClient.listTopics();
            Set<String> topics = result.names().get();

            logger.info("Found {} topics:", topics.size());
            topics.forEach(topic -> logger.info("  - {}", topic));

            return topics;

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Failed to list topics: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Describe a specific topic
     */
    public void describeTopic(String topicName) {
        try {
            DescribeTopicsResult result = adminClient.describeTopics(Collections.singletonList(topicName));
            TopicDescription description = result.topicNameValues().get(topicName).get();

            logger.info("Topic Description for '{}':", topicName);
            logger.info("  Name: {}", description.name());
            logger.info("  Internal: {}", description.isInternal());
            logger.info("  Partitions: {}", description.partitions().size());

            description.partitions().forEach(partition -> {
                logger.info("    Partition {}: Leader={}, Replicas={}, ISR={}",
                    partition.partition(),
                    partition.leader().id(),
                    partition.replicas().size(),
                    partition.isr().size());
            });

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Failed to describe topic '{}': {}", topicName, e.getMessage());
        }
    }

    /**
     * Close the AdminClient
     */
    public void close() {
        adminClient.close();
        logger.info("AdminClient closed");
    }

    public void analyzePartitionDistribution(String topicName) {
        try {
            DescribeTopicsResult result = adminClient.describeTopics(Collections.singletonList(topicName));
            TopicDescription description = result.topicNameValues().get(topicName).get();
            
            logger.info("=== Partition Analysis for '{}' ===", topicName);
            logger.info("Total partitions: {}", description.partitions().size());
            
            Map<Integer, Integer> brokerPartitionCount = new HashMap<>();
            
            for (TopicPartitionInfo partition : description.partitions()) {
                int leaderId = partition.leader().id();
                brokerPartitionCount.merge(leaderId, 1, Integer::sum);
                
                logger.info("Partition {}: Leader=Broker-{}, Replicas={}", 
                    partition.partition(), 
                    leaderId,
                    partition.replicas().stream()
                        .map(node -> "Broker-" + node.id())
                        .collect(Collectors.joining(", ")));
            }
            
            logger.info("Partition distribution across brokers:");
            brokerPartitionCount.forEach((brokerId, count) -> 
                logger.info("  Broker-{}: {} partitions", brokerId, count));
                
        } catch (Exception e) {
            logger.error("Error analyzing partition distribution", e);
        }
    }

    public void updateTopicConfigs(String topicName, Map<String, String> newConfigs) {
        logger.info("\n--- Attempting to update configuration for topic: '{}' ---", topicName);

        ConfigResource topicResource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);

        List<AlterConfigOp> ops = new ArrayList<>();
        for (Map.Entry<String, String> configEntry : newConfigs.entrySet()) {
            ops.add(
                new AlterConfigOp(
                    new ConfigEntry(configEntry.getKey(), configEntry.getValue()),
                    AlterConfigOp.OpType.SET
                )
            );
        }

        try {
            AlterConfigsResult alterResult = adminClient.incrementalAlterConfigs(Collections.singletonMap(topicResource, ops));
            alterResult.all().get(); // Wait for the update to complete.
            logger.info("Successfully updated configuration for '{}'.", topicName);

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Failed to update configuration for topic '{}'", topicName, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void exercise1() {
        logger.info("==============================================");
        logger.info("======== Running Exercise 1 Solution =========");
        logger.info("==============================================");
        
        createTopic("exercise-1-topic", 4, (short) 1);
        createTopic("exercise-1-compare", 2, (short) 1);
        
        Set<String> topics = listTopics();
        logger.info("Found {} topics in total after creation.", topics.size());
        
        describeTopic("exercise-1-topic");
        describeTopic("exercise-1-compare");
        
        logger.info("Exercise completed successfully.");
    }

    public void exercise2() {
        logger.info("==============================================");
        logger.info("======== Running Exercise 2 Solution =========");
        logger.info("==============================================");
        
        createTopic("partition-demo", 6, (short) 1);
        
        describeTopic("partition-demo");
        analyzePartitionDistribution("partition-demo");
        
        logger.info("Exercise completed successfully.");
    }

    public void exercise3() {
        logger.info("==============================================");
        logger.info("======== Running Exercise 3 Solution =========");
        logger.info("==============================================");

        String topicToUpdate = "high-retention-topic";
        
        Map<String, String> highRetentionConfigs = new HashMap<>();
        highRetentionConfigs.put("retention.ms", "604800000"); 
        highRetentionConfigs.put("segment.ms", "3600000");  
        createTopic(topicToUpdate, 3, (short) 1, highRetentionConfigs);

        Map<String, String> compactedConfigs = new HashMap<>();
        compactedConfigs.put("cleanup.policy", "compact");
        compactedConfigs.put("min.cleanable.dirty.ratio", "0.1");
        createTopic("compacted-topic", 3, (short) 1, compactedConfigs);

        logger.info("\n--- Describing topics after initial creation (BEFORE update) ---");
        describeTopic(topicToUpdate);
        describeTopic("compacted-topic");

        logger.info("\n--- Now updating configuration for '{}' ---", topicToUpdate);
        Map<String, String> updates = new HashMap<>();
        updates.put("retention.ms", "86400000"); 

        updateTopicConfigs(topicToUpdate, updates);

        logger.info("\n--- Describing '{}' after configuration update (AFTER update) ---");
        describeTopic(topicToUpdate);
        
        logger.info("\nExercise completed successfully.");
    }

    /**
     * Main method for demonstration
     */
    public static void main(String[] args) {
        String bootstrapServers = "localhost:9092";

        BasicTopicOperations topicOps = new BasicTopicOperations(bootstrapServers);

        try {
            topicOps.exercise1();
            topicOps.exercise2();
            topicOps.exercise3();
        } finally {
            topicOps.close();
        }
    }
}
