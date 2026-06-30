package com.ahmedsohail.transferservice.transfer;

import com.ahmedsohail.transferservice.transfer.dto.TransferRequest;
import com.ahmedsohail.transferservice.transfer.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REST endpoints for sending money and reading transfer history. */
@RestController
@RequestMapping("/api")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody TransferRequest request) {
        Transfer transfer = transferService.transfer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransferResponse.from(transfer));
    }

    @GetMapping("/transfers/{id}")
    public TransferResponse getById(@PathVariable Long id) {
        return TransferResponse.from(transferService.getTransfer(id));
    }

    @GetMapping("/accounts/{accountId}/transfers")
    public List<TransferResponse> history(@PathVariable Long accountId) {
        return transferService.historyFor(accountId).stream().map(TransferResponse::from).toList();
    }
}
