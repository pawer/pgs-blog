package com.pgs.spark.bigdata.config;

import com.codahale.metrics.MetricRegistry;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.policies.LoadBalancingPolicy;
import com.datastax.driver.core.policies.ReconnectionPolicy;
import com.datastax.driver.core.policies.RetryPolicy;
import com.datastax.driver.extras.codecs.jdk8.LocalDateCodec;
import com.pgs.spark.bigdata.config.codec.CustomZonedDateTimeCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cassandra.CassandraProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * The type Cassandra configuration.
 */
@Configuration
@EnableConfigurationProperties(CassandraProperties.class)
public class CassandraConfiguration {

    @Value("${spring.data.cassandra.protocolVersion:V4}")
    private ProtocolVersion protocolVersion;

    /**
     * The Metric registry.
     */
    @Autowired(required = false)
    MetricRegistry metricRegistry;

    private final Logger log = LoggerFactory.getLogger(CassandraConfiguration.class);

    /**
     * Cassandra cluster.
     *
     * @param properties the configuration
     * @return the cluster
     */
    @Bean
    public Cluster cluster(CassandraProperties properties) {
        Cluster.Builder builder = Cluster.builder()
                .withClusterName(properties.getClusterName())
                .withProtocolVersion(protocolVersion)
                .withPort(properties.getPort());

        if (properties.getUsername() != null) {
            builder.withCredentials(properties.getUsername(), properties.getPassword());
        }
        if (properties.getCompression() != null) {
            builder.withCompression(properties.getCompression());
        }
        if (properties.getLoadBalancingPolicy() != null) {
            LoadBalancingPolicy policy = instantiate(properties.getLoadBalancingPolicy());
            builder.withLoadBalancingPolicy(policy);
        }
        builder.withQueryOptions(getQueryOptions(properties));
        if (properties.getReconnectionPolicy() != null) {
            ReconnectionPolicy policy = instantiate(properties.getReconnectionPolicy());
            builder.withReconnectionPolicy(policy);
        }
        if (properties.getRetryPolicy() != null) {
            RetryPolicy policy = instantiate(properties.getRetryPolicy());
            builder.withRetryPolicy(policy);
        }
        builder.withSocketOptions(getSocketOptions());
        if (properties.isSsl()) {
            builder.withSSL();
        }
        String points = properties.getContactPoints();
        builder.addContactPoints(StringUtils.commaDelimitedListToStringArray(points));

        Cluster cluster = builder.build();

        cluster.getConfiguration().getCodecRegistry()
                .register(LocalDateCodec.instance)
                .register(CustomZonedDateTimeCodec.instance);

        if (metricRegistry != null) {
            cluster.init();
            metricRegistry.registerAll(cluster.getMetrics().getRegistry());
        }

        return cluster;
    }

    /**
     * Helper method to instantiate object of given type.
     *
     * @param <T>  the type parameter
     * @param type the type
     * @return new object instance
     */
    public static <T> T instantiate(Class<T> type) {
        return BeanUtils.instantiate(type);
    }

    private QueryOptions getQueryOptions(CassandraProperties properties) {
        QueryOptions options = new QueryOptions();
        if (properties.getConsistencyLevel() != null) {
            options.setConsistencyLevel(properties.getConsistencyLevel());
        }
        if (properties.getSerialConsistencyLevel() != null) {
            options.setSerialConsistencyLevel(properties.getSerialConsistencyLevel());
        }
        options.setFetchSize(properties.getFetchSize());
        return options;
    }

    private SocketOptions getSocketOptions() {
        SocketOptions options = new SocketOptions();
        options.setConnectTimeoutMillis(50000);
        options.setReadTimeoutMillis(120000);
        return options;
    }

    /**
     * Cassandra session.
     *
     * @param properties the properties
     * @param cluster    the cluster
     * @return the session
     */
    @Bean(destroyMethod = "close")
    public Session session(CassandraProperties properties, Cluster cluster) {
        log.debug("Configuring Cassandra session");
        return StringUtils.hasText(properties.getKeyspaceName()) ? cluster.connect(properties.getKeyspaceName()) : cluster.connect();
    }
}
