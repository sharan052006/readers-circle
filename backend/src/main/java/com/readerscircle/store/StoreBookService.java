package com.readerscircle.store;

import com.readerscircle.store.dto.BookDto;
import com.readerscircle.store.dto.CreateBookRequest;
import com.readerscircle.store.dto.UpdateBookRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sellable book catalog: admin CRUD + stats, reader storefront list.
 * Response shapes are camelCase to match the Books UI contract.
 */
@Service
public class StoreBookService {

  /** Stock at or below this (but above zero) renders as "Low Stock". */
  public static final int LOW_STOCK_THRESHOLD = 5;

  private final StoreBookRepository books;
  private final StoreCartItemRepository cartItems;
  private final StoreOrderRepository orders;

  public StoreBookService(
      StoreBookRepository books,
      StoreCartItemRepository cartItems,
      StoreOrderRepository orders) {
    this.books = books;
    this.cartItems = cartItems;
    this.orders = orders;
  }

  @Transactional(readOnly = true)
  public BookDto.Page<BookDto> adminList(
      String query, String status, String sort, int page, int size) {
    List<StoreBook> all = books.findAllByOrderByCreatedAtDesc();
    String q = query == null ? "" : query.trim().toLowerCase();
    String st = status == null ? "" : status.trim().toUpperCase();
    BookStatus want = null;
    if (!st.isEmpty() && !"ALL".equals(st)) {
      try {
        want = BookStatus.valueOf(st);
      } catch (IllegalArgumentException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
      }
    }
    List<StoreBook> filtered = new ArrayList<>();
    for (StoreBook b : all) {
      if (want != null && b.getStatus() != want) continue;
      if (!q.isEmpty()) {
        String hay = ((b.getTitle() == null ? "" : b.getTitle()) + " "
            + (b.getAuthor() == null ? "" : b.getAuthor())).toLowerCase();
        if (!hay.contains(q)) continue;
      }
      filtered.add(b);
    }
    sortBooks(filtered, sort);
    return pageOf(filtered, page, size);
  }

  @Transactional(readOnly = true)
  public BookDto.Page<BookDto> readerList(
      String query, String availability, Integer minPrice, Integer maxPrice, String sort,
      int page, int size) {
    List<StoreBook> all = books.findAllByOrderByCreatedAtDesc();
    String q = query == null ? "" : query.trim().toLowerCase();
    String av = availability == null ? "" : availability.trim().toLowerCase();
    List<StoreBook> filtered = new ArrayList<>();
    for (StoreBook b : all) {
      if ("in_stock".equals(av) && b.getStockQty() <= 0) continue;
      if ("out_of_stock".equals(av) && b.getStockQty() > 0) continue;
      if (minPrice != null && b.getPriceRupees() < minPrice) continue;
      if (maxPrice != null && b.getPriceRupees() > maxPrice) continue;
      if (!q.isEmpty()) {
        String hay = ((b.getTitle() == null ? "" : b.getTitle()) + " "
            + (b.getAuthor() == null ? "" : b.getAuthor()) + " "
            + (b.getDescription() == null ? "" : b.getDescription())).toLowerCase();
        if (!hay.contains(q)) continue;
      }
      filtered.add(b);
    }
    sortBooks(filtered, sort);
    return pageOf(filtered, page, size);
  }

  @Transactional(readOnly = true)
  public BookDto getById(UUID id) {
    StoreBook b = books.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    return BookDto.of(b, LOW_STOCK_THRESHOLD);
  }

  @Transactional
  public BookDto create(CreateBookRequest req) {
    String title = req.title().trim();
    String author = req.author().trim();
    if (title.isEmpty() || author.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book title and author are required");
    }
    int price = req.price() == null ? 0 : Math.max(0, req.price());
    int stock = req.stockQty() == null ? 0 : Math.max(0, req.stockQty());
    StoreBook b = new StoreBook();
    b.setTitle(title);
    b.setAuthor(author);
    b.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
    b.setCoverImageUrl(req.coverImageUrl() == null || req.coverImageUrl().isBlank() ? null : req.coverImageUrl().trim());
    b.setPriceRupees(price);
    b.setStockQty(stock);
    b.setStatus(parseStatus(req.status(), stock));
    books.save(b);
    return BookDto.of(b, LOW_STOCK_THRESHOLD);
  }

  @Transactional
  public BookDto update(UUID id, UpdateBookRequest req) {
    StoreBook b = books.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    if (req.title() != null && !req.title().isBlank()) b.setTitle(req.title().trim());
    if (req.author() != null && !req.author().isBlank()) b.setAuthor(req.author().trim());
    if (req.description() != null) b.setDescription(req.description().isBlank() ? null : req.description().trim());
    if (req.coverImageUrl() != null) {
      b.setCoverImageUrl(req.coverImageUrl().isBlank() ? null : req.coverImageUrl().trim());
    }
    if (req.price() != null) b.setPriceRupees(Math.max(0, req.price()));
    if (req.stockQty() != null) b.setStockQty(Math.max(0, req.stockQty()));
    if (req.status() != null && !req.status().isBlank()) {
      b.setStatus(parseStatus(req.status(), b.getStockQty()));
    } else if (req.stockQty() != null) {
      // Keep the pill truthful when only stock changes: zero stock reads Out of Stock.
      b.setStatus(b.getStockQty() > 0 ? BookStatus.AVAILABLE : BookStatus.OUT_OF_STOCK);
    }
    books.save(b);
    return BookDto.of(b, LOW_STOCK_THRESHOLD);
  }

  @Transactional
  public void delete(UUID id) {
    StoreBook b = books.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    cartItems.deleteByBookId(b.getId());
    books.delete(b);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> stats() {
    List<StoreBook> all = books.findAll();
    long available = all.stream().filter(b -> b.getStatus() == BookStatus.AVAILABLE).count();
    long outOfStock = all.stream().filter(b -> b.getStatus() == BookStatus.OUT_OF_STOCK).count();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("totalBooks", all.size());
    out.put("available", available);
    out.put("outOfStock", outOfStock);
    out.put("totalOrders", orders.count());
    return out;
  }

  private BookStatus parseStatus(String raw, int stock) {
    if (raw == null || raw.isBlank()) {
      return stock > 0 ? BookStatus.AVAILABLE : BookStatus.OUT_OF_STOCK;
    }
    try {
      return BookStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + raw);
    }
  }

  private void sortBooks(List<StoreBook> list, String sort) {
    String s = sort == null ? "" : sort.trim().toLowerCase();
    switch (s) {
      case "price_asc" -> list.sort(Comparator.comparingInt(StoreBook::getPriceRupees));
      case "price_desc" -> list.sort(
          Comparator.comparingInt(StoreBook::getPriceRupees).reversed());
      case "title_asc", "title" -> list.sort(
          Comparator.comparing(b -> b.getTitle() == null ? "" : b.getTitle().toLowerCase()));
      default -> list.sort(
          Comparator.comparing(StoreBook::getCreatedAt,
              Comparator.nullsLast(Comparator.reverseOrder())));
    }
  }

  private BookDto.Page<BookDto> pageOf(List<StoreBook> filtered, int page, int size) {
    int sz = Math.max(1, Math.min(50, size));
    int pg = Math.max(0, page);
    int total = filtered.size();
    int pages = (int) Math.ceil(total / (double) sz);
    int from = Math.min(pg * sz, total);
    int to = Math.min(from + sz, total);
    List<BookDto> content = new ArrayList<>();
    for (StoreBook b : filtered.subList(from, to)) {
      content.add(BookDto.of(b, LOW_STOCK_THRESHOLD));
    }
    return new BookDto.Page<>(content, pg, sz, total, pages);
  }
}
