package com.hcmute.servletlogin.connection;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBConnection {


    private static final String URL =
            "jdbc:sqlserver://localhost:1433;"
                    + "databaseName=ServletDB;"
                    + "encrypt=true;"
                    + "trustServerCertificate=true";


    private static final String USER = "sa";

    private static final String PASSWORD = "123";


    public static Connection getConnection() {

        try {

            Class.forName(
                    "com.microsoft.sqlserver.jdbc.SQLServerDriver"
            );


            Connection conn =
                    DriverManager.getConnection(
                            URL,
                            USER,
                            PASSWORD
                    );


            System.out.println(
                    "Database connected!"
            );


            return conn;


        } catch(Exception e) {

            e.printStackTrace();

        }


        return null;
    }
}