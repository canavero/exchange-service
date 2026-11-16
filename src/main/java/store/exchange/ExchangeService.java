package store.exchange;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class ExchangeService {

    private static final DateTimeFormatter PTAX_DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    private final RestClient provider;

    @Autowired
    public ExchangeService(@Value("${store.exchange.provider}") String url) {
        this(RestClient.builder().baseUrl(url).requestFactory(timeouts()).build());
    }

    ExchangeService(RestClient provider) {
        this.provider = provider;
    }

    // bottlenecks: cache (60 s no Redis, compartilhado entre réplicas) evita bater no BACEN
    // a cada request; retry + circuit breaker protegem contra lentidão/queda dele
    @Cacheable(value = "exchanges", key = "#from + '-' + #to")
    @Retry(name = "provider", fallbackMethod = "unavailable")
    @CircuitBreaker(name = "provider")
    public Rate rate(String from, String to) {
        // PTAX só cota contra o real: o par sai cruzado via BRL. Quem compra `from` pagando `to`
        // vende `to` ao banco (compra) e compra `from` (venda)
        Quote f = brl(from), t = brl(to);
        return new Rate(f.sell() / t.buy(), f.buy() / t.sell(), f.date().compareTo(t.date()) > 0 ? f.date() : t.date());
    }

    private record Quote(double buy, double sell, String date) {}

    // última cotação da moeda em BRL nos últimos 7 dias (cobre fim de semana e feriado)
    private Quote brl(String currency) {
        if (currency.equals("BRL")) return new Quote(1, 1, "");
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        // {"@odata.context": "...", "value": [{"cotacaoCompra": 5.7519, "cotacaoVenda": 5.7525, "dataHoraCotacao": "2026-11-16 12:06:13.954", ...}]}
        Map<String, Object> body = provider.get()
            .uri("/CotacaoMoedaPeriodo(moeda=@m,dataInicial=@i,dataFinalCotacao=@f)"
                + "?@m='{c}'&@i='{i}'&@f='{f}'&$orderby=dataHoraCotacao desc&$top=1&$format=json",
                currency, today.minusDays(7).format(PTAX_DATE), today.format(PTAX_DATE))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> value = (List<Map<String, Object>>) body.get("value");
        if (value.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown currency " + currency);
        }
        Map<String, Object> q = value.get(0);
        return new Quote(((Number) q.get("cotacaoCompra")).doubleValue(),
            ((Number) q.get("cotacaoVenda")).doubleValue(), (String) q.get("dataHoraCotacao"));
    }

    // fallback no @Retry (camada externa): roda uma vez, após as tentativas ou com o circuito aberto
    private Rate unavailable(String from, String to, Throwable e) {
        if (e instanceof ResponseStatusException notFound) {
            throw notFound;
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
