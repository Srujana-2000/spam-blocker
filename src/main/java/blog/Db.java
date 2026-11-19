package blog;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.io.*;

/** JDBC boundary: parameterized SQL, one connection per request, persistent H2 storage. */
public final class Db {
    private static String url;
    public static Path root;
    public static synchronized void init(Path home) throws Exception {
        root = home.toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("data"));
        Class.forName("org.h2.Driver");
        url = "jdbc:h2:file:" + root.resolve("data/blog").toString() + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000";
        try (Connection c = open()) {
            String schema = Files.readString(root.resolve("config/schema.sql"));
            for (String statement : schema.split(";")) if (!statement.isBlank()) c.createStatement().execute(statement);
            if (((Number)one(c,"SELECT COUNT(*) AS n FROM accounts").get("n")).intValue() == 0) seed(c);
        }
    }
    public static Connection open() throws SQLException { return DriverManager.getConnection(url,"sa",""); }
    public static PreparedStatement prepare(Connection c,String sql,Object... args) throws SQLException {
        PreparedStatement p = c.prepareStatement(sql);
        for (int i=0;i<args.length;i++) p.setObject(i+1,args[i]);
        return p;
    }
    public static List<Map<String,Object>> rows(Connection c,String sql,Object... args) throws SQLException {
        try (PreparedStatement p=prepare(c,sql,args); ResultSet rs=p.executeQuery()) {
            List<Map<String,Object>> list=new ArrayList<>();
            while(rs.next()) {
                Map<String,Object> row=new LinkedHashMap<>();
                for(int i=1;i<=rs.getMetaData().getColumnCount();i++) row.put(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT),rs.getObject(i));
                list.add(row);
            }
            return list;
        }
    }
    public static Map<String,Object> one(Connection c,String sql,Object... args) throws SQLException {
        var rows=rows(c,sql,args); return rows.isEmpty()?null:rows.getFirst();
    }
    public static int exec(Connection c,String sql,Object... args) throws SQLException {
        try(var p=prepare(c,sql,args)) { return p.executeUpdate(); }
    }
    public static void audit(Connection c,long actor,String action,String detail) throws SQLException {
        exec(c,"INSERT INTO audit_log(actor_id,action,detail) VALUES(?,?,?)",actor,action,detail);
    }
    private static void seed(Connection c) throws Exception {
        c.setAutoCommit(false);
        try {
            for(String role:List.of("ADMIN","VERIFIER","USER")) {
                String name=role.toLowerCase(Locale.ROOT);
                exec(c,"INSERT INTO accounts(username,full_name,email,password_hash,role) VALUES(?,?,?,?,?)",name,Character.toUpperCase(name.charAt(0))+name.substring(1)+" Demo",name+"@example.test",Security.hash(switch(role){case "ADMIN" -> "Admin@12345";case "VERIFIER" -> "Verifier@12345";default -> "User@12345";}),role);
            }
            exec(c,"INSERT INTO categories(name) VALUES(?)","Technology");
            exec(c,"INSERT INTO categories(name) VALUES(?)","Community");
            exec(c,"INSERT INTO categories(name) VALUES(?)","Learning");
            exec(c,"INSERT INTO forums(title,body,category_id,created_by) VALUES(?,?,1,1)","Welcome to a better conversation","A small space for big ideas. Share what you are building, ask a thoughtful question, or help someone learn something new.\n\nThis forum uses a CSV word list to hold potentially unwanted comments for a verifier. Clean comments appear immediately. Try the demo and see how moderation works.");
            exec(c,"INSERT INTO forums(title,body,category_id,created_by) VALUES(?,?,3,1)","What did you learn this week?","Every useful discovery starts with a question. Tell the community about a concept, an experiment, or a lesson that stayed with you.");
            exec(c,"INSERT INTO forums(title,body,category_id,created_by) VALUES(?,?,2,1)","A community worth coming back to","Good discussions are built together. What makes an online community feel welcoming? Share one idea for keeping conversations helpful and respectful.");
            exec(c,"INSERT INTO comments(forum_id,author_id,body,status,matched_terms) VALUES(1,3,?,'PUBLISHED','')","I like having a place to share ideas and learn from others.");
            audit(c,1,"DEMO_INITIALIZED","Created three demo accounts and three sample forums.");
            c.commit();
        } catch(Exception e) {c.rollback();throw e;} finally {c.setAutoCommit(true);}
    }
    private Db() {}
}
