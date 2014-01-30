package com.lowtuna.gymclasscal.util;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class JsoupDocumentLoader {
    private final LoadingCache<String, Document> documentCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .recordStats()
            .build(new CacheLoader<String, Document>() {
                @Override
                public Document load(String key) throws Exception {
                    URL url = new URL(key);
                    StringBuilder hostUrl = new StringBuilder();
                    hostUrl.append(url.getProtocol());
                    hostUrl.append("-");
                    hostUrl.append(url.getAuthority().replace('.', '_').replace(':', '-'));

                    Timer.Context timerContext = urlTimers.get(hostUrl.toString()).time();
                    try {
                        Connection.Response response = Jsoup.connect(key).execute();
                        if (response.statusCode() == 200) {
                            return response.parse();
                        }
                        log.warn("Received non-200 response code ({}) from {}", response.statusCode(), key);
                        throw new RuntimeException("Received non-200 status code from " + key);
                    } finally {
                        timerContext.stop();
                    }
                }
            });
    private final LoadingCache<String, Timer> urlTimers = CacheBuilder.newBuilder().build(new CacheLoader<String, Timer>() {
        @Override
        public Timer load(String key) throws Exception {
            return metricRegistry.timer(MetricRegistry.name(getClass(), key));
        }
    });

    private final MetricRegistry metricRegistry;

    public JsoupDocumentLoader(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;

        metricRegistry.register(MetricRegistry.name(getClass(), "documentCache", "size"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return documentCache.size();
            }
        });
        metricRegistry.register(MetricRegistry.name(getClass(), "documentCache", "hits"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return documentCache.stats().hitCount();
            }
        });
        metricRegistry.register(MetricRegistry.name(getClass(), "documentCache", "misses"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return documentCache.stats().missCount();
            }
        });
        metricRegistry.register(MetricRegistry.name(getClass(), "documentCache", "evictions"), new Gauge<Long>() {
            @Override
            public Long getValue() {
                return documentCache.stats().evictionCount();
            }
        });
        metricRegistry.register(MetricRegistry.name(getClass(), "documentCache", "loadPenalty"), new Gauge<Double>() {
            @Override
            public Double getValue() {
                return documentCache.stats().averageLoadPenalty();
            }
        });
    }

    public Optional<Document> loadDocument(String url) {
        try {
            return Optional.of(documentCache.get(url));
        } catch (ExecutionException e) {
            log.warn("Couldn't load document from {}", url, e);
            return Optional.absent();
        }
    }

}
