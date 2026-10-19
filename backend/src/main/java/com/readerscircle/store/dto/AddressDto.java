package com.readerscircle.store.dto;

import com.readerscircle.store.StoreAddress;

/** Delivery address row for checkout. */
public record AddressDto(
    String id,
    String label,
    String fullName,
    String phone,
    String line1,
    String line2,
    String city,
    String state,
    String pincode,
    boolean isDefault) {

  public static AddressDto of(StoreAddress a) {
    return new AddressDto(
        a.getId() == null ? null : a.getId().toString(),
        a.getLabel(),
        a.getFullName(),
        a.getPhone(),
        a.getLine1(),
        a.getLine2(),
        a.getCity(),
        a.getState(),
        a.getPincode(),
        a.isDefault());
  }
}
