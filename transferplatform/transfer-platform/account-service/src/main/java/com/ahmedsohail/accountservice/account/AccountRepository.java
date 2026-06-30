package com.ahmedsohail.accountservice.account;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository giving CRUD access to {@link Account} rows. */
public interface AccountRepository extends JpaRepository<Account, Long> {
}
