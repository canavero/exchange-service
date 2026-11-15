package store.exchange;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExchangeResource implements ExchangeController {

    @Autowired
    private ExchangeService exchangeService;

    @Override
    public ResponseEntity<ExchangeOut> exchange(String from, String to, String idAccount) {
        Rate r = exchangeService.rate(from.toUpperCase(), to.toUpperCase());
        return ResponseEntity.ok(ExchangeOut.builder()
            .sell(r.sell())
            .buy(r.buy())
            .date(r.date())
            .idAccount(idAccount)
            .build());
    }

    @Override
    public ResponseEntity<Void> healthCheck() {
        return ResponseEntity.ok().build();
    }

}
