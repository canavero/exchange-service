package store.exchange;

import java.io.Serializable;

// cotação guardada no cache Redis
public record Rate(Double sell, Double buy, String date) implements Serializable {
}
