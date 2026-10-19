package app.studyflow;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Bounded offline text reader. Never executes document scripts or fetches external entities. */
final class DocumentText {
    static final int LIMIT=4*1024*1024;
    static final class Document {
        final List<String> pages;
        Document(String text) throws IOException {
            if(text.trim().isEmpty()) throw new IOException("No readable text found. Use Open with for scanned or image-only documents.");
            pages=new ArrayList<>();
            for(int start=0;start<text.length();) {
                int end=Math.min(start+5000,text.length());
                if(end<text.length()) { int split=text.lastIndexOf('\n',end); if(split>start+2500) end=split+1; }
                if(end<text.length() && Character.isHighSurrogate(text.charAt(end-1)))end--;
                pages.add(text.substring(start,end));start=end;
            }
        }
    }
    static String extension(String name) {int i=name.lastIndexOf('.');return i<0?"":name.substring(i+1).toLowerCase(Locale.ROOT);}
    static boolean supports(String name,String mime) {
        return Arrays.asList("txt","md","csv","tsv","json","xml","html","htm","log","docx","pptx","xlsx","odt","ods","odp","epub").contains(extension(name)) || mime.startsWith("text/")&&!mime.equals("text/rtf");
    }
    static Document read(File file,String name) throws Exception {
        String ext=extension(name), result;
        if(Arrays.asList("docx","pptx","xlsx","odt","ods","odp","epub").contains(ext)) {
            try(ZipFile zip=new ZipFile(file)) {
                StringBuilder out=new StringBuilder(); Budget budget=new Budget();
                switch(ext) {
                    case "docx": out.append(extract(entry(zip,"word/document.xml",budget))); break;
                    case "pptx":
                        // Relationship order, including presentations whose slides have been rearranged.
                        Map<String,String> rels=relationships(entry(zip,"ppt/_rels/presentation.xml.rels",budget));
                        for(String id:attributes(entry(zip,"ppt/presentation.xml",budget),"sldId","id",true)) {
                            String target=rels.get(id);if(target!=null)append(out,extract(entry(zip,resolve("ppt/presentation.xml",target),budget)));
                        }
                        break;
                    case "xlsx":
                        List<String> shared=new ArrayList<>();
                        if(zip.getEntry("xl/sharedStrings.xml")!=null)parse(entry(zip,"xl/sharedStrings.xml",budget),new DefaultHandler(){
                            StringBuilder s;boolean text;
                            public void startElement(String u,String l,String q,Attributes a){if(l.equals("si"))s=new StringBuilder();if(l.equals("t"))text=true;}
                            public void characters(char[] c,int st,int len){if(text&&s!=null)s.append(c,st,len);}
                            public void endElement(String u,String l,String q){if(l.equals("t"))text=false;if(l.equals("si")){shared.add(s.toString());s=null;}}
                        });
                        Map<String,String> sheets=relationships(entry(zip,"xl/_rels/workbook.xml.rels",budget));
                        byte[] workbook=entry(zip,"xl/workbook.xml",budget);
                        List<String> ids=attributes(workbook,"sheet","id",true), names=attributes(workbook,"sheet","name",false);
                        for(int i=0;i<ids.size();i++) {
                            String target=sheets.get(ids.get(i));if(target==null)continue;
                            append(out,"\n"+(i<names.size()?names.get(i):"Sheet")+"\n");
                            StringBuilder sheet=new StringBuilder();
                            parse(entry(zip,resolve("xl/workbook.xml",target),budget),new DefaultHandler(){
                                String type="", ref="";StringBuilder value;boolean capture;
                                public void startElement(String u,String l,String q,Attributes a){if(l.equals("c")){type=a.getValue("t");ref=a.getValue("r");value=new StringBuilder();}if(l.equals("v")||l.equals("t"))capture=true;}
                                public void characters(char[] c,int st,int len){if(capture&&value!=null)value.append(c,st,len);}
                                public void endElement(String u,String l,String q)throws SAXException{
                                    if(l.equals("v")||l.equals("t"))capture=false;
                                    if(l.equals("c")){String v=value.toString();if("s".equals(type))try{v=shared.get(Integer.parseInt(v));}catch(Exception e){throw new SAXException("Invalid shared string",e);}
                                        sheet.append(ref==null?"":ref+": ").append(v).append("   ");if(sheet.length()>LIMIT)throw new SAXException("Document too large");}
                                    if(l.equals("row"))sheet.append('\n');
                                }
                            });append(out,sheet.toString());
                        }
                        break;
                    case "epub":
                        byte[] container=entry(zip,"META-INF/container.xml",budget);
                        List<String> roots=attributes(container,"rootfile","full-path",false);
                        if(roots.isEmpty())throw new IOException("Missing EPUB package");String opf=roots.get(0);
                        byte[] packageXml=entry(zip,opf,budget);Map<String,String> items=new HashMap<>();
                        parse(packageXml,new DefaultHandler(){public void startElement(String u,String l,String q,Attributes a){if(l.equals("item"))items.put(a.getValue("id"),a.getValue("href"));}});
                        for(String id:attributes(packageXml,"itemref","idref",false)) {String href=items.get(id);if(href!=null)append(out,extract(entry(zip,resolve(opf,href),budget)));}
                        break;
                    default: out.append(extract(entry(zip,"content.xml",budget)));
                }
                result=out.toString();
            }
        } else {
            try(InputStream in=new FileInputStream(file)){result=decode(bounded(in,new Budget()));}
            // HTML is shown as inert text: no WebView, scripts, remote images, or links execute.
            if(ext.equals("html")||ext.equals("htm"))result=result.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>","").replaceAll("(?i)</?(p|div|br|h[1-6]|li|tr)\\b[^>]*>","\n").replaceAll("(?s)<[^>]+>","").replace("&nbsp;"," ").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&amp;","&");
        }
        return new Document(result);
    }
    private static void append(StringBuilder out,String text)throws IOException{if(out.length()+text.length()+2>LIMIT)throw new IOException("Text is too large for the offline reader. Use Open with.");out.append(text).append("\n\n");}
    private static final class Budget {int remaining=LIMIT;}
    private static byte[] bounded(InputStream in,Budget b)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){b.remaining-=n;if(b.remaining<0)throw new IOException("Document exceeds the 4 MB expanded-text limit. Use Open with.");out.write(buf,0,n);}return out.toByteArray();
    }
    private static byte[] entry(ZipFile zip,String path,Budget b)throws IOException {ZipEntry e=zip.getEntry(path);if(e==null)throw new IOException("Missing document content: "+path);try(InputStream in=zip.getInputStream(e)){return bounded(in,b);}}
    private static String decode(byte[] bytes) {
        if(bytes.length>=2 && bytes[0]==(byte)0xff && bytes[1]==(byte)0xfe)return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16LE);
        if(bytes.length>=2 && bytes[0]==(byte)0xfe && bytes[1]==(byte)0xff)return new String(bytes,2,bytes.length-2,StandardCharsets.UTF_16BE);
        return new String(bytes,StandardCharsets.UTF_8).replace("\uFEFF","");
    }
    static void parse(byte[] xml,DefaultHandler handler)throws Exception {
        SAXParserFactory f=SAXParserFactory.newInstance();f.setNamespaceAware(true);
        // Fail closed if the platform parser cannot disable external entities.
        f.setFeature("http://xml.org/sax/features/external-general-entities",false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        XMLReader r=f.newSAXParser().getXMLReader();
        r.setEntityResolver((publicId,systemId)->new InputSource(new StringReader("")));
        // DTDs are unnecessary for supported formats; reject them, including UTF-16 encodings.
        String source=new String(xml,StandardCharsets.ISO_8859_1).replace("\u0000", "");
        if(source.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")||source.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw new IOException("Documents with DTD declarations are not supported.");
        r.setContentHandler(handler);r.parse(new InputSource(new ByteArrayInputStream(xml)));
    }
    private static String extract(byte[] xml)throws Exception {
        StringBuilder out=new StringBuilder();
        parse(xml,new DefaultHandler(){int hidden;
            public void startElement(String u,String l,String q,Attributes a){if(l.equals("script")||l.equals("style"))hidden++;if(l.equals("tab"))out.append('\t');}
            public void characters(char[] c,int st,int len)throws SAXException{if(hidden==0){out.append(c,st,len);if(out.length()>LIMIT)throw new SAXException("Text too large");}}
            public void endElement(String u,String l,String q){if(l.equals("script")||l.equals("style"))hidden--;if(Arrays.asList("p","h","div","br","tr","table-row","h1","h2","li").contains(l))out.append('\n');if(l.equals("table-cell"))out.append('\t');}
        });return out.toString();
    }
    private static List<String> attributes(byte[] xml,String element,String attr,boolean namespaced)throws Exception {
        List<String> out=new ArrayList<>();parse(xml,new DefaultHandler(){public void startElement(String u,String l,String q,Attributes a){if(l.equals(element))for(int i=0;i<a.getLength();i++)if(a.getLocalName(i).equals(attr)&&(!namespaced||!a.getURI(i).isEmpty()))out.add(a.getValue(i));}});return out;
    }
    private static Map<String,String> relationships(byte[] xml)throws Exception {
        Map<String,String> out=new HashMap<>();parse(xml,new DefaultHandler(){public void startElement(String u,String l,String q,Attributes a){if(l.equals("Relationship")&&!"External".equals(a.getValue("TargetMode")))out.put(a.getValue("Id"),a.getValue("Target"));}});return out;
    }
    private static String resolve(String base,String target)throws IOException {
        try {java.net.URI resolved=new java.net.URI(base).resolve(target).normalize();String path=resolved.getPath();if(resolved.isAbsolute()||resolved.getAuthority()!=null||path==null||path.contains(".."))throw new IOException("Invalid document reference");return path.startsWith("/")?path.substring(1):path;}
        catch(java.net.URISyntaxException e){throw new IOException("Invalid document reference",e);}
    }
}
