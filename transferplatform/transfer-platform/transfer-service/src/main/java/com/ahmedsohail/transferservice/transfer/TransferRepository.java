package com.ahmedsohail.transferservice.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA repository for {@link Transfer} rows. */
public interface TransferRepository extends JpaRepository<Transfer, Long> {

    /** Returns every transfer the account took part in, newest first. */
    List<Transfer> findByFromAccountIdOrToAccountIdOrderByCreatedAtDescIdDesc(Long fromAccountId, Long toAccountId);
}
