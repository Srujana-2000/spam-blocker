import blog.Security;
import blog.SpamFilter;
import java.nio.file.*;
import java.util.*;

public class CoreTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path csv=Files.createTempFile("spam-filter-", ".csv");
        try {
            Files.writeString(csv,"\uFEFFterm\nspam,scam\n\"buy now\"\n\"free money\"\nlastword");
            var terms=SpamFilter.terms(csv);
            check(terms.size()==5,"Reads all cells and the last CSV entry");
            check(SpamFilter.match("SPAM!",terms).equals(List.of("spam")),"Case and punctuation normalization");
            check(SpamFilter.match("spammer and scampi",terms).isEmpty(),"No partial-word false positives");
            check(SpamFilter.match("You can BUY, NOW.",terms).equals(List.of("buy now")),"Phrase with punctuation");
            check(SpamFilter.match("ＳＰＡＭ",terms).equals(List.of("spam")),"Unicode NFKC normalization");
            check(SpamFilter.match("This is useful.",terms).isEmpty(),"Clean comment");
            check(SpamFilter.match("lastword",terms).equals(List.of("lastword")),"Last entry is not skipped");
            check(SpamFilter.match("spam spam spam",terms).size()==1,"One match per term");
            Files.writeString(csv,"term\n\"spam");
            try{SpamFilter.terms(csv);throw new AssertionError("Malformed CSV accepted");}catch(java.io.IOException expected){checks++;}
            Files.writeString(csv,"term\n");
            try{SpamFilter.terms(csv);throw new AssertionError("Empty dictionary accepted");}catch(java.io.IOException expected){checks++;}
            String hash=Security.hash("A secure test password");
            check(Security.verify("A secure test password",hash),"Password verifies");
            check(!Security.verify("Wrong password",hash),"Wrong password rejected");
            check(!hash.equals(Security.hash("A secure test password")),"Independent salts");
            check(Security.escape("<script>\"&'").equals("&lt;script&gt;&quot;&amp;&#39;"),"HTML escaping");
            check(!Security.equal(null,"x")&&Security.equal("abc","abc")&&!Security.equal("abc","abd"),"CSRF comparison");
            System.out.println("PASS: "+checks+" Java core checks");
        }finally{Files.deleteIfExists(csv);}
    }
}
