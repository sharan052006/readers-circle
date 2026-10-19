package com.readerscircle.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.readerscircle.auth.Role;
import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.store.dto.AddressRequest;
import com.readerscircle.store.dto.OrderDto;
import com.readerscircle.store.dto.PlaceOrderRequest;
import com.readerscircle.store.dto.UpdateOrderStatusRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StoreOrderServiceTest {

  @Mock private StoreBookRepository books;
  @Mock private StoreCartItemRepository cartItems;
  @Mock private StoreAddressRepository addresses;
  @Mock private StoreOrderRepository orders;
  @Mock private StoreOrderItemRepository orderItems;
  @Mock private StoreOrderCounterRepository counters;
  @Mock private UserRepository users;

  private StoreOrderService service;
  private UUID userId;
  private StoreBook book;
  private StoreAddress address;

  @BeforeEach
  void setUp() {
    service = new StoreOrderService(books, cartItems, addresses, orders, orderItems, counters, users);
    userId = UUID.randomUUID();
    book = new StoreBook();
    book.setId(UUID.randomUUID());
    book.setTitle("Dune");
    book.setAuthor("Herbert");
    book.setPriceRupees(200);
    book.setStockQty(10);
    book.setStatus(BookStatus.AVAILABLE);
    address = new StoreAddress();
    address.setUserId(userId);
    address.setFullName("Sharan");
    address.setPhone("9876543210");
    address.setLine1("123 Anna Nagar");
    address.setCity("Chennai");
    address.setState("Tamil Nadu");
    address.setPincode("600040");
    User u = new User("Sharan", "sharan@example.com", "pw", Role.READER);
    lenient().when(users.findById(userId)).thenReturn(Optional.of(u));
  }

  @Test
  void addToCartMergesQuantity() {
    UUID bookId = book.getId();
    when(books.findById(bookId)).thenReturn(Optional.of(book));
    when(cartItems.findByUserIdAndBookId(userId, bookId)).thenReturn(Optional.empty());
    when(cartItems.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

    service.addToCart(userId, bookId, 2);

    verify(cartItems).save(any(StoreCartItem.class));
  }

  @Test
  void addToCartRejectsOverStock() {
    when(books.findById(book.getId())).thenReturn(Optional.of(book));

    assertThatThrownBy(() -> service.addToCart(userId, book.getId(), 99))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void addToCartRejectsOutOfStock() {
    book.setStockQty(0);
    when(books.findById(book.getId())).thenReturn(Optional.of(book));

    assertThatThrownBy(() -> service.addToCart(userId, book.getId(), 1))
        .isInstanceOf(ResponseStatusException.class);
  }

  private OrderDto place() {
    UUID addrId = UUID.randomUUID();
    when(addresses.findById(addrId)).thenReturn(Optional.of(address));
    StoreCartItem ci = new StoreCartItem(userId, book.getId(), 2);
    when(cartItems.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(ci));
    when(books.findById(book.getId())).thenReturn(Optional.of(book));
    when(books.decrementStock(book.getId(), 2)).thenReturn(1);
    when(counters.findByYr(any())).thenReturn(Optional.empty());
    when(counters.saveAndFlush(any(StoreOrderCounter.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(orders.save(any(StoreOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    when(orderItems.save(any(StoreOrderItem.class))).thenAnswer(inv -> inv.getArgument(0));
    when(orderItems.findByOrderId(any())).thenReturn(List.of());

    return service.placeOrder(userId, new PlaceOrderRequest(addrId, "UPI", null));
  }

  @Test
  void placeOrderCreatesNumberedOrderAndClearsCart() {
    OrderDto dto = place();

    assertThat(dto.orderNumber()).startsWith("RC-");
    assertThat(dto.status()).isEqualTo("PENDING");
    assertThat(dto.subtotal()).isEqualTo(400);
    assertThat(dto.total()).isEqualTo(400);
    assertThat(dto.paymentStatus()).isEqualTo("PAID");
    assertThat(dto.timeline()).hasSize(4);
    assertThat(dto.timeline().get(0).done()).isTrue();
    verify(cartItems).deleteByUserId(userId);
  }

  @Test
  void placeOrderEmptyCartThrows() {
    UUID addrId = UUID.randomUUID();
    when(addresses.findById(addrId)).thenReturn(Optional.of(address));
    when(cartItems.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

    assertThatThrownBy(() -> service.placeOrder(userId, new PlaceOrderRequest(addrId, "UPI", null)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void cancelPendingRestocks() {
    StoreOrder o = new StoreOrder();
    o.setUserId(userId);
    o.setStatus(OrderStatus.PENDING);
    when(orders.findById(o.getId())).thenReturn(Optional.of(o));
    StoreOrderItem item = new StoreOrderItem();
    item.setBookId(book.getId());
    item.setQty(2);
    when(orderItems.findByOrderId(any())).thenReturn(List.of(item));
    when(books.findById(book.getId())).thenReturn(Optional.of(book));
    when(orders.save(any(StoreOrder.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderDto dto = service.cancelOrder(userId, o.getId());

    assertThat(dto.status()).isEqualTo("CANCELLED");
    assertThat(book.getStockQty()).isEqualTo(12);
  }

  @Test
  void cancelShippedThrows() {
    StoreOrder o = new StoreOrder();
    o.setUserId(userId);
    o.setStatus(OrderStatus.SHIPPED);
    when(orders.findById(o.getId())).thenReturn(Optional.of(o));

    assertThatThrownBy(() -> service.cancelOrder(userId, o.getId()))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void updateStatusStampsTimeline() {
    StoreOrder o = new StoreOrder();
    o.setUserId(userId);
    o.setStatus(OrderStatus.CONFIRMED);
    when(orders.findById(o.getId())).thenReturn(Optional.of(o));
    when(orderItems.findByOrderId(any())).thenReturn(List.of());
    when(orders.save(any(StoreOrder.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderDto dto = service.updateStatus(
        o.getId(), new UpdateOrderStatusRequest("SHIPPED", "note", "IP123"));

    assertThat(dto.status()).isEqualTo("SHIPPED");
    assertThat(dto.trackingId()).isEqualTo("IP123");
    assertThat(dto.timeline().get(2).done()).isTrue();
  }

  @Test
  void firstAddressBecomesDefault() {
    when(addresses.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());
    when(addresses.save(any(StoreAddress.class))).thenAnswer(inv -> inv.getArgument(0));

    var dto = service.addAddress(userId, new AddressRequest(
        "Home", "Sharan", "9876543210", "123 Anna Nagar", null, "Chennai", "Tamil Nadu", "600040", null));

    assertThat(dto.isDefault()).isTrue();
  }
}
