package store.exchange;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class ExchangeService {

    private final RestClient provider;

    @Autowired
    public ExchangeService(@Value("${store.exchange.provider}") String url) {
        this(RestClient.builder().baseUrl(url).requestFactory(timeouts()).build());
    }

    ExchangeService(RestClient provider) {
        this.provider = provider;
    }

    // bottlenecks: cache (60 s no Redis, compartilhado entre réplicas) evita bater na API
    // de terceiros a cada request; retry + circuit breaker protegem contra lentidão/queda dela
    @Cacheable(value = "exchanges", key = "#from + '-' + #to")
    @Retry(name = "provider", fallbackMethod = "unavailable")
    @CircuitBreaker(name = "provider")
    public Rate rate(String from, String to) {
        // AwesomeAPI: {"USDBRL": {"bid": "5.66", "ask": "5.67", "create_date": "2026-11-13 18:30:13", ...}}
        Map<String, Map<String, String>> body = provider.get()
            .uri("/json/last/{from}-{to}", from, to)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        Map<String, String> q = body.get(from + to);
        return new Rate(Double.valueOf(q.get("ask")), Double.valueOf(q.get("bid")), q.get("create_date"));
    }

    // fallback no @Retry (camada externa): roda uma vez, após as tentativas ou com o circuito aberto
    private Rate unavailable(String from, String to, Throwable e) {
        if (e instanceof HttpClientErrorException.NotFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown currency pair " + from + "-" + to);
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "exchange provider unavailable");
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(2));
        f.setReadTimeout(Duration.ofSeconds(3));
        return f;
    }

}
