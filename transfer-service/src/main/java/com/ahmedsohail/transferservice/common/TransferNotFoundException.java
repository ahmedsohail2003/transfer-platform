package com.ahmedsohail.transferservice.common;

/** Raised when a transfer with the given id does not exist. Mapped to HTTP 404. */
public class TransferNotFoundException extends NotFoundException {
    public TransferNotFoundException(Long id) {
        super("Transfer not found: id=" + id);
    }
}
