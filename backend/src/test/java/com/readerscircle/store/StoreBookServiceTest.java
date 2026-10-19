package com.readerscircle.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.readerscircle.store.dto.BookDto;
import com.readerscircle.store.dto.CreateBookRequest;
import com.readerscircle.store.dto.UpdateBookRequest;
import java.time.Instant;
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
class StoreBookServiceTest {

  @Mock private StoreBookRepository books;
  @Mock private StoreCartItemRepository cartItems;
  @Mock private StoreOrderRepository orders;

  private StoreBookService service;

  @BeforeEach
  void setUp() {
    service = new StoreBookService(books, cartItems, orders);
  }

  private StoreBook book(String title, int price, int stock) {
    StoreBook b = new StoreBook();
    b.setId(UUID.randomUUID());
    b.setTitle(title);
    b.setAuthor("Author");
    b.setPriceRupees(price);
    b.setStockQty(stock);
    b.setStatus(stock > 0 ? BookStatus.AVAILABLE : BookStatus.OUT_OF_STOCK);
    return b;
  }

  @Test
  void createDefaultsStatusFromStock() {
    when(books.save(any(StoreBook.class))).thenAnswer(inv -> inv.getArgument(0));

    BookDto inStock = service.create(
        new CreateBookRequest("Dune", "Herbert", null, null, 200, 5, null));
    assertThat(inStock.status()).isEqualTo("AVAILABLE");

    BookDto empty = service.create(
        new CreateBookRequest("Rare", "Anon", null, null, 100, 0, null));
    assertThat(empty.status()).isEqualTo("OUT_OF_STOCK");
  }

  @Test
  void updateStockToZeroFlipsStatus() {
    StoreBook b = book("Dune", 200, 5);
    when(books.findById(b.getId())).thenReturn(Optional.of(b));
    when(books.save(any(StoreBook.class))).thenAnswer(inv -> inv.getArgument(0));

    BookDto dto = service.update(b.getId(), new UpdateBookRequest(null, null, null, null, null, 0, null));
    assertThat(dto.stockQty()).isEqualTo(0);
    assertThat(dto.status()).isEqualTo("OUT_OF_STOCK");
  }

  @Test
  void readerListFiltersStockAndPrice() {
    StoreBook a = book("Cheap", 100, 10);
    StoreBook b = book("Pricy", 900, 10);
    StoreBook c = book("Gone", 150, 0);
    when(books.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(a, b, c));

    BookDto.Page<BookDto> page = service.readerList(null, "in_stock", 50, 500, "price_asc", 0, 12);
    assertThat(page.content()).hasSize(1);
    assertThat(page.content().get(0).title()).isEqualTo("Cheap");
  }

  @Test
  void lowStockFlagAtThreshold() {
    when(books.save(any(StoreBook.class))).thenAnswer(inv -> inv.getArgument(0));
    BookDto dto = service.create(
        new CreateBookRequest("Almost", "Anon", null, null, 50, 5, "AVAILABLE"));
    assertThat(dto.lowStock()).isTrue();
  }

  @Test
  void deleteClearsCartRows() {
    StoreBook b = book("Gone", 100, 1);
    when(books.findById(b.getId())).thenReturn(Optional.of(b));

    service.delete(b.getId());

    verify(cartItems).deleteByBookId(b.getId());
    verify(books).delete(b);
  }

  @Test
  void adminListRejectsUnknownStatus() {
    when(books.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
    assertThatThrownBy(() -> service.adminList(null, "NOPE", "latest", 0, 10))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void statsCounts() {
    when(books.findAll()).thenReturn(List.of(book("A", 10, 3), book("B", 10, 0)));
    when(orders.count()).thenReturn(7L);

    assertThat(service.stats())
        .containsEntry("totalBooks", 2)
        .containsEntry("available", 1L)
        .containsEntry("outOfStock", 1L)
        .containsEntry("totalOrders", 7L);
  }
}
