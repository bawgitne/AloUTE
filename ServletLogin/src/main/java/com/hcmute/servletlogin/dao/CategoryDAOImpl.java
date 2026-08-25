package com.hcmute.servletlogin.dao;

import com.hcmute.servletlogin.connection.DBConnection;
import com.hcmute.servletlogin.model.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;


public class CategoryDAOImpl implements CategoryDAO {


    @Override
    public List<Category> getAll() {

        List<Category> list = new ArrayList<>();

        String sql =
                "SELECT id, name, description FROM Category";


        try(
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ){

            while(rs.next()){

                Category category = new Category();

                category.setId(
                        rs.getInt("id")
                );

                category.setName(
                        rs.getString("name")
                );

                category.setDescription(
                        rs.getString("description")
                );


                list.add(category);
            }


        } catch(Exception e){

            e.printStackTrace();

        }


        return list;
    }


    @Override
    public Category getById(int id) {

        Category category = null;

        String sql = "SELECT * FROM Category WHERE id=?";


        try(
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ){

            ps.setInt(1, id);

            ResultSet rs = ps.executeQuery();


            if(rs.next()){

                category = new Category();

                category.setId(
                        rs.getInt("id")
                );

                category.setName(
                        rs.getString("name")
                );

                category.setDescription(
                        rs.getString("description")
                );
            }


        } catch(Exception e){

            e.printStackTrace();

        }


        return category;
    }


    @Override
    public void insert(Category category) {

        String sql =
                "INSERT INTO Category(name, description) VALUES (?,?)";


        try(
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ){

            ps.setString(
                    1,
                    category.getName()
            );

            ps.setString(
                    2,
                    category.getDescription()
            );


            ps.executeUpdate();


        } catch(Exception e){

            e.printStackTrace();

        }
    }


    @Override
    public void update(Category category) {

        String sql =
                "UPDATE Category SET name=?, description=? WHERE id=?";


        try(
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ){

            ps.setString(
                    1,
                    category.getName()
            );

            ps.setString(
                    2,
                    category.getDescription()
            );

            ps.setInt(
                    3,
                    category.getId()
            );


            ps.executeUpdate();


        } catch(Exception e){

            e.printStackTrace();

        }
    }


    @Override
    public void delete(int id) {

        String sql =
                "DELETE FROM Category WHERE id=?";


        try(
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ){

            ps.setInt(1,id);

            ps.executeUpdate();


        } catch(Exception e){

            e.printStackTrace();

        }
    }

    @Override
    public boolean existsByName(String name) {


        String sql =
                "SELECT COUNT(*) FROM Category WHERE name=?";


        try(
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(sql)
        ){

            ps.setString(1, name);


            ResultSet rs =
                    ps.executeQuery();


            if(rs.next()){

                return rs.getInt(1) > 0;

            }


        } catch(Exception e){

            e.printStackTrace();

        }


        return false;

    }

    @Override
    public boolean existsByNameExceptId(String name, int id) {


        String sql =
                "SELECT COUNT(*) FROM Category WHERE name=? AND id<>?";


        try(
                Connection conn = DBConnection.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(sql)
        ){

            ps.setString(1, name);
            ps.setInt(2, id);


            ResultSet rs =
                    ps.executeQuery();


            if(rs.next()){

                return rs.getInt(1) > 0;

            }


        }catch(Exception e){

            e.printStackTrace();

        }


        return false;
    }
}