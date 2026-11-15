package store.exchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExchangeServiceTest {

    @Test
    void mapsAskToSellAndBidToBuy() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://provider");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://provider/json/last/USD-BRL")).andRespond(withSuccess("""
            {"USDBRL": {"bid": "5.6666", "ask": "5.668", "create_date": "2026-11-13 18:30:13"}}
            """, MediaType.APPLICATION_JSON));

        Rate rate = new ExchangeService(builder.build()).rate("USD", "BRL");

        assertEquals(5.668, rate.sell());
        assertEquals(5.6666, rate.buy());
        assertEquals("2026-11-13 18:30:13", rate.date());
        server.verify();
    }

}
