package blog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

/** Intentionally a dictionary filter, not an ML spam detector. No substring matches. */
public final class SpamFilter {
    public static String normalize(String text) {
        return Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+"," ").strip().replaceAll(" +"," ");
    }
    public static List<String> terms(Path csv) throws IOException {
        // Real CSV reader: supports quoted cells, escaped quotes, and embedded newlines.
        String data=Files.readString(csv,StandardCharsets.UTF_8).replace("\uFEFF","");
        Set<String> result=new LinkedHashSet<>();
        StringBuilder cell=new StringBuilder();boolean quoted=false;
        for(int i=0;i<=data.length();i++) {
            char ch=i==data.length()?'\n':data.charAt(i);
            if(ch=='"') {
                if(quoted&&i+1<data.length()&&data.charAt(i+1)=='"'){cell.append('"');i++;}
                else quoted=!quoted;
            } else if(!quoted&&(ch==','||ch=='\n'||ch=='\r')) {
                String term=cell.toString().strip();cell.setLength(0);
                if(!term.isBlank()&&!term.startsWith("#")&&!term.equalsIgnoreCase("term")) {
                    String n=normalize(term);if(!n.isEmpty())result.add(n);
                }
            } else cell.append(ch);
        }
        if(quoted)throw new IOException("Unclosed quote in dictionary CSV");
        if(result.isEmpty())throw new IOException("The dictionary has no usable terms");
        return List.copyOf(result);
    }
    public static List<String> match(String text,List<String> dictionary) {
        String normalized=" "+normalize(text)+" ";
        return dictionary.stream().filter(t->normalized.contains(" "+t+" ")).toList();
    }
    private SpamFilter(){}
}
