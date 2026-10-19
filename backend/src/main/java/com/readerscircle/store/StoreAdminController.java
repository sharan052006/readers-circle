package com.readerscircle.store;

import com.readerscircle.store.dto.BookDto;
import com.readerscircle.store.dto.CreateBookRequest;
import com.readerscircle.store.dto.OrderDto;
import com.readerscircle.store.dto.UpdateBookRequest;
import com.readerscircle.store.dto.UpdateOrderStatusRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Books + Orders management (Manage Books / Manage Orders screens).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class StoreAdminController {

  private final StoreBookService bookService;
  private final StoreOrderService orderService;

  public StoreAdminController(StoreBookService bookService, StoreOrderService orderService) {
    this.bookService = bookService;
    this.orderService = orderService;
  }

  @GetMapping("/books")
  public BookDto.Page<BookDto> books(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String status,
      @RequestParam(required = false, defaultValue = "latest") String sort,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "10") int size) {
    return bookService.adminList(query, status, sort, page, size);
  }

  @PostMapping("/books")
  @ResponseStatus(HttpStatus.CREATED)
  public BookDto createBook(@Valid @RequestBody CreateBookRequest request) {
    return bookService.create(request);
  }

  @PatchMapping("/books/{id}")
  public BookDto updateBook(@PathVariable UUID id, @RequestBody UpdateBookRequest request) {
    return bookService.update(id, request);
  }

  @DeleteMapping("/books/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteBook(@PathVariable UUID id) {
    bookService.delete(id);
  }

  @GetMapping("/books/stats")
  public Map<String, Object> bookStats() {
    return bookService.stats();
  }

  @GetMapping("/orders")
  public BookDto.Page<OrderDto> orders(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String status,
      @RequestParam(required = false, defaultValue = "latest") String sort,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "10") int size) {
    return orderService.adminList(query, status, sort, page, size);
  }

  @GetMapping("/orders/{id}")
  public OrderDto orderDetail(@PathVariable UUID id) {
    return orderService.adminDetail(id);
  }

  @PatchMapping("/orders/{id}/status")
  public OrderDto updateOrderStatus(
      @PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
    return orderService.updateStatus(id, request);
  }

  @GetMapping("/orders/stats")
  public Map<String, Object> orderStats() {
    return orderService.orderStats();
  }
}
