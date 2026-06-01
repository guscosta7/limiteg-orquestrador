package br.com.itau.limiteg.orquestrador.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

    @Value("${spring.cloud.aws.dynamodb.endpoint:}")
    private String localEndpoint;

    @Value("${spring.cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key:}")
    private String secretKey;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
            .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")));

        // If an explicit local endpoint is configured, prefer static (local) credentials.
        if (!localEndpoint.isEmpty()) {
            AwsCredentials creds;
            // prefer explicit properties if provided (application-local.yml), otherwise fall back to env
            String ak = accessKey.isEmpty() ? System.getenv().getOrDefault("AWS_ACCESS_KEY_ID", "test") : accessKey;
            String sk = secretKey.isEmpty() ? System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY", "test") : secretKey;
            String session = System.getenv().getOrDefault("AWS_SESSION_TOKEN", "");

            if (!session.isEmpty()) {
                creds = AwsSessionCredentials.create(ak, sk, session);
            } else {
                creds = AwsBasicCredentials.create(ak, sk);
            }

            builder.endpointOverride(URI.create(localEndpoint))
                   .credentialsProvider(StaticCredentialsProvider.create(creds));
        } else {
            // No local endpoint — try to build credentials from environment if present, including session token.
            String ak = System.getenv("AWS_ACCESS_KEY_ID");
            String sk = System.getenv("AWS_SECRET_ACCESS_KEY");
            String session = System.getenv("AWS_SESSION_TOKEN");

            if (ak != null && sk != null) {
                AwsCredentials creds = (session != null && !session.isEmpty())
                        ? AwsSessionCredentials.create(ak, sk, session)
                        : AwsBasicCredentials.create(ak, sk);
                builder.credentialsProvider(StaticCredentialsProvider.create(creds));
            } else {
                builder.credentialsProvider(DefaultCredentialsProvider.create());
            }
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    }
}
