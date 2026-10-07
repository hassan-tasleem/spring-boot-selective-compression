# Spring Boot Selective Compression

A Spring Boot library that provides **opt-in HTTP response compression** using **Brotli** and **GZIP**.

Unlike Spring Boot's global response compression, this library allows you to control compression at the **controller** or **endpoint** level using the `@Compress` annotation.

Only endpoints explicitly annotated with `@Compress` are eligible for compression.

## Features

* Opt-in HTTP response compression
* Brotli (`br`) support
* GZIP (`gzip`) support
* Controller-level compression
* Endpoint-level compression
* Configurable minimum response size
* Automatic compression based on the client's `Accept-Encoding` header
* Endpoints without `@Compress` remain uncompressed

## How It Works

The library uses the `@Compress` annotation to determine which responses are eligible for compression.

For example:

```java
@Compress
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping
    public List<Product> getProducts() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.findById(id);
    }
}
```

All endpoints inside `ProductController` are eligible for compression.

Alternatively, compression can be enabled for individual endpoints:

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Compress
    @GetMapping
    public List<Product> getProducts() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.findById(id);
    }
}
```

In this case, only:

```text
GET /api/products
```

is eligible for compression.

The following endpoint remains uncompressed:

```text
GET /api/products/{id}
```

## Installation

Add the library to your Spring Boot application's `pom.xml`:

```xml
<dependency>
    <groupId>YOUR_GROUP_ID</groupId>
    <artifactId>YOUR_ARTIFACT_ID</artifactId>
    <version>1.0.0</version>
</dependency>
```

Replace the Maven coordinates with the coordinates of the published library.

## Usage

Import the `@Compress` annotation:

```java
import com.compression.compression_library.annotation.Compress;
```

You can then apply it to a controller or individual endpoint.

### Controller-Level Compression

Use `@Compress` on a controller when most or all endpoints in that controller should support compression.

```java
@Compress
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping
    public List<Product> getProducts() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.findById(id);
    }
}
```

Both endpoints are eligible for compression.

### Endpoint-Level Compression

Use `@Compress` directly on a method when only a specific endpoint should support compression.

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Compress
    @GetMapping
    public List<Product> getProducts() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.findById(id);
    }
}
```

Only `/api/products` is eligible for compression.

## Minimum Response Size

Compression is not always beneficial for small responses because the compression process itself introduces overhead.

The `@Compress` annotation allows you to configure the minimum response size.

By default, the minimum response size is:

```text
1024 bytes
```

You can change it for a specific endpoint:

```java
@Compress(minResponseSize = 2048)
@GetMapping("/products")
public List<Product> getProducts() {
    return productService.findAll();
}
```

With this configuration, the response must reach at least `2048` bytes before it becomes eligible for compression.

You can also use a smaller threshold:

```java
@Compress(minResponseSize = 512)
@GetMapping("/products")
public List<Product> getProducts() {
    return productService.findAll();
}
```

## Compression Algorithms

The library supports:

| Algorithm | Encoding |
| --------- | -------- |
| Brotli    | `br`     |
| GZIP      | `gzip`   |

The compression algorithm is selected automatically based on the client's `Accept-Encoding` request header.

### Brotli

If the client supports Brotli:

```http
Accept-Encoding: br
```

the library can return the response using Brotli compression.

### GZIP

If the client supports GZIP:

```http
Accept-Encoding: gzip
```

the library can return the response using GZIP compression.

### Multiple Encodings

If the client supports both:

```http
Accept-Encoding: br, gzip
```

the library can select Brotli.

### No Supported Encoding

If the client does not advertise a supported compression format, the response is returned normally without compression.

For example:

```http
Accept-Encoding: identity
```

results in an uncompressed response.

## When Is a Response Compressed?

A response is eligible for compression when the following conditions are satisfied:

1. The endpoint or its controller is annotated with `@Compress`.
2. The response meets the configured minimum response size.
3. The client supports a compression algorithm through `Accept-Encoding`.
4. The response has a compressible response type/content.
5. The response has not already been compressed.

If any required condition is not satisfied, the response is returned normally.

## Opt-In Compression

The main principle of this library is:

> **Compression is opt-in.**

An endpoint is not compressed simply because the library is included in the application.

For example:

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping
    public List<Order> getOrders() {
        return orderService.findAll();
    }
}
```

This endpoint does **not** use compression because it does not have `@Compress`.

To enable compression:

```java
@Compress
@GetMapping
public List<Order> getOrders() {
    return orderService.findAll();
}
```

## Controller vs Endpoint Compression

Choose controller-level compression when most or all endpoints in a controller should support compression:

```java
@Compress
@RestController
public class ProductController {
    
    // endpoints
}
```

Choose endpoint-level compression when only selected endpoints should support compression:

```java
@RestController
public class ProductController {

    @Compress
    @GetMapping("/products")
    public List<Product> products() {
        return productService.findAll();
    }
}
```

### Recommended Approach

Use endpoint-level compression when you need precise control.

Use controller-level compression when the majority of endpoints in a controller return large responses and should be compressed.

## Example

A typical application might look like this:

```java
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Compress(minResponseSize = 1024)
    @GetMapping
    public List<Report> getReports() {
        return reportService.findAll();
    }

    @GetMapping("/{id}")
    public Report getReport(@PathVariable Long id) {
        return reportService.findById(id);
    }
}
```

Here:

* `GET /api/reports` is eligible for compression.
* `GET /api/reports/{id}` is not compressed.
* The report list must reach `1024` bytes before compression is applied.
* The compression format is selected according to the client's `Accept-Encoding` header.

## Why Use Selective Compression?

Global compression is convenient, but it may not be appropriate for every endpoint.

Selective compression allows you to target endpoints where compression provides the most benefit, such as:

* Large JSON responses
* Large report responses
* Data-heavy REST APIs
* Large lists or collections
* Endpoints returning repetitive JSON data

This can help avoid unnecessary compression overhead for small responses.

## Summary

Spring Boot Selective Compression provides a simple way to add HTTP response compression without enabling compression globally.

The basic usage is:

```java
@Compress
@GetMapping("/large-data")
public List<Data> getLargeData() {
    return service.getLargeData();
}
```

The library automatically handles:

* Whether the endpoint is eligible for compression
* Minimum response size
* Client `Accept-Encoding`
* Brotli support
* GZIP support
* Returning the original response when compression is not appropriate

The core idea is:

> **Add `@Compress` where you want compression. Everything else remains unchanged.**

## License

This project is licensed under the **Apache License 2.0**.

