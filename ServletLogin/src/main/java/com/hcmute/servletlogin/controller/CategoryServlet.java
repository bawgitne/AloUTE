package com.hcmute.servletlogin.controller;


import com.hcmute.servletlogin.model.Category;
import com.hcmute.servletlogin.service.CategoryService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

import java.io.IOException;
import java.util.List;


@WebServlet("/categories")
public class CategoryServlet extends HttpServlet {


    private CategoryService categoryService;


    @Override
    public void init() {

        categoryService =
                new CategoryService();

    }



    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {


        String action =
                request.getParameter("action");



        // EDIT
        if ("edit".equals(action)) {


            int id =
                    Integer.parseInt(
                            request.getParameter("id")
                    );


            Category category =
                    categoryService.getById(id);


            request.setAttribute(
                    "category",
                    category
            );


            request.getRequestDispatcher(
                    "category-edit.jsp"
            ).forward(
                    request,
                    response
            );



        }


        // DELETE
        else if ("delete".equals(action)) {


            int id =
                    Integer.parseInt(
                            request.getParameter("id")
                    );


            categoryService.delete(id);


            response.sendRedirect(
                    "categories"
            );



        }


        // LIST
        else {


            List<Category> list =
                    categoryService.getAll();


            request.setAttribute(
                    "categories",
                    list
            );


            request.getRequestDispatcher(
                    "category-list.jsp"
            ).forward(
                    request,
                    response
            );

        }

    }







    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {


        request.setCharacterEncoding("UTF-8");


        String action =
                request.getParameter("action");



        // =====================
        // INSERT
        // =====================

        if ("insert".equals(action)) {



            String name =
                    request.getParameter("name");


            String description =
                    request.getParameter("description");



            // Check empty

            if(name == null || name.trim().isEmpty()) {


                request.setAttribute(
                        "error",
                        "Name cannot be empty"
                );


                request.getRequestDispatcher(
                        "category-form.jsp"
                ).forward(
                        request,
                        response
                );


                return;

            }



            // Check duplicate

            if(categoryService.existsByName(
                    name.trim()
            )) {



                request.setAttribute(
                        "error",
                        "Category name already exists"
                );


                request.getRequestDispatcher(
                        "category-form.jsp"
                ).forward(
                        request,
                        response
                );


                return;

            }




            Category category =
                    new Category();


            category.setName(
                    name.trim()
            );


            category.setDescription(
                    description
            );



            categoryService.insert(category);



            response.sendRedirect(
                    "categories"
            );



        }





        // =====================
        // UPDATE
        // =====================

        else if ("update".equals(action)) {



            int id =
                    Integer.parseInt(
                            request.getParameter("id")
                    );



            String name =
                    request.getParameter("name");


            String description =
                    request.getParameter("description");



            // Check empty

            if(name == null || name.trim().isEmpty()) {


                Category category =
                        new Category();


                category.setId(id);

                category.setName(name);

                category.setDescription(description);



                request.setAttribute(
                        "category",
                        category
                );


                request.setAttribute(
                        "error",
                        "Name cannot be empty"
                );


                request.getRequestDispatcher(
                        "category-edit.jsp"
                ).forward(
                        request,
                        response
                );


                return;

            }




            // Check duplicate except itself

            if(categoryService.existsByNameExceptId(
                    name.trim(),
                    id
            )) {



                Category category =
                        new Category();


                category.setId(id);

                category.setName(name);

                category.setDescription(description);



                request.setAttribute(
                        "category",
                        category
                );


                request.setAttribute(
                        "error",
                        "Category name already exists"
                );



                request.getRequestDispatcher(
                        "category-edit.jsp"
                ).forward(
                        request,
                        response
                );


                return;

            }




            Category category =
                    new Category();


            category.setId(id);

            category.setName(
                    name.trim()
            );


            category.setDescription(
                    description
            );



            categoryService.update(category);



            response.sendRedirect(
                    "categories"
            );

        }

    }

}