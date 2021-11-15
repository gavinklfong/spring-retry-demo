package space.gavinklfong.insurance.quotation.services;

import com.github.javafaker.Faker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import space.gavinklfong.insurance.quotation.apiclients.CustomerSrvClient;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@Slf4j
@EnableRetry
@SpringJUnitConfig
@TestPropertySource(properties = {
        "app.quotation.expiryTime=1440"
})
@ContextConfiguration(classes = {QuotationService.class, RetryableCustomerSrvClient.class})
@Tag("UnitTest")
public class QuotationServiceTests {

    @MockBean
    private QuotationRepository quotationRepo;

    @MockBean
    private CustomerSrvClient customerSrvClient;

    @MockBean
    private ProductSrvClient productSrvClient;

    @Autowired
    private QuotationService quotationService;

    private Faker faker = new Faker();

    private static final String PRODUCT_CODE = "CAR001-01";
    private static final long CUSTOMER_ID = 1l;
    private static final String POST_CODE = "SW20";
    private static final String POST_CODE_OUT_SCOPE = "SM3";
    private static final String POST_CODE_WITH_DISCOUNT = "XX1";
    private static final double QUOTATION_AMOUNT = 1500;

    private static final double PRODUCT_LISTED_PRICE = 1500;
    private static final String[] PRODUCT_POST_CODES = {POST_CODE, POST_CODE_WITH_DISCOUNT, "SM1", "E12"};
    private static final String[] PRODUCT_POST_CODES_WITH_DISCOUNT = {POST_CODE_WITH_DISCOUNT, "E3", "E4"};
    private static final double PRODUCT_POST_CODE_DISCOUNT = 0.1;

    private static final double QUOTATION_AMOUNT_WITH_DISCOUNT = QUOTATION_AMOUNT * (1 - PRODUCT_POST_CODE_DISCOUNT);

    @Test
    void givenEverythingPassed_whenRequestForQuotation_thenReturnListedPrice() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {

        setupQuotationRepo();
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(18, 99)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate());
        setupProductSrvClient();

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE)
                .build();
        Quotation quotation = quotationService.generateQuotation(req);

        assertEquals(QUOTATION_AMOUNT, quotation.getAmount());
        assertNotNull(quotation.getQuotationCode());
        assertTrue(quotation.getExpiryTime().isAfter(LocalDateTime.now()));
        assertEquals(CUSTOMER_ID, quotation.getCustomerId());
        assertEquals(PRODUCT_CODE, quotation.getProductCode());
    }

    @Test
    void givenCustomerBelow18_whenRequestForQuotation_thenThrowException() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {

        setupQuotationRepo();
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(0, 17)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate());
        setupProductSrvClient();

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE)
                .build();
        assertThrows(QuotationCriteriaNotFulfilledException.class, () ->
                quotationService.generateQuotation(req)
        );
    }

    @Test
    void givenCustomerAbove18AndPostCodeOutOfScope_whenRequestForQuotation_thenThrowException() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {

        setupQuotationRepo();
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(18, 99)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate());
        setupProductSrvClient();

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE_OUT_SCOPE)
                .build();

        assertThrows(QuotationCriteriaNotFulfilledException.class, () -> {
            quotationService.generateQuotation(req);
        });

    }

    @Test
    void givenCustomerBelow18AndPostCodeOutOfScope_whenRequestForQuotation_thenThrowException() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {

        setupQuotationRepo();
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(0, 17)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate());
        setupProductSrvClient();

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE_OUT_SCOPE)
                .build();
        assertThrows(QuotationCriteriaNotFulfilledException.class, () -> {
            quotationService.generateQuotation(req);
        });
    }

    @Test
    void givenUnknownCustomer_whenRequestForQuotation_thenThrowException() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {

        setupQuotationRepo();
        setupProductSrvClient();

        when(customerSrvClient.getCustomer(anyLong())).thenReturn(Optional.empty());

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE)
                .build();
        assertThrows(RecordNotFoundException.class, () -> {
            quotationService.generateQuotation(req);
        });
    }

    @Test
    void givenUnknownProduct_whenRequestForQuotation_thenThrowException() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {

        setupQuotationRepo();
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(18, 99)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate());

        when(productSrvClient.getProduct(anyString())).thenReturn(Optional.empty());

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE)
                .build();
        assertThrows(RecordNotFoundException.class, () -> {
            quotationService.generateQuotation(req);
        });
    }

    @Test
    void givenCustomerAbove18AndPostCodeWithDiscount_whenRequestForQuotation_thenReturnPriceWithDiscount() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {

        setupQuotationRepo();
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(18, 99)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate());
        setupProductSrvClient();

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE_WITH_DISCOUNT)
                .build();
        Quotation quotation = quotationService.generateQuotation(req);

        assertEquals(QUOTATION_AMOUNT_WITH_DISCOUNT, quotation.getAmount());
        assertNotNull(quotation.getQuotationCode());
        assertTrue(quotation.getExpiryTime().isAfter(LocalDateTime.now()));
        assertEquals(CUSTOMER_ID, quotation.getCustomerId());
        assertEquals(PRODUCT_CODE, quotation.getProductCode());
    }

    @Test
    void givenRetryonCustomerRetrievalSuccess_whenRequestForQuotation_thenSuccess() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {
        setupQuotationRepo();
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(18, 99)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate(), 3, true);
        setupProductSrvClient();

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE)
                .build();
        Quotation quotation = quotationService.generateQuotation(req);

        assertEquals(QUOTATION_AMOUNT, quotation.getAmount());
        assertNotNull(quotation.getQuotationCode());
        assertTrue(quotation.getExpiryTime().isAfter(LocalDateTime.now()));
        assertEquals(CUSTOMER_ID, quotation.getCustomerId());
        assertEquals(PRODUCT_CODE, quotation.getProductCode());
    }

    @Test
    void givenAllRetryOnCustomerRetrievalExhausted_whenRequestForQuotation_thenThrowException() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {
        setupQuotationRepo();
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(18, 99)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate(), 4, true);
        setupProductSrvClient();

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE)
                .build();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> quotationService.generateQuotation(req));
    }

    @Test
    void givenRetryOnProductRetrievalSuccess_whenRequestForQuotation_thenSuccess() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {
        setupQuotationRepo();
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(18, 99)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate());
        setupProductSrvClient(3, true);

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE)
                .build();

        Quotation quotation = quotationService.generateQuotation(req);

        assertEquals(QUOTATION_AMOUNT, quotation.getAmount());
        assertNotNull(quotation.getQuotationCode());
        assertTrue(quotation.getExpiryTime().isAfter(LocalDateTime.now()));
        assertEquals(CUSTOMER_ID, quotation.getCustomerId());
        assertEquals(PRODUCT_CODE, quotation.getProductCode());
    }

    @Test
    void givenAllRetryOnProductRetrievalExhausted_whenRequestForQuotation_thenThrowException() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {
        setupQuotationRepo();
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(18, 99)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate());
        setupProductSrvClient(4, true);

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE)
                .build();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> quotationService.generateQuotation(req));
    }

    @Test
    void givenRetryOnQuotationSaveSuccess_whenRequestForQuotation_thenSuccess() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {
        setupQuotationRepo(1, true);
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(18, 99)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate());
        setupProductSrvClient();

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE)
                .build();

        Quotation quotation = quotationService.generateQuotation(req);

        assertEquals(QUOTATION_AMOUNT, quotation.getAmount());
        assertNotNull(quotation.getQuotationCode());
        assertTrue(quotation.getExpiryTime().isAfter(LocalDateTime.now()));
        assertEquals(CUSTOMER_ID, quotation.getCustomerId());
        assertEquals(PRODUCT_CODE, quotation.getProductCode());
    }

    @Test
    void givenAllRetryOnQuotationSaveExhausted_whenRequestForQuotation_thenThrowException() throws IOException, RecordNotFoundException, QuotationCriteriaNotFulfilledException {
        setupQuotationRepo(4, false);
        setupCustomerSrvClient(CUSTOMER_ID, faker.date().birthday(18, 99)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate());
        setupProductSrvClient();

        QuotationReq req = QuotationReq.builder()
                .customerId(CUSTOMER_ID)
                .productCode(PRODUCT_CODE)
                .postCode(POST_CODE)
                .build();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> quotationService.generateQuotation(req));
    }

    private void setupCustomerSrvClient(Long customerId, LocalDate dob) throws IOException {
        setupCustomerSrvClient(customerId, dob, 0, true);
    }

    private void setupCustomerSrvClient(Long customerId, LocalDate dob, int noOfFailure, boolean isSuccessAtLast) throws IOException {

        OngoingStubbing stubbing = when(customerSrvClient.getCustomer(anyLong()));
        for (int i = 0; i < noOfFailure; i++) {
            stubbing = stubbing.thenThrow(new RuntimeException("Exception " + i));
        }

        if (isSuccessAtLast) {
            Optional<Customer> customer = Optional.of(generateCustomer(customerId, dob));
            stubbing.thenReturn(customer);
        }
    }

    private Customer generateCustomer(Long customerId, LocalDate dob) {
        return Customer.builder()
                .id(customerId)
                .dob(dob)
                .name(faker.name().name())
                .build();
    }

    private Product generateProduct() {
        return Product.builder()
                .productCode(PRODUCT_CODE)
                .productClass("Online")
                .productPlan("Home-General")
                .postCodesInService(PRODUCT_POST_CODES)
                .listedPrice(PRODUCT_LISTED_PRICE)
                .postCodesWithDiscount(PRODUCT_POST_CODES_WITH_DISCOUNT)
                .postCodeDiscountRate(PRODUCT_POST_CODE_DISCOUNT)
                .build();
    }

    private void setupProductSrvClient() {
        setupProductSrvClient(0, true);
    }

    private void setupProductSrvClient(int noOfFailure, boolean isSuccessAtLast) {

        OngoingStubbing stubbing = when(productSrvClient.getProduct(anyString()));
        for (int i = 0; i < noOfFailure; i++) {
            stubbing = stubbing.thenThrow(new RuntimeException("Exception " + i));
        }

        if (isSuccessAtLast) {
            Optional<Product> product = Optional.of(generateProduct());
            stubbing.thenReturn(product);
        }
    }

    private void setupQuotationRepo() {
        setupQuotationRepo(0, true);
    }

    private void setupQuotationRepo(int noOfFailure, boolean isSuccessAtLast) {

        OngoingStubbing stubbing = when(quotationRepo.save(any(Quotation.class)));
        for (int i = 0; i < noOfFailure; i++) {
            stubbing = stubbing.thenThrow(new RuntimeException("Exception " + i));
        }

        if (isSuccessAtLast) {
            stubbing.thenAnswer(invocation -> (Quotation) invocation.getArgument(0));
        }
    }
}
