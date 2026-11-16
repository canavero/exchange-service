package store.exchange;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class ExchangeServiceTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ptax");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private void ptax(String currency, String json) {
        server.expect(requestTo(containsString("@m='" + currency + "'")))
            .andRespond(withSuccess("{\"@odata.context\": \"x\", \"value\": [" + json + "]}",
                MediaType.APPLICATION_JSON));
    }

    @Test
    void mapsVendaToSellAndCompraToBuy() {
        ptax("USD", """
            {"cotacaoCompra": 5.7519, "cotacaoVenda": 5.7525, "dataHoraCotacao": "2026-11-16 12:06:13.954"}""");

        Rate rate = new ExchangeService(builder.build()).rate("USD", "BRL");

        assertEquals(5.7525, rate.sell());
        assertEquals(5.7519, rate.buy());
        assertEquals("2026-11-16 12:06:13.954", rate.date());
        server.verify();
    }

    @Test
    void crossesThroughBrl() {
        ptax("EUR", """
            {"cotacaoCompra": 6.0, "cotacaoVenda": 6.6, "dataHoraCotacao": "2026-11-16 11:04:10.683"}""");
        ptax("USD", """
            {"cotacaoCompra": 5.0, "cotacaoVenda": 5.5, "dataHoraCotacao": "2026-11-16 12:06:13.954"}""");

        Rate rate = new ExchangeService(builder.build()).rate("EUR", "USD");

        assertEquals(6.6 / 5.0, rate.sell()); // compra EUR pagando USD: vende USD (compra) e compra EUR (venda)
        assertEquals(6.0 / 5.5, rate.buy());
        assertEquals("2026-11-16 12:06:13.954", rate.date()); // a mais recente das duas
        server.verify();
    }

    @Test
    void unknownCurrencyIsNotFound() {
        ptax("XXX", "");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> new ExchangeService(builder.build()).rate("XXX", "BRL"));
        assertEquals(404, e.getStatusCode().value());
    }

}
