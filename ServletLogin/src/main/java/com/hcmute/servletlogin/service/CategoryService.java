package com.hcmute.servletlogin.service;

import com.hcmute.servletlogin.dao.CategoryDAO;
import com.hcmute.servletlogin.dao.CategoryDAOImpl;
import com.hcmute.servletlogin.model.Category;

import java.util.List;


public class CategoryService {


    private CategoryDAO categoryDAO;


    public CategoryService() {

        categoryDAO =
                new CategoryDAOImpl();

    }

    public boolean existsByName(String name){

        return categoryDAO.existsByName(name);

    }

    public List<Category> getAll() {

        return categoryDAO.getAll();

    }



    public Category getById(int id) {

        return categoryDAO.getById(id);

    }



    public void insert(Category category) {

        categoryDAO.insert(category);

    }



    public void update(Category category) {

        categoryDAO.update(category);

    }



    public void delete(int id) {

        categoryDAO.delete(id);

    }


    public boolean existsByNameExceptId(String name, int id){

        return categoryDAO.existsByNameExceptId(name,id);

    }

}