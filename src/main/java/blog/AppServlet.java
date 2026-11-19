package blog;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Routes requests, checks authorization, and owns transactional business operations. */
public class AppServlet extends HttpServlet {
    private static final int BLOCK_ON=4;
    private static final Map<String,long[]> LOGIN_ATTEMPTS=new ConcurrentHashMap<>();
    @Override public void destroy() {
        try {
            var drivers=java.sql.DriverManager.getDrivers();
            while(drivers.hasMoreElements()) {
                var driver=drivers.nextElement();
                if(driver.getClass().getClassLoader()==getClass().getClassLoader())java.sql.DriverManager.deregisterDriver(driver);
            }
        }catch(SQLException e){getServletContext().log("Driver cleanup failed",e);}
    }
    @Override public void init() throws ServletException {
        try { Db.init(Path.of(Objects.requireNonNull(System.getenv("BLOG_HOME"),"Set BLOG_HOME using run.py"))); }
        catch(Exception e){throw new ServletException("Database initialization failed",e);}
    }
    @Override protected void service(HttpServletRequest req,HttpServletResponse res) throws IOException,ServletException {
        req.setCharacterEncoding("UTF-8");res.setCharacterEncoding("UTF-8");
        res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("X-Frame-Options","DENY");
        res.setHeader("Referrer-Policy","same-origin");res.setHeader("Cache-Control","no-store");
        res.setHeader("Content-Security-Policy","default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        String path=req.getPathInfo();if(path==null||path.equals("/"))path="/forums";
        if(!Set.of("GET","POST").contains(req.getMethod())){res.sendError(405);return;}
        HttpSession session=req.getSession();
        if(session.getAttribute("csrf")==null)session.setAttribute("csrf",Security.token());
        req.setAttribute("csrf",session.getAttribute("csrf"));
        Object flash=session.getAttribute("flash");session.removeAttribute("flash");req.setAttribute("flash",flash);
        try(Connection c=Db.open()) {
            Map<String,Object> me=null;
            if(session.getAttribute("uid")!=null) {
                me=Db.one(c,"SELECT * FROM accounts WHERE id=?",session.getAttribute("uid"));
                if(me==null||!Boolean.TRUE.equals(me.get("active"))||Boolean.TRUE.equals(me.get("blocked"))) {
                    session.removeAttribute("uid");flash(req,"Your account is blocked or inactive. Contact a verifier.");redirect(res,"/login");return;
                }
            }
            req.setAttribute("me",me);
            boolean post=req.getMethod().equals("POST");
            if(post&&Objects.toString(req.getContentType(),"").toLowerCase(Locale.ROOT).startsWith("multipart/form-data"))req.getParts();
            if(post&&!Security.equal(req.getParameter("csrf"),(String)session.getAttribute("csrf"))){error(req,res,403,"The form expired. Reload the page and try again.");return;}
            if(path.equals("/logout")&&post){session.invalidate();redirect(res,"/login");return;}
            if(Set.of("/login","/register").contains(path)) {
                if(post) {
                    if(path.equals("/login"))login(c,req,res);else register(c,req,res,false);
                } else {req.setAttribute("page",path.substring(1));render(req,res);}
                return;
            }
            if(path.equals("/about")||path.equals("/contact")) {
                if(post){res.sendError(405);return;}
                req.setAttribute("page",path.substring(1));render(req,res);return;
            }
            if(path.equals("/forums")&&!post) {
                String q=optional(req,"q",100);String category=optional(req,"category",30);
                String sql="SELECT f.id,f.title,f.body,f.created_at,f.image_type,t.name AS category,(SELECT COUNT(*) FROM comments c WHERE c.forum_id=f.id AND c.status='PUBLISHED') AS comment_count FROM forums f JOIN categories t ON t.id=f.category_id WHERE f.archived=FALSE";
                List<Object> params=new ArrayList<>();
                if(!q.isBlank()){sql+=" AND (LOWER(f.title) LIKE ? ESCAPE '!' OR LOWER(f.body) LIKE ? ESCAPE '!')";String search="%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";params.add(search);params.add(search);}
                if(!category.isBlank()){sql+=" AND f.category_id=?";params.add(parseId(category));}
                req.setAttribute("forums",Db.rows(c,sql+" ORDER BY f.id DESC",params.toArray()));
                req.setAttribute("categories",Db.rows(c,"SELECT * FROM categories ORDER BY name"));req.setAttribute("page","forums");render(req,res);return;
            }
            if(path.equals("/forum")&&!post) {
                long id=id(req);var forum=Db.one(c,"SELECT f.*,t.name AS category FROM forums f JOIN categories t ON t.id=f.category_id WHERE f.id=? AND f.archived=FALSE",id);
                if(forum==null){error(req,res,404,"This forum is unavailable.");return;}
                req.setAttribute("forum",forum);
                req.setAttribute("comments",Db.rows(c,"SELECT c.*,a.username FROM comments c JOIN accounts a ON a.id=c.author_id WHERE c.forum_id=? AND c.status='PUBLISHED' ORDER BY c.id",id));
                if(me!=null)req.setAttribute("myComments",Db.rows(c,"SELECT * FROM comments WHERE forum_id=? AND author_id=? AND status<>'PUBLISHED' ORDER BY id DESC",id,me.get("id")));
                req.setAttribute("page","forum");render(req,res);return;
            }
            if(path.equals("/image")&&!post) {
                try(var p=Db.prepare(c,"SELECT image_bytes,image_type FROM forums WHERE id=? AND archived=FALSE",id(req));var rs=p.executeQuery()) {
                    if(!rs.next()||rs.getBytes(1)==null){res.sendError(404);return;}
                    res.setCharacterEncoding((String)null);res.setContentType(rs.getString(2));res.getOutputStream().write(rs.getBytes(1));return;
                }
            }
            if(me==null){flash(req,"Sign in to continue.");redirect(res,"/login");return;}
            String role=(String)me.get("role");boolean staff=!role.equals("USER");long uid=((Number)me.get("id")).longValue();
            if(path.equals("/profile")) {
                if(post){updateProfile(c,req,uid);flash(req,"Your profile has been updated.");redirect(res,"/profile");}
                else{req.setAttribute("page","profile");render(req,res);}return;
            }
            if(path.equals("/comment")&&post) {
                authorize(role.equals("USER"),"Only reader accounts may post comments.");postComment(c,req,res,uid);return;
            }
            if(path.equals("/dashboard")&&!post) {
                req.setAttribute("stats",Db.one(c,"SELECT (SELECT COUNT(*) FROM forums WHERE archived=FALSE) AS forums,(SELECT COUNT(*) FROM comments WHERE status='PUBLISHED') AS published,(SELECT COUNT(*) FROM comments WHERE status='PENDING') AS pending,(SELECT COUNT(*) FROM accounts WHERE role='USER' AND active=TRUE) AS readers"));
                if(role.equals("USER"))req.setAttribute("recent",Db.rows(c,"SELECT c.*,f.title FROM comments c JOIN forums f ON f.id=c.forum_id WHERE author_id=? ORDER BY id DESC LIMIT 20",uid));
                else req.setAttribute("recent",Db.rows(c,"SELECT l.*,a.username FROM audit_log l JOIN accounts a ON a.id=l.actor_id ORDER BY l.id DESC LIMIT 10"));
                req.setAttribute("page","dashboard");render(req,res);return;
            }
            authorize(staff,"This page is for administrators and verifiers.");
            if(path.equals("/moderation")&&!post) {
                String status=optional(req,"status",20);if(!Set.of("PENDING","PUBLISHED","REJECTED","ALL").contains(status))status="PENDING";
                String where=status.equals("ALL")?"":" WHERE c.status=?";
                req.setAttribute("comments",Db.rows(c,"SELECT c.*,a.username,f.title FROM comments c JOIN accounts a ON a.id=c.author_id JOIN forums f ON f.id=c.forum_id"+where+" ORDER BY c.id DESC",status.equals("ALL")?new Object[]{}:new Object[]{status}));
                req.setAttribute("filterStatus",status);req.setAttribute("page","moderation");render(req,res);return;
            }
            if(path.equals("/review")&&post) {
                String decision=required(req,"decision",15);require(Set.of("PUBLISHED","REJECTED").contains(decision),"Invalid review decision.");
                String note=optional(req,"note",500);
                c.setAutoCommit(false);
                try {
                    int changed=Db.exec(c,"UPDATE comments SET status=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,review_note=? WHERE id=? AND status='PENDING'",decision,uid,note,id(req));
                    require(changed==1,"This comment was already reviewed or does not exist.");
                    Db.audit(c,uid,"COMMENT_"+decision,"Comment #"+id(req));c.commit();
                } catch(Exception e){c.rollback();throw e;}
                flash(req,"Comment "+(decision.equals("PUBLISHED")?"approved and published.":"rejected."));redirect(res,"/moderation");return;
            }
            if(path.equals("/users")&&!post) {
                req.setAttribute("users",Db.rows(c,"SELECT * FROM accounts ORDER BY id"));req.setAttribute("page","users");render(req,res);return;
            }
            if(path.equals("/account")&&post) {
                String action=required(req,"action",15);require(Set.of("block","unblock","deactivate").contains(action),"Invalid account action.");
                c.setAutoCommit(false);
                try {
                    var target=Db.one(c,"SELECT * FROM accounts WHERE id=? FOR UPDATE",id(req));
                    require(target!=null&&target.get("role").equals("USER"),"Only reader accounts can be managed here.");
                    if(action.equals("unblock"))Db.exec(c,"UPDATE accounts SET blocked=FALSE,active=TRUE,spam_strikes=0 WHERE id=?",id(req));
                    else if(action.equals("block"))Db.exec(c,"UPDATE accounts SET blocked=TRUE WHERE id=?",id(req));
                    else Db.exec(c,"UPDATE accounts SET active=FALSE WHERE id=?",id(req));
                    Db.audit(c,uid,"ACCOUNT_"+action.toUpperCase(Locale.ROOT),"Account #"+id(req));c.commit();
                } catch(Exception e){c.rollback();throw e;}
                flash(req,"Account updated. Restoring an account resets its strikes.");redirect(res,"/users");return;
            }
            if(path.equals("/delete-forum")&&post) {
                c.setAutoCommit(false);
                try {
                    require(Db.exec(c,"UPDATE forums SET archived=TRUE WHERE id=? AND archived=FALSE",id(req))==1,"Forum not found.");
                    Db.audit(c,uid,"FORUM_ARCHIVED","Forum #"+id(req));c.commit();
                }catch(Exception e){c.rollback();throw e;}
                flash(req,"Forum removed from public view. Its moderation history is retained.");redirect(res,"/forums");return;
            }
            if(path.equals("/dictionary")&&!post) {
                req.setAttribute("terms",SpamFilter.terms(Db.root.resolve("config/spam-words.csv")));if(role.equals("ADMIN"))req.setAttribute("dictionaryCsv",Files.readString(Db.root.resolve("config/spam-words.csv")));req.setAttribute("page","dictionary");render(req,res);return;
            }
            if(path.equals("/audit")&&!post) {
                req.setAttribute("audit",Db.rows(c,"SELECT l.*,a.username FROM audit_log l JOIN accounts a ON a.id=l.actor_id ORDER BY l.id DESC LIMIT 500"));req.setAttribute("page","audit");render(req,res);return;
            }
            authorize(role.equals("ADMIN"),"Only administrators may perform this action.");
            if(path.equals("/dictionary")&&post) {
                String csv=requiredRaw(req,"csv",65536);
                Path config=Db.root.resolve("config"),target=config.resolve("spam-words.csv");
                Path temp=Files.createTempFile(config,"dictionary-",".csv");
                try {
                    Files.writeString(temp,csv);
                    try{SpamFilter.terms(temp);}catch(IOException e){throw new IllegalArgumentException("Invalid CSV: include at least one term and close all quoted cells.");}
                    synchronized(SpamFilter.class) {
                        Files.copy(target,config.resolve("spam-words.csv.bak"),StandardCopyOption.REPLACE_EXISTING);
                        Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                    }
                    Db.audit(c,uid,"DICTIONARY_UPDATED","Replaced CSV dictionary; previous version backed up locally.");
                }finally{Files.deleteIfExists(temp);}
                flash(req,"Dictionary saved. The next comment uses these terms. The previous CSV was backed up.");redirect(res,"/dictionary");return;
            }
            if(path.equals("/verifier")) {
                if(post)register(c,req,res,true);else{req.setAttribute("page","verifier");render(req,res);}return;
            }
            if(path.equals("/category")&&post) {
                Db.exec(c,"INSERT INTO categories(name) VALUES(?)",required(req,"name",80));flash(req,"Forum type created.");redirect(res,"/edit-forum");return;
            }
            if(path.equals("/edit-forum")) {
                if(post)saveForum(c,req,res,uid);
                else {
                    if(req.getParameter("id")!=null){var f=Db.one(c,"SELECT * FROM forums WHERE id=? AND archived=FALSE",id(req));require(f!=null,"Forum not found.");req.setAttribute("forum",f);}
                    req.setAttribute("categories",Db.rows(c,"SELECT * FROM categories ORDER BY name"));req.setAttribute("page","edit-forum");render(req,res);
                }return;
            }
            error(req,res,404,"Page not found.");
        } catch(Forbidden e){error(req,res,403,e.getMessage());}
        catch(IllegalArgumentException e){error(req,res,400,e.getMessage());}
        catch(SQLException e){
            if(e.getSQLState()!=null&&e.getSQLState().equals("23505"))error(req,res,409,"That username, email address, or forum type already exists.");
            else{getServletContext().log("Database operation failed",e);error(req,res,500,"The operation could not be completed. Please try again.");}
        }catch(IllegalStateException e){error(req,res,400,"The uploaded file is too large. Use a PNG or JPEG up to 2 MB.");}
        catch(Exception e){getServletContext().log("Request failed",e);error(req,res,500,"The operation could not be completed. Check the server log, then try again.");}
    }
    private void login(Connection c,HttpServletRequest req,HttpServletResponse res)throws Exception {
        String username=required(req,"username",40).toLowerCase(Locale.ROOT),password=requiredRaw(req,"password",128);
        String key=req.getRemoteAddr();long now=System.currentTimeMillis();
        // Local-demo throttle; use a shared rate limiter for multi-instance deployment.
        LOGIN_ATTEMPTS.entrySet().removeIf(e->now-e.getValue()[0]>900000);
        var attempt=LOGIN_ATTEMPTS.computeIfAbsent(key,k->new long[]{now,0});
        synchronized(attempt) {if(attempt[1]>=20){error(req,res,429,"Too many sign-in attempts. Try again in 15 minutes.");return;}attempt[1]++;}
        var account=Db.one(c,"SELECT * FROM accounts WHERE username=?",username);
        if(account==null||!Security.verify(password,(String)account.get("password_hash"))||!Boolean.TRUE.equals(account.get("active"))||Boolean.TRUE.equals(account.get("blocked"))) {
            error(req,res,401,"Invalid username or password, or your account is blocked/inactive.");return;
        }
        LOGIN_ATTEMPTS.remove(key);req.changeSessionId();req.getSession().setAttribute("uid",account.get("id"));req.getSession().setAttribute("csrf",Security.token());
        flash(req,"Welcome back, "+account.get("full_name")+".");redirect(res,"/dashboard");
    }
    private void register(Connection c,HttpServletRequest req,HttpServletResponse res,boolean verifier)throws Exception {
        String username=required(req,"username",40).toLowerCase(Locale.ROOT);
        require(username.matches("[a-z0-9_]{3,40}"),"Username must be 3-40 letters, digits, or underscores.");
        String name=required(req,"full_name",100),email=validEmail(req),password=requiredRaw(req,"password",128);
        require(password.length()>=10,"Password must contain at least 10 characters.");
        String phone=phone(req);String role=verifier?"VERIFIER":"USER";
        c.setAutoCommit(false);
        try {
            Db.exec(c,"INSERT INTO accounts(username,full_name,email,password_hash,role,phone) VALUES(?,?,?,?,?,?)",username,name,email,Security.hash(password),role,phone);
            if(verifier)Db.audit(c,((Number)((Map<?,?>)req.getAttribute("me")).get("id")).longValue(),"VERIFIER_CREATED",username);
            c.commit();
        }catch(Exception e){c.rollback();throw e;}
        flash(req,verifier?"Verifier account created.":"Account created. Sign in to join the conversation.");redirect(res,verifier?"/users":"/login");
    }
    private void postComment(Connection c,HttpServletRequest req,HttpServletResponse res,long uid)throws Exception {
        long forum=id(req);String body=required(req,"body",3000);
        var matches=SpamFilter.match(body,SpamFilter.terms(Db.root.resolve("config/spam-words.csv")));
        require(String.join(", ",matches).length()<=4000,"Too many dictionary matches. Shorten the comment.");
        c.setAutoCommit(false);int strikes;boolean blocked;
        try {
            var user=Db.one(c,"SELECT * FROM accounts WHERE id=? FOR UPDATE",uid);
            require(Boolean.TRUE.equals(user.get("active"))&&!Boolean.TRUE.equals(user.get("blocked")),"Your account is blocked or inactive.");
            require(Db.one(c,"SELECT id FROM forums WHERE id=? AND archived=FALSE FOR UPDATE",forum)!=null,"Forum not found.");
            strikes=((Number)user.get("spam_strikes")).intValue()+(matches.isEmpty()?0:1);blocked=strikes>=BLOCK_ON;
            Db.exec(c,"INSERT INTO comments(forum_id,author_id,body,status,matched_terms) VALUES(?,?,?,?,?)",forum,uid,body,matches.isEmpty()?"PUBLISHED":"PENDING",String.join(", ",matches));
            if(!matches.isEmpty()) {
                Db.exec(c,"UPDATE accounts SET spam_strikes=?,blocked=? WHERE id=?",strikes,blocked,uid);
                Db.audit(c,uid,"COMMENT_FLAGGED","Forum #"+forum+"; strike "+strikes+"; matched "+String.join(", ",matches).substring(0,Math.min(350,String.join(", ",matches).length())));
                if(blocked)Db.audit(c,uid,"ACCOUNT_AUTO_BLOCKED","Reached four flagged submissions.");
            }
            c.commit();
        }catch(Exception e){c.rollback();throw e;}
        if(blocked){req.getSession().removeAttribute("uid");flash(req,"Your account is blocked after 4 flagged comments. A verifier can restore it.");redirect(res,"/login");}
        else{flash(req,matches.isEmpty()?"Your comment is published.":"Your comment contains a dictionary term and is held for review. Warning "+strikes+" of 4.");redirect(res,"/forum?id="+forum);}
    }
    private void updateProfile(Connection c,HttpServletRequest req,long uid)throws Exception {
        String birth=optional(req,"birth_date",10);
        if(!birth.isBlank())try{require(!LocalDate.parse(birth).isAfter(LocalDate.now()),"Date of birth cannot be in the future.");}catch(java.time.format.DateTimeParseException e){throw new IllegalArgumentException("Enter a valid date of birth.");}
        Db.exec(c,"UPDATE accounts SET full_name=?,email=?,phone=?,gender=?,address=?,city=?,state=?,birth_date=? WHERE id=?",required(req,"full_name",100),validEmail(req),phone(req),optional(req,"gender",30),optional(req,"address",500),optional(req,"city",80),optional(req,"state",80),birth,uid);
    }
    private void saveForum(Connection c,HttpServletRequest req,HttpServletResponse res,long uid)throws Exception {
        String title=required(req,"title",160),body=required(req,"body",20000);long category=parseId(required(req,"category_id",30));
        require(Db.one(c,"SELECT id FROM categories WHERE id=?",category)!=null,"Select an existing forum type.");
        Part part=req.getPart("image");byte[] image=null;
        if(part!=null&&part.getSize()>0) {
            require(part.getSize()<=2*1024*1024,"Image must be no larger than 2 MB.");
            byte[] bytes;try(var in=part.getInputStream()){bytes=in.readAllBytes();}
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);require(readers.hasNext(),"Upload a valid PNG or JPEG image.");
                var reader=readers.next();
                try {
                    reader.setInput(input);String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    require(Set.of("png","jpeg","jpg").contains(format),"Only PNG and JPEG images are supported.");
                    require((long)reader.getWidth(0)*reader.getHeight(0)<=12000000,"Image dimensions are too large (maximum 12 megapixels).");
                    BufferedImage decoded=reader.read(0);var out=new ByteArrayOutputStream();ImageIO.write(decoded,"png",out);image=out.toByteArray();
                } finally{reader.dispose();}
            }
        }
        c.setAutoCommit(false);long forum;
        try {
            if(req.getParameter("id")==null||req.getParameter("id").isBlank()) {
                try(var p=c.prepareStatement("INSERT INTO forums(title,body,category_id,created_by,image_bytes,image_type) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                    p.setString(1,title);p.setString(2,body);p.setLong(3,category);p.setLong(4,uid);p.setBytes(5,image);p.setString(6,image==null?null:"image/png");p.executeUpdate();try(var keys=p.getGeneratedKeys()){keys.next();forum=keys.getLong(1);}
                }
            }else{
                forum=id(req);require(Db.exec(c,"UPDATE forums SET title=?,body=?,category_id=? WHERE id=? AND archived=FALSE",title,body,category,forum)==1,"Forum not found.");
                if(image!=null)Db.exec(c,"UPDATE forums SET image_bytes=?,image_type='image/png' WHERE id=?",image,forum);
                else if("yes".equals(req.getParameter("remove_image")))Db.exec(c,"UPDATE forums SET image_bytes=NULL,image_type=NULL WHERE id=?",forum);
            }
            Db.audit(c,uid,"FORUM_SAVED","Forum #"+forum);c.commit();
        }catch(Exception e){c.rollback();throw e;}
        flash(req,"Forum saved successfully.");redirect(res,"/forum?id="+forum);
    }
    private static String validEmail(HttpServletRequest req){String email=required(req,"email",254).toLowerCase(Locale.ROOT);require(email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),"Enter a valid email address.");return email;}
    private static String phone(HttpServletRequest req){String p=optional(req,"phone",10);require(p.isEmpty()||p.matches("[0-9]{10}"),"Phone number must contain exactly 10 digits.");return p;}
    private static String requiredRaw(HttpServletRequest req,String name,int max){String s=req.getParameter(name);require(s!=null&&!s.isBlank(),"The "+name.replace('_',' ')+" field is required.");require(s.length()<=max,"The "+name+" field is too long.");return s;}
    private static String required(HttpServletRequest req,String name,int max){return requiredRaw(req,name,max).strip();}
    private static String optional(HttpServletRequest req,String name,int max){String s=Objects.toString(req.getParameter(name),"").strip();require(s.length()<=max,"The "+name+" field is too long.");return s;}
    private static long id(HttpServletRequest req){return parseId(required(req,"id",30));}
    private static long parseId(String value){try{long id=Long.parseLong(value);require(id>0,"Invalid identifier.");return id;}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid identifier.");}}
    private static void authorize(boolean condition,String message){if(!condition)throw new Forbidden(message);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    private static void flash(HttpServletRequest req,String text){req.getSession().setAttribute("flash",text);}
    private static void redirect(HttpServletResponse res,String path)throws IOException{res.setStatus(303);res.setHeader("Location","/app"+path);}
    private static void render(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException{res.setContentType("text/html; charset=UTF-8");req.getRequestDispatcher("/WEB-INF/view.jsp").forward(req,res);}
    private static void error(HttpServletRequest req,HttpServletResponse res,int status,String message)throws ServletException,IOException{res.setStatus(status);req.setAttribute("page","error");req.setAttribute("error",message);render(req,res);}
    private static class Forbidden extends RuntimeException{Forbidden(String s){super(s);}}
}
