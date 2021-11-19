package space.gavinklfong.insurance.quotation.apiclients;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import space.gavinklfong.insurance.quotation.models.Customer;

import java.util.Optional;

@Service
public class RetryableCustomerSrvClient {
    @Autowired
    private CustomerSrvClient customerSrvClient;

    @Retryable(value = RuntimeException.class, maxAttempts = 4, backoff = @Backoff(delay = 500L, maxDelay = 3000L, multiplier = 2, random = true))
    public Optional<Customer> getCustomer(Long id) {
        return customerSrvClient.getCustomer(id);
    }
}
