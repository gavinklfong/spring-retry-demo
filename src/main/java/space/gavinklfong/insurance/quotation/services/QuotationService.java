package space.gavinklfong.insurance.quotation.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import space.gavinklfong.insurance.quotation.apiclients.CustomerSrvClient;
import space.gavinklfong.insurance.quotation.apiclients.ProductSrvClient;
import space.gavinklfong.insurance.quotation.apiclients.QuotationEngineClient;
import space.gavinklfong.insurance.quotation.dtos.QuotationEngineReq;
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
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
public class QuotationService {

	public static final int CUSTOMER_ELIGIBLE_AGE = 18;

	@Autowired
	private QuotationRepository quotationRepo;

	@Autowired
	private QuotationEngineClient quotationEngineClient;

	@Autowired
	private CustomerSrvClient customerSrvClient;	
	
	@Autowired
	private ProductSrvClient productSrvClient;

	public Quotation generateQuotation(QuotationReq request) throws RecordNotFoundException, QuotationCriteriaNotFulfilledException {
		
		Customer customer = retrieveCustomer(request.getCustomerId())
				.orElseThrow(() -> new RecordNotFoundException("Unknown customer"));

		Product product = retrieveProduct(request.getProductCode())
				.orElseThrow(() -> new RecordNotFoundException("Unknown product"));

		evaluateQuotationCriteria(request, customer, product);

		Quotation quotation = generateQuotation(customer, product);
		saveQuotation(quotation);

		return quotation;
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
		if (!Stream.of(product.getPostCodeInService()).anyMatch(s -> s.equalsIgnoreCase(request.getPostCode()))) {
			throw new QuotationCriteriaNotFulfilledException(String.format("Request post code %s is not within the scope of service", request.getPostCode()));
		}
	}

	private Optional<Product> retrieveProduct(String productCode) {
		return productSrvClient.getProduct(productCode);
	}

	private Optional<Customer> retrieveCustomer(Long customerId) {
		return customerSrvClient.getCustomer(customerId);
	}

	private Quotation generateQuotation(Customer customer, Product product) {
		QuotationEngineReq quotationEngineReq = new QuotationEngineReq(customer, product);
		return quotationEngineClient.generateQuotation(quotationEngineReq);
	}

	private void saveQuotation(Quotation quotation) {
		quotationRepo.save(quotation);
	}

}
