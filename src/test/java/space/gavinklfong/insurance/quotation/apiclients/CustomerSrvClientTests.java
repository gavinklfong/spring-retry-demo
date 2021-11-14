package space.gavinklfong.insurance.quotation.apiclients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.javafaker.Faker;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import space.gavinklfong.insurance.quotation.models.Customer;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@ExtendWith(SpringExtension.class)
@WireMockTest
public class CustomerSrvClientTests {

    final long CUSTOMER_ID = 1l;

    private Faker faker = new Faker();

    private ObjectMapper objectMapper;

    private CustomerSrvClient customerSrvClient;

    public CustomerSrvClientTests() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        customerSrvClient = new CustomerSrvClient(wmRuntimeInfo.getHttpBaseUrl());
        WireMock.reset();
    }

    @Test
    void givenRecordExists_whenGetCustomer_thenReturnCustomerRecord() throws JsonProcessingException {

        Customer expectedCustomer = generateCustomer();

        stubFor(get(format("/customers/%s", CUSTOMER_ID)).willReturn(
                aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(expectedCustomer))
        ));

        Optional<Customer> customerOptional = customerSrvClient.getCustomer(CUSTOMER_ID);

        // Assert response
        assertTrue(customerOptional.isPresent());
        Customer customer = customerOptional.get();
        assertNotNull(customer.getDob());
        assertNotNull(customer.getName());
        assertEquals(expectedCustomer, customer);
    }

    @Test
    void givenRecordNotFound_whenGetCustomer_thenReturnEmpty() {

        stubFor(get(format("/customers/%s", CUSTOMER_ID)).willReturn(
                aResponse().withStatus(404)
        ));

        // Initialize API client and trigger request
        Optional<Customer> customerOptional = customerSrvClient.getCustomer(CUSTOMER_ID);

        // Assert response
        assertTrue(customerOptional.isEmpty());
    }

    @Test
    void givenRecordExists_whenGetCustomers_thenReturnCustomerList() throws JsonProcessingException {

        List expectedCustomerList = Arrays.asList(generateCustomer());

        stubFor(get("/customers").willReturn(
                aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(expectedCustomerList))));

        // Initialize API client and trigger request
        List<Customer> customers = customerSrvClient.getCustomers();

        // Assert response
        assertTrue(customers.size() > 0);
    }

    @Test
    void givenRecordNotFound_whenGetCustomers_thenReturnEmpty() throws JsonProcessingException {

        // Setup request matcher and response using OpenAPI definition
        stubFor(get("/customers").willReturn(
                aResponse().withStatus(404)
        ));

        // Initialize API client and trigger request
        List<Customer> customers = customerSrvClient.getCustomers();

        // Assert response
        assertTrue(customers.size() == 0);
    }

    private LocalDate generateDob(int minAge, int maxAge) {
        return faker.date().birthday(minAge, maxAge)
                .toInstant().atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private String generateName() {
        return faker.name().name();
    }

    private Customer generateCustomer() {
        return Customer.builder()
                .id(CUSTOMER_ID)
                .dob(generateDob(18, 99))
                .name(generateName())
                .build();
    }
}
