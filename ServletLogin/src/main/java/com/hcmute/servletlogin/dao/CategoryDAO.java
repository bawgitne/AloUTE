package com.hcmute.servletlogin.dao;

import com.hcmute.servletlogin.model.Category;

import java.util.List;

public interface CategoryDAO {

    List<Category> getAll();

    Category getById(int id);

    void insert(Category category);

    void update(Category category);

    void delete(int id);

    boolean existsByName(String name);

    boolean existsByNameExceptId(String name, int id);
}