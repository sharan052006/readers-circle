package com.readerscircle.store;

import com.readerscircle.auth.User;
import com.readerscircle.auth.UserRepository;
import com.readerscircle.store.dto.AddressDto;
import com.readerscircle.store.dto.AddressRequest;
import com.readerscircle.store.dto.BookDto;
import com.readerscircle.store.dto.CartItemDto;
import com.readerscircle.store.dto.OrderDto;
import com.readerscircle.store.dto.PlaceOrderRequest;
import com.readerscircle.store.dto.UpdateOrderStatusRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reader cart + addresses + checkout, and admin order management.
 * Stock is reserved atomically at checkout; order numbers read RC-2026-0001.
 */
@Service
public class StoreOrderService {

  private final StoreBookRepository books;
  private final StoreCartItemRepository cartItems;
  private final StoreAddressRepository addresses;
  private final StoreOrderRepository orders;
  private final StoreOrderItemRepository orderItems;
  private final StoreOrderCounterRepository counters;
  private final UserRepository users;

  public StoreOrderService(
      StoreBookRepository books,
      StoreCartItemRepository cartItems,
      StoreAddressRepository addresses,
      StoreOrderRepository orders,
      StoreOrderItemRepository orderItems,
      StoreOrderCounterRepository counters,
      UserRepository users) {
    this.books = books;
    this.cartItems = cartItems;
    this.addresses = addresses;
    this.orders = orders;
    this.orderItems = orderItems;
    this.counters = counters;
    this.users = users;
  }

  // ---- cart ----

  @Transactional(readOnly = true)
  public CartItemDto.CartDto cart(UUID userId) {
    List<CartItemDto> out = new ArrayList<>();
    int subtotal = 0;
    for (StoreCartItem ci : cartItems.findByUserIdOrderByCreatedAtAsc(userId)) {
      StoreBook b = books.findById(ci.getBookId()).orElse(null);
      if (b == null) continue;
      int line = b.getPriceRupees() * ci.getQty();
      subtotal += line;
      out.add(new CartItemDto(
          ci.getId().toString(),
          b.getId().toString(),
          b.getTitle(),
          b.getAuthor(),
          b.getCoverImageUrl(),
          b.getPriceRupees(),
          b.getStockQty(),
          b.getStatus() == null ? "AVAILABLE" : b.getStatus().name(),
          ci.getQty(),
          line));
    }
    return new CartItemDto.CartDto(out, subtotal, out.size());
  }

  @Transactional
  public CartItemDto.CartDto addToCart(UUID userId, UUID bookId, int qty) {
    if (qty < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be at least 1");
    StoreBook b = books.findById(bookId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    if (b.getStockQty() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This book is out of stock");
    }
    StoreCartItem existing = cartItems.findByUserIdAndBookId(userId, bookId).orElse(null);
    int want = qty + (existing == null ? 0 : existing.getQty());
    if (want > b.getStockQty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Only " + b.getStockQty() + " in stock");
    }
    if (existing == null) {
      cartItems.save(new StoreCartItem(userId, bookId, qty));
    } else {
      existing.setQty(want);
      cartItems.save(existing);
    }
    return cart(userId);
  }

  @Transactional
  public CartItemDto.CartDto setCartQty(UUID userId, UUID itemId, int qty) {
    if (qty < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be at least 1");
    StoreCartItem ci = cartItems.findById(itemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));
    if (!ci.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
    }
    StoreBook b = books.findById(ci.getBookId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    if (qty > b.getStockQty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Only " + b.getStockQty() + " in stock");
    }
    ci.setQty(qty);
    cartItems.save(ci);
    return cart(userId);
  }

  @Transactional
  public CartItemDto.CartDto removeFromCart(UUID userId, UUID itemId) {
    StoreCartItem ci = cartItems.findById(itemId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));
    if (!ci.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
    }
    cartItems.delete(ci);
    return cart(userId);
  }

  // ---- addresses ----

  @Transactional(readOnly = true)
  public List<AddressDto> myAddresses(UUID userId) {
    List<AddressDto> out = new ArrayList<>();
    for (StoreAddress a : addresses.findByUserIdOrderByCreatedAtAsc(userId)) {
      out.add(AddressDto.of(a));
    }
    return out;
  }

  @Transactional
  public AddressDto addAddress(UUID userId, AddressRequest req) {
    StoreAddress a = new StoreAddress();
    a.setUserId(userId);
    applyAddress(a, req);
    boolean makeDefault = req.isDefault() == null
        ? addresses.findByUserIdOrderByCreatedAtAsc(userId).isEmpty()
        : req.isDefault();
    a.setDefault(makeDefault);
    addresses.save(a);
    if (makeDefault) clearOtherDefaults(userId, a.getId());
    return AddressDto.of(a);
  }

  @Transactional
  public AddressDto updateAddress(UUID userId, UUID id, AddressRequest req) {
    StoreAddress a = addresses.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    if (!a.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
    }
    applyAddress(a, req);
    if (req.isDefault() != null) a.setDefault(req.isDefault());
    addresses.save(a);
    if (a.isDefault()) clearOtherDefaults(userId, a.getId());
    return AddressDto.of(a);
  }

  @Transactional
  public void deleteAddress(UUID userId, UUID id) {
    StoreAddress a = addresses.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    if (!a.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
    }
    addresses.delete(a);
  }

  private void applyAddress(StoreAddress a, AddressRequest req) {
    if (req.label() != null && !req.label().isBlank()) a.setLabel(req.label().trim());
    a.setFullName(req.fullName().trim());
    a.setPhone(req.phone().trim());
    a.setLine1(req.line1().trim());
    a.setLine2(req.line2() == null || req.line2().isBlank() ? null : req.line2().trim());
    a.setCity(req.city().trim());
    a.setState(req.state().trim());
    a.setPincode(req.pincode().trim());
  }

  private void clearOtherDefaults(UUID userId, UUID keepId) {
    for (StoreAddress other : addresses.findByUserIdOrderByCreatedAtAsc(userId)) {
      if (!other.getId().equals(keepId) && other.isDefault()) {
        other.setDefault(false);
        addresses.save(other);
      }
    }
  }

  // ---- checkout ----

  @Transactional
  public OrderDto placeOrder(UUID userId, PlaceOrderRequest req) {
    StoreAddress a = addresses.findById(req.addressId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery address not found"));
    if (!a.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery address not found");
    }
    List<StoreCartItem> lines = cartItems.findByUserIdOrderByCreatedAtAsc(userId);
    if (lines.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your cart is empty");
    }
    String method = req.paymentMethod() == null ? "UPI" : req.paymentMethod().trim().toUpperCase();
    if (!List.of("UPI", "ONLINE", "CARD", "COD").contains(method)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown payment method: " + req.paymentMethod());
    }

    // Reserve stock atomically per line before creating anything.
    List<StoreBook> reserved = new ArrayList<>();
    for (StoreCartItem ci : lines) {
      StoreBook b = books.findById(ci.getBookId())
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "A book in your cart is no longer available"));
      if (books.decrementStock(b.getId(), ci.getQty()) == 0) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Not enough stock for " + b.getTitle());
      }
      StoreBook fresh = books.findById(b.getId()).orElseThrow();
      syncStatus(fresh);
      reserved.add(fresh);
    }

    int subtotal = 0;
    for (int i = 0; i < lines.size(); i++) {
      subtotal += reserved.get(i).getPriceRupees() * lines.get(i).getQty();
    }
    int shipping = 0; // free shipping on all orders
    Instant now = Instant.now();

    StoreOrder o = new StoreOrder();
    o.setOrderNumber(nextOrderNumber(now));
    o.setUserId(userId);
    o.setStatus(OrderStatus.PENDING);
    o.setSubtotalRupees(subtotal);
    o.setShippingRupees(shipping);
    o.setTotalRupees(subtotal + shipping);
    o.setPaymentMethod(method);
    if ("COD".equals(method)) {
      o.setPaymentStatus("PENDING");
    } else {
      // Mock gateway: online payments confirm instantly with a generated reference.
      o.setPaymentStatus("PAID");
      o.setTransactionId("UPI" + String.format("%09d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 1_000_000_000L));
      o.setPaidAt(now);
    }
    o.setShipName(a.getFullName());
    o.setShipPhone(a.getPhone());
    o.setShipLine1(a.getLine1());
    o.setShipLine2(a.getLine2());
    o.setShipCity(a.getCity());
    o.setShipState(a.getState());
    o.setShipPincode(a.getPincode());
    o.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
    o.setPendingAt(now);
    orders.save(o);

    for (int i = 0; i < lines.size(); i++) {
      StoreBook b = reserved.get(i);
      StoreCartItem ci = lines.get(i);
      StoreOrderItem item = new StoreOrderItem();
      item.setOrderId(o.getId());
      item.setBookId(b.getId());
      item.setTitle(b.getTitle());
      item.setAuthor(b.getAuthor());
      item.setCoverImageUrl(b.getCoverImageUrl());
      item.setPriceRupees(b.getPriceRupees());
      item.setQty(ci.getQty());
      item.setLineTotal(b.getPriceRupees() * ci.getQty());
      orderItems.save(item);
    }
    cartItems.deleteByUserId(userId);
    return toDto(o, orderItems.findByOrderId(o.getId()));
  }

  private String nextOrderNumber(Instant now) {
    int year = now.atZone(ZoneId.of("Asia/Kolkata")).getYear();
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        StoreOrderCounter c = counters.findByYr(year).orElse(null);
        if (c == null) {
          c = new StoreOrderCounter(year, 2);
          counters.saveAndFlush(c);
          return formatOrderNumber(year, 1);
        }
        int n = c.getNextNumber();
        c.setNextNumber(n + 1);
        counters.saveAndFlush(c);
        return formatOrderNumber(year, n);
      } catch (DataIntegrityViolationException e) {
        counters.flush();
      }
    }
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Could not generate order number, please retry");
  }

  private String formatOrderNumber(int year, int n) {
    return "RC-" + year + "-" + String.format("%04d", n);
  }

  // ---- reader order views ----

  @Transactional(readOnly = true)
  public List<OrderDto> myOrders(UUID userId) {
    List<OrderDto> out = new ArrayList<>();
    for (StoreOrder o : orders.findByUserIdOrderByCreatedAtDesc(userId)) {
      out.add(toDto(o, orderItems.findByOrderId(o.getId())));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public OrderDto myOrderDetail(UUID userId, UUID id) {
    StoreOrder o = orders.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!o.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    }
    return toDto(o, orderItems.findByOrderId(o.getId()));
  }

  @Transactional
  public OrderDto cancelOrder(UUID userId, UUID id) {
    StoreOrder o = orders.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!o.getUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    }
    if (o.getStatus() != OrderStatus.PENDING) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending orders can be cancelled");
    }
    // Release reserved stock.
    for (StoreOrderItem item : orderItems.findByOrderId(o.getId())) {
      if (item.getBookId() != null) {
        books.findById(item.getBookId()).ifPresent(b -> {
          b.setStockQty(b.getStockQty() + item.getQty());
          books.save(b);
          syncStatus(b);
        });
      }
    }
    o.setStatus(OrderStatus.CANCELLED);
    o.setCancelledAt(Instant.now());
    orders.save(o);
    return toDto(o, orderItems.findByOrderId(o.getId()));
  }

  // ---- admin order management ----

  @Transactional(readOnly = true)
  public BookDto.Page<OrderDto> adminList(String query, String status, String sort, int page, int size) {
    List<StoreOrder> all = orders.findAllByOrderByCreatedAtDesc();
    String q = query == null ? "" : query.trim().toLowerCase();
    String st = status == null ? "" : status.trim().toUpperCase();
    OrderStatus want = null;
    if (!st.isEmpty() && !"ALL".equals(st)) {
      try {
        want = OrderStatus.valueOf(st);
      } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
      }
    }
    List<StoreOrder> filtered = new ArrayList<>();
    for (StoreOrder o : all) {
      if (want != null && o.getStatus() != want) continue;
      if (!q.isEmpty()) {
        User u = users.findById(o.getUserId()).orElse(null);
        String readerName = u == null ? "" : displayName(u);
        String readerEmail = u == null || u.getEmail() == null ? "" : u.getEmail();
        List<StoreOrderItem> items = orderItems.findByOrderId(o.getId());
        StringBuilder hay = new StringBuilder()
            .append(o.getOrderNumber() == null ? "" : o.getOrderNumber()).append(' ')
            .append(readerName).append(' ').append(readerEmail).append(' ');
        for (StoreOrderItem item : items) {
          hay.append(item.getTitle() == null ? "" : item.getTitle()).append(' ');
        }
        if (!hay.toString().toLowerCase().contains(q)) continue;
      }
      filtered.add(o);
    }
    String s = sort == null ? "" : sort.trim().toLowerCase();
    if ("oldest".equals(s)) {
      filtered.sort(Comparator.comparing(StoreOrder::getCreatedAt,
          Comparator.nullsLast(Comparator.naturalOrder())));
    }
    List<OrderDto> dtos = new ArrayList<>();
    for (StoreOrder o : filtered) {
      dtos.add(toDto(o, orderItems.findByOrderId(o.getId())));
    }
    int sz = Math.max(1, Math.min(50, size));
    int pg = Math.max(0, page);
    int total = dtos.size();
    int pages = (int) Math.ceil(total / (double) sz);
    int from = Math.min(pg * sz, total);
    int to = Math.min(from + sz, total);
    return new BookDto.Page<>(dtos.subList(from, to), pg, sz, total, pages);
  }

  @Transactional(readOnly = true)
  public OrderDto adminDetail(UUID id) {
    StoreOrder o = orders.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    return toDto(o, orderItems.findByOrderId(o.getId()));
  }

  @Transactional
  public OrderDto updateStatus(UUID id, UpdateOrderStatusRequest req) {
    StoreOrder o = orders.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    OrderStatus next;
    try {
      next = OrderStatus.valueOf(req.status().trim().toUpperCase());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + req.status());
    }
    if (o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.DELIVERED) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Order is already " + o.getStatus().name().toLowerCase());
    }
    Instant now = Instant.now();
    switch (next) {
      case CONFIRMED -> {
        if (o.getConfirmedAt() == null) o.setConfirmedAt(now);
      }
      case SHIPPED -> {
        if (o.getConfirmedAt() == null) o.setConfirmedAt(now);
        if (o.getShippedAt() == null) o.setShippedAt(now);
      }
      case DELIVERED -> {
        if (o.getConfirmedAt() == null) o.setConfirmedAt(now);
        if (o.getShippedAt() == null) o.setShippedAt(now);
        if (o.getDeliveredAt() == null) o.setDeliveredAt(now);
        if (!"PAID".equals(o.getPaymentStatus())) {
          o.setPaymentStatus("PAID");
          o.setPaidAt(now);
          if (o.getTransactionId() == null) {
            o.setTransactionId("UPI" + String.format("%09d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 1_000_000_000L));
          }
        }
      }
      case CANCELLED -> {
        if (o.getCancelledAt() == null) o.setCancelledAt(now);
        for (StoreOrderItem item : orderItems.findByOrderId(o.getId())) {
          if (item.getBookId() != null) {
            books.findById(item.getBookId()).ifPresent(b -> {
              b.setStockQty(b.getStockQty() + item.getQty());
              books.save(b);
              syncStatus(b);
            });
          }
        }
      }
      default -> {
        // PENDING: no-op restamp
      }
    }
    o.setStatus(next);
    if (req.note() != null) o.setNote(req.note().isBlank() ? null : req.note().trim());
    if (req.trackingId() != null) {
      o.setTrackingId(req.trackingId().isBlank() ? null : req.trackingId().trim());
    }
    orders.save(o);
    return toDto(o, orderItems.findByOrderId(o.getId()));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> orderStats() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("totalOrders", orders.count());
    out.put("pendingOrders", orders.countByStatus(OrderStatus.PENDING));
    out.put("shippedOrders", orders.countByStatus(OrderStatus.SHIPPED));
    out.put("deliveredOrders", orders.countByStatus(OrderStatus.DELIVERED));
    return out;
  }

  private OrderDto toDto(StoreOrder o, List<StoreOrderItem> items) {
    User u = users.findById(o.getUserId()).orElse(null);
    String name = u == null ? "Reader" : displayName(u);
    String email = u == null || u.getEmail() == null ? "" : u.getEmail();
    List<OrderDto.OrderLineDto> lines = new ArrayList<>();
    for (StoreOrderItem item : items) {
      lines.add(new OrderDto.OrderLineDto(
          item.getBookId() == null ? null : item.getBookId().toString(),
          item.getTitle(),
          item.getAuthor(),
          item.getCoverImageUrl(),
          item.getPriceRupees(),
          item.getQty(),
          item.getLineTotal()));
    }
    AddressDto address = new AddressDto(
        null, "", o.getShipName(), o.getShipPhone(), o.getShipLine1(), o.getShipLine2(),
        o.getShipCity(), o.getShipState(), o.getShipPincode(), false);
    List<OrderDto.TimelineEntry> timeline = new ArrayList<>();
    timeline.add(new OrderDto.TimelineEntry(
        "PENDING", "Order Placed", "Order received from reader.", iso(o.getPendingAt()), true));
    timeline.add(new OrderDto.TimelineEntry(
        "CONFIRMED", "Order Confirmed", "Order confirmed by admin.", iso(o.getConfirmedAt()),
        o.getConfirmedAt() != null));
    String shipDetail = "Order shipped" + (o.getTrackingId() == null ? "." : " via courier. Tracking ID: " + o.getTrackingId());
    timeline.add(new OrderDto.TimelineEntry(
        "SHIPPED", "Shipped", shipDetail, iso(o.getShippedAt()), o.getShippedAt() != null));
    timeline.add(new OrderDto.TimelineEntry(
        "DELIVERED", "Delivered",
        o.getDeliveredAt() == null ? "-" : "Order delivered.",
        iso(o.getDeliveredAt()), o.getDeliveredAt() != null));
    if (o.getStatus() == OrderStatus.CANCELLED) {
      timeline.add(new OrderDto.TimelineEntry(
          "CANCELLED", "Cancelled", o.getNote() == null ? "Order cancelled." : o.getNote(),
          iso(o.getCancelledAt()), true));
    }
    return new OrderDto(
        o.getId() == null ? null : o.getId().toString(),
        o.getOrderNumber(),
        o.getStatus().name(),
        o.getSubtotalRupees(),
        o.getShippingRupees(),
        o.getTotalRupees(),
        o.getPaymentMethod(),
        o.getPaymentStatus(),
        o.getTransactionId(),
        iso(o.getPaidAt()),
        new OrderDto.ReaderInfo(name, email),
        address,
        lines,
        timeline,
        o.getNote(),
        o.getTrackingId(),
        iso(o.getCreatedAt()),
        iso(o.getCreatedAt()));
  }

  private String displayName(User u) {
    if (u.getName() != null && !u.getName().isBlank()) return u.getName();
    if (u.getEmail() != null && !u.getEmail().isBlank()) {
      int at = u.getEmail().indexOf('@');
      return at > 0 ? u.getEmail().substring(0, at) : u.getEmail();
    }
    return "Reader";
  }

  private String iso(Instant i) {
    return i == null ? null : i.toString();
  }

  /**
   * Keep the availability pill truthful across stock crossings: hitting zero
   * reads Out of Stock, climbing back above zero restores Available.
   */
  private void syncStatus(StoreBook b) {
    if (b.getStockQty() <= 0 && b.getStatus() != BookStatus.OUT_OF_STOCK) {
      b.setStatus(BookStatus.OUT_OF_STOCK);
      books.save(b);
    } else if (b.getStockQty() > 0 && b.getStatus() == BookStatus.OUT_OF_STOCK) {
      b.setStatus(BookStatus.AVAILABLE);
      books.save(b);
    }
  }
}
