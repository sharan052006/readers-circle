package com.readerscircle.store;

import com.readerscircle.store.dto.AddressDto;
import com.readerscircle.store.dto.AddressRequest;
import com.readerscircle.store.dto.BookDto;
import com.readerscircle.store.dto.CartItemDto;
import com.readerscircle.store.dto.CartLineRequest;
import com.readerscircle.store.dto.OrderDto;
import com.readerscircle.store.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reader storefront: Discover Books, cart, addresses, checkout and my orders.
 */
@RestController
@PreAuthorize("isAuthenticated()")
public class StoreController {

  private final StoreBookService bookService;
  private final StoreOrderService orderService;

  public StoreController(StoreBookService bookService, StoreOrderService orderService) {
    this.bookService = bookService;
    this.orderService = orderService;
  }

  @GetMapping("/api/books")
  public BookDto.Page<BookDto> books(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String availability,
      @RequestParam(required = false) Integer minPrice,
      @RequestParam(required = false) Integer maxPrice,
      @RequestParam(required = false, defaultValue = "latest") String sort,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "12") int size) {
    return bookService.readerList(query, availability, minPrice, maxPrice, sort, page, size);
  }

  @GetMapping("/api/books/{id}")
  public BookDto bookDetail(@PathVariable UUID id) {
    return bookService.getById(id);
  }

  @GetMapping("/api/cart")
  public CartItemDto.CartDto cart(Authentication authentication) {
    return orderService.cart(getUserId(authentication));
  }

  @PostMapping("/api/cart/items")
  @ResponseStatus(HttpStatus.CREATED)
  public CartItemDto.CartDto addToCart(
      @Valid @RequestBody CartLineRequest request, Authentication authentication) {
    int qty = request.qty() == null ? 1 : request.qty();
    return orderService.addToCart(getUserId(authentication), request.bookId(), qty);
  }

  @PatchMapping("/api/cart/items/{itemId}")
  public CartItemDto.CartDto setQty(
      @PathVariable UUID itemId, @RequestBody Map<String, Integer> body, Authentication authentication) {
    int qty = body == null || body.get("qty") == null ? 1 : body.get("qty");
    return orderService.setCartQty(getUserId(authentication), itemId, qty);
  }

  @DeleteMapping("/api/cart/items/{itemId}")
  public CartItemDto.CartDto removeItem(@PathVariable UUID itemId, Authentication authentication) {
    return orderService.removeFromCart(getUserId(authentication), itemId);
  }

  @GetMapping("/api/addresses")
  public List<AddressDto> addresses(Authentication authentication) {
    return orderService.myAddresses(getUserId(authentication));
  }

  @PostMapping("/api/addresses")
  @ResponseStatus(HttpStatus.CREATED)
  public AddressDto addAddress(
      @Valid @RequestBody AddressRequest request, Authentication authentication) {
    return orderService.addAddress(getUserId(authentication), request);
  }

  @PatchMapping("/api/addresses/{id}")
  public AddressDto updateAddress(
      @PathVariable UUID id, @Valid @RequestBody AddressRequest request, Authentication authentication) {
    return orderService.updateAddress(getUserId(authentication), id, request);
  }

  @DeleteMapping("/api/addresses/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAddress(@PathVariable UUID id, Authentication authentication) {
    orderService.deleteAddress(getUserId(authentication), id);
  }

  @PostMapping("/api/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public OrderDto placeOrder(
      @Valid @RequestBody PlaceOrderRequest request, Authentication authentication) {
    return orderService.placeOrder(getUserId(authentication), request);
  }

  @GetMapping("/api/orders")
  public List<OrderDto> myOrders(Authentication authentication) {
    return orderService.myOrders(getUserId(authentication));
  }

  @GetMapping("/api/orders/{id}")
  public OrderDto myOrderDetail(@PathVariable UUID id, Authentication authentication) {
    return orderService.myOrderDetail(getUserId(authentication), id);
  }

  @PostMapping("/api/orders/{id}/cancel")
  public OrderDto cancelOrder(@PathVariable UUID id, Authentication authentication) {
    return orderService.cancelOrder(getUserId(authentication), id);
  }

  private UUID getUserId(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return UUID.fromString(auth.getName());
  }
}
