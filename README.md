# Demonstration of Spring Retry

This repository contains a sample implementation of price quotation API and automated test cases. 

The purpose to demonstrate the use of retry supported by Spring framework.

## System Overview
The price quotation API exposes an endpoint for quotation request

```
[POST] /quotations/generate`
```

The service retrieves information from Product API and Customer API for evaulation. The generated generated quotation is then saved into data store and return back to client.

![System Logic](https://raw.githubusercontent.com/gavinklfong/spring-retry-demo/master/blob/System_Overview.png?raw=true)


## Build & Execution

To build and run API, run this command to compile and run unit tests and integration tests
```
mvn clean install
```

## Declarative Approach
The use of Spring annotation ```@Retryable``` injects retry mechanism into the system logic by creating a proxy. It is transparent to the system logic as the proxy is injected during runtime.

Refer to ```RetryableCustomerSrvClient``` for the sample use of ```@Retryable``` annotation


![Proxy](https://raw.githubusercontent.com/gavinklfong/spring-retry-demo/master/blob/Retry_Proxy.png?raw=true)

## Imperative Approach
Another approach is to make use of ```RetryTemplate``` which allows system logic to determine the retry policy programmatically.

Refer to ```QuotationService.retrieveProduct()``` for the sample use of ```RetryTemplate```

![RetryTemplate](https://raw.githubusercontent.com/gavinklfong/spring-retry-demo/master/blob/RetryTemplate.png?raw=true)


## Automated Test for Retry Logic
It is hard to simulate error in data stores and external APIs. Mockito is a great tool to mock the exception error in unit tests.

You can find the sample unit test cases in ```QuotationServiceTests```.