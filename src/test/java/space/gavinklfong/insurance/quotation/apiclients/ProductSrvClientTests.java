package space.gavinklfong.insurance.quotation.apiclients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import space.gavinklfong.insurance.quotation.models.Product;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@ExtendWith(SpringExtension.class)
@WireMockTest
public class ProductSrvClientTests {

    private static final String POST_CODE = "SW20";
    private static final String POST_CODE_OUT_SCOPE = "SM3";
    private static final String POST_CODE_WITH_DISCOUNT = "XX1";

    private static final String PRODUCT_CODE = "CAR001-01";
    private static final double PRODUCT_LISTED_PRICE = 1500;
    private static final String[] PRODUCT_POST_CODES = {POST_CODE, POST_CODE_WITH_DISCOUNT, "SM1", "E12"};
    private static final String[] PRODUCT_POST_CODES_WITH_DISCOUNT = {POST_CODE_WITH_DISCOUNT, "E3", "E4"};
    private static final double PRODUCT_POST_CODE_DISCOUNT = 0.1;

    private ObjectMapper objectMapper = new ObjectMapper();

    private ProductSrvClient productSrvClient;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        productSrvClient = new ProductSrvClient(wmRuntimeInfo.getHttpBaseUrl());
        WireMock.reset();
    }

    @Test
    void givenRecordExists_whenGetProduct_thenReturnProduct() throws JsonProcessingException {

        // Setup request matcher and response using MockServerClient API
        stubFor(get(format("/products/%s", PRODUCT_CODE)).willReturn(
                aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(
                                objectMapper.writeValueAsString(
                                        generateProduct()
                                )
                        )
        ));

        // Initialize API client and trigger request
        Optional<Product> productOptional = productSrvClient.getProduct(PRODUCT_CODE);

        // Assert response
        assertTrue(productOptional.isPresent());
        Product product = productOptional.get();
        assertNotNull(product.getProductPlan());
        assertNotNull(product.getProductClass());
        assertTrue(product.getPostCodesInService().length > 0);
    }

    @Test
    void givenRecordExists_whenGetProducts_thenReturnProductList() throws JsonProcessingException {

        List products = Arrays.asList(generateProduct());

        stubFor(get("/products").willReturn(
                aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(products))
        ));

        // Initialize API client and trigger request
        List<Product> actualProducts = productSrvClient.getProducts();

        // Assert response
        assertNotNull(actualProducts);
        assertTrue(actualProducts.size() > 0);
        assertEquals(products, actualProducts);
    }

    private Product generateProduct() {
        return Product.builder()
                .productCode(PRODUCT_CODE)
                .productPlan("Home-General")
                .productClass("Online")
                .postCodesInService(PRODUCT_POST_CODES)
                .listedPrice(PRODUCT_LISTED_PRICE)
                .postCodeDiscountRate(PRODUCT_POST_CODE_DISCOUNT)
                .postCodesWithDiscount(PRODUCT_POST_CODES_WITH_DISCOUNT)
                .build();
    }


    @Test
    void givenRecordNotFound_whenGetProduct_thenReturnEmty() {

        stubFor(get(format("/products/%s", PRODUCT_CODE)).willReturn(
                aResponse().withStatus(404)
        ));

        // Initialize API client and trigger request
        Optional<Product> productOptional = productSrvClient.getProduct(PRODUCT_CODE);

        // Assert response
        assertTrue(productOptional.isEmpty());
    }

    @Test
    void givenRecordNotFound_whenGetProducts_thenReturnEmpty() {

        // Setup request matcher and response using OpenAPI definition
        stubFor(get("/products").willReturn(
                aResponse().withStatus(404)
        ));

        // Initialize API client and trigger request
        List<Product> products = productSrvClient.getProducts();

        // Assert response
        assertTrue(products.size() == 0);
    }
}
