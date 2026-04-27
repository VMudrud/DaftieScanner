package com.vmudrud.daftiescanner.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
@ConditionalOnExpression("!'${daft.dynamo.seen-table:}'.isBlank()")
class DynamoDbConfiguration {

    private static final String LOCAL_KEY = "local";
    private static final String DEFAULT_REGION = "eu-west-1";
    private static final String ENV_AWS_REGION = "AWS_REGION";

    @Value("${daft.dynamo.endpoint:}")
    private String endpoint;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        var region = Region.of(System.getenv().getOrDefault(ENV_AWS_REGION, DEFAULT_REGION));
        var builder = DynamoDbClient.builder().region(region);
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                   .credentialsProvider(StaticCredentialsProvider.create(
                       AwsBasicCredentials.create(LOCAL_KEY, LOCAL_KEY)));
        }
        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }
}
