package org.sqlite.encrypt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.sqlite.date.FastDateFormat;

/**
 * 测试数据库加密功能。
 * <p>使用 SQLCipher</>
 */
public class EncryptTest  {
    /**
     * -Dorg.sqlite.lib.path=.
     * -Dorg.sqlite.lib.name=sqlite_cryption_support.dll
     */
    @Test
    public void testCreateDB() {
        System.out.println("测试数据库加密功能。。。");
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:target/encrypt-0.db", "", "password");
    }

    @Test
    public void createTable() throws Exception {
        Connection conn = getConnection();
        Statement stmt = conn.createStatement();
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS sample2 "
                        + "(id INTEGER PRIMARY KEY, descr VARCHAR(40))");
        stmt.close();

        stmt = conn.createStatement();
        try {
            stmt.execute("DELETE FROM sample2");
            stmt.execute("INSERT INTO sample2 values(1, 'test content1')");
            ResultSet rs = stmt.executeQuery("SELECT * FROM sample2");
            rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        conn.close();
    }

    @Test
    public void setFloatTest() throws Exception {
        float f = 3.141597f;
        Connection conn = getConnection();

        try {
            conn.createStatement().execute("create table IF NOT EXISTS sample (data NOAFFINITY)");
            PreparedStatement prep = conn.prepareStatement("insert into sample values(?)");
            prep.setFloat(1, f);
            prep.executeUpdate();

            PreparedStatement stmt = conn.prepareStatement("select * from sample where data > ?");
            stmt.setObject(1, 3.0f);
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next());
            float f2 = rs.getFloat(1);
            assertEquals(f, f2, 0.0000001);
        } finally {
            conn.close();
        }
    }

}
