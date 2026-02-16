package com.xbleey.goldpricealert.repository;

import com.xbleey.goldpricealert.model.GoldMailRecipient;

import java.util.List;
import java.util.Optional;

public interface GoldMailRecipientStore {

    List<GoldMailRecipient> findAll();

    List<GoldMailRecipient> findEnabled();

    Optional<GoldMailRecipient> findById(Long id);

    Optional<GoldMailRecipient> findByEmail(String email);

    GoldMailRecipient save(GoldMailRecipient record);

    int update(GoldMailRecipient record);

    int deleteById(Long id);
}
