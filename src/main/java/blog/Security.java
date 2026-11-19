package blog;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;

public final class Security {
    private static final SecureRandom RANDOM=new SecureRandom();
    public static String token() {byte[] b=new byte[32];RANDOM.nextBytes(b);return HexFormat.of().formatHex(b);}
    public static String hash(String password) throws Exception {
        byte[] salt=new byte[16];RANDOM.nextBytes(salt);
        return "pbkdf2$210000$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(derive(password,salt,210000));
    }
    private static byte[] derive(String p,byte[] salt,int iterations) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(p.toCharArray(),salt,iterations,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    public static boolean verify(String password,String encoded) throws Exception {
        String[] a=encoded.split("\\$");
        return a.length==4 && MessageDigest.isEqual(Base64.getDecoder().decode(a[3]),derive(password,Base64.getDecoder().decode(a[2]),Integer.parseInt(a[1])));
    }
    public static boolean equal(String a,String b){return a!=null&&b!=null&&MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    public static String escape(Object value) {
        if(value==null)return "";
        return value.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    private Security(){}
}
