package ru.perevalov.gamerecommenderai.config;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.perevalov.gamerecommenderai.grpc.GameRecommenderServiceGrpc;

@Slf4j
@Configuration
public class GrpcConfig {

    @Value("${grpc.ai-service.host:localhost}")
    private String aiServiceHost;

    @Value("${grpc.ai-service.port:9090}")
    private int aiServicePort;

    @Bean
    public ManagedChannel managedChannel() {
        log.info("Creating gRPC channel to {}:{}", aiServiceHost, aiServicePort);
        return NettyChannelBuilder
                .forAddress(aiServiceHost, aiServicePort)
                .usePlaintext() // For local development only
                .build();
    }

    @Bean
    public GameRecommenderServiceGrpc.GameRecommenderServiceBlockingStub gameRecommenderServiceStub(ManagedChannel channel) {
        return GameRecommenderServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    public GameRecommenderServiceGrpc.GameRecommenderServiceStub gameRecommenderServiceAsyncStub(ManagedChannel channel) {
        return GameRecommenderServiceGrpc.newStub(channel);
    }
}
