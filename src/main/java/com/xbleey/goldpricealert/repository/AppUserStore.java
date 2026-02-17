package com.xbleey.goldpricealert.repository;

import com.xbleey.goldpricealert.model.AppUser;

import java.util.List;
import java.util.Optional;

public interface AppUserStore {

    List<AppUser> findAll();

    Optional<AppUser> findById(Long id);

    Optional<AppUser> findByUsername(String username);

    AppUser save(AppUser user);

    int update(AppUser user);

    int deleteById(Long id);
}
