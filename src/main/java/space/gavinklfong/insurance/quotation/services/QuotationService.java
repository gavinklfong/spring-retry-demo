package space.gavinklfong.insurance.quotation.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import space.gavinklfong.insurance.quotation.apiclients.ProductSrvClient;
import space.gavinklfong.insurance.quotation.apiclients.RetryableCustomerSrvClient;
import space.gavinklfong.insurance.quotation.dtos.QuotationReq;
import space.gavinklfong.insurance.quotation.exceptions.QuotationCriteriaNotFulfilledException;
import space.gavinklfong.insurance.quotation.exceptions.RecordNotFoundException;
import space.gavinklfong.insurance.quotation.models.Customer;
import space.gavinklfong.insurance.quotation.models.Product;
import space.gavinklfong.insurance.quotation.models.Quotation;
import space.gavinklfong.insurance.quotation.repositories.QuotationRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;

@Slf4j
@Service
public class QuotationService {

	public static final int CUSTOMER_ELIGIBLE_AGE = 18;

	@Value("${app.quotation.expiryTime}")
	private Integer quotationExpiryTime;

	@Autowired
	private QuotationRepository quotationRepo;

	@Autowired
	private RetryableCustomerSrvClient customerSrvClient;

	@Autowired
	private ProductSrvClient productSrvClient;

	public Quotation generateQuotation(QuotationReq request) throws RecordNotFoundException, QuotationCriteriaNotFulfilledException {
		
		Customer customer = customerSrvClient.getCustomer(request.getCustomerId())
				.orElseThrow(() -> new RecordNotFoundException("Unknown customer"));

		Product product = retrieveProduct(request.getProductCode())
				.orElseThrow(() -> new RecordNotFoundException("Unknown product"));

		evaluateQuotationCriteria(request, customer, product);

		return saveQuotation(
				generateQuotation(request, customer, product)
		);

	}

	public Optional<Quotation> fetchQuotation(String quotationCode) {
		return quotationRepo.findById(quotationCode);
	}

	private void evaluateQuotationCriteria(QuotationReq request, Customer customer, Product product) throws QuotationCriteriaNotFulfilledException {

		// customer's age should be 18 or above
		LocalDateTime now = LocalDateTime.now();
		Period period = Period.between(customer.getDob(), now.toLocalDate());
		if (period.getYears() < CUSTOMER_ELIGIBLE_AGE) {
			throw new QuotationCriteriaNotFulfilledException("customer's age < 18");
		}

		log.info("Retrieved product: " + product.toString());
		log.info("Request post code: " + request.toString());

		// the request post code should be within the product's service scope
		if (!Stream.of(product.getPostCodesInService()).anyMatch(s -> s.equalsIgnoreCase(request.getPostCode()))) {
			throw new QuotationCriteriaNotFulfilledException(String.format("Request post code %s is not within the scope of service", request.getPostCode()));
		}
	}

	private Quotation generateQuotation(QuotationReq request, Customer customer, Product product) {

		Double quotationAmount = product.getListedPrice().doubleValue();

		LocalDateTime now = LocalDateTime.now();

		// check if post code is in the discount list
		//
		// Offer discount if customer's post code matches the specification in product info
		//
		if (nonNull(product.getPostCodesWithDiscount())) {
			boolean found = Arrays.stream(product.getPostCodesWithDiscount())
					.anyMatch(x -> x.equalsIgnoreCase(request.getPostCode()));
			if (found) {
				log.debug("Post code matched, apply discount rate = " + product.getPostCodeDiscountRate());
				quotationAmount *= (1 - product.getPostCodeDiscountRate());
			}
		}

		log.debug("After post code check, amount = " + quotationAmount);

		// Construct quotation and save to data store
		return Quotation.builder()
				.quotationCode(UUID.randomUUID().toString())
				.customerId(customer.getId())
				.expiryTime(now.plusMinutes(quotationExpiryTime))
				.productCode(request.getProductCode())
				.amount(quotationAmount)
				.build();
	}

	private Quotation saveQuotation(Quotation quotation) {
		RetryTemplate retryTemplate = RetryTemplate.builder()
				.maxAttempts(3)
				.fixedBackoff(1000L)
				.build();

		return retryTemplate.execute(arg -> quotationRepo.save(quotation));
	}

	private Optional<Product> retrieveProduct(String productCode) {
		RetryTemplate retryTemplate = RetryTemplate.builder()
				.maxAttempts(4)
				.retryOn(RuntimeException.class)
				.exponentialBackoff(300L, 2, 5000L, true)
				.build();

		return retryTemplate.execute(arg -> productSrvClient.getProduct(productCode));
	}
}
