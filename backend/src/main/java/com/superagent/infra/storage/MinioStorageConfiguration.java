package com.superagent.infra.storage;

import com.superagent.infra.config.SuperAgentProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioStorageConfiguration {

    @Bean
    public MinioClient minioClient(SuperAgentProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getStorage().getMinioEndpoint())
                .credentials(
                        properties.getStorage().getMinioAccessKey(),
                        properties.getStorage().getMinioSecretKey()
                )
                .build();
    }

    @Bean
    public MinioBucketInitializer minioBucketInitializer(MinioClient minioClient, SuperAgentProperties properties) {
        return new MinioBucketInitializer(minioClient, properties);
    }

    public static class MinioBucketInitializer {

        private final MinioClient minioClient;
        private final SuperAgentProperties properties;

        public MinioBucketInitializer(MinioClient minioClient, SuperAgentProperties properties) {
            this.minioClient = minioClient;
            this.properties = properties;
        }

        @PostConstruct
        public void ensureBucket() throws Exception {
            if (!Boolean.TRUE.equals(properties.getStorage().getMinioAutoCreateBucket())) {
                return;
            }

            String bucket = properties.getStorage().getMinioBucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        }
    }
}
