package app.studyflow;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class DocumentTextTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static File zip(String... pairs)throws Exception {
        File f=File.createTempFile("studyflow",".zip");f.deleteOnExit();
        try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){for(int i=0;i<pairs.length;i+=2){z.putNextEntry(new ZipEntry(pairs[i]));z.write(pairs[i+1].getBytes(StandardCharsets.UTF_8));z.closeEntry();}}return f;
    }
    static String read(File f,String name)throws Exception{return String.join("",DocumentText.read(f,name).pages);}
    public static void main(String[] args)throws Exception {
        File text=File.createTempFile("studyflow",".txt");text.deleteOnExit();
        String original="Chapter one\n"+"വിദ്യാഭ്യാസം Hello 🌟\n".repeat(1500);Files.writeString(text.toPath(),original);
        DocumentText.Document d=DocumentText.read(text,"notes.txt");check(d.pages.size()>1,"Paginated long text");check(String.join("",d.pages).equals(original),"Pagination must not lose Unicode or text");
        Files.write(text.toPath(),new byte[]{(byte)0xff,(byte)0xfe,65,0,66,0});check(read(text,"notes.txt").equals("AB"),"UTF-16 BOM");
        File docx=zip("word/document.xml","<w:document xmlns:w='urn:word'><w:p><w:r><w:t>Revision</w:t></w:r></w:p><w:p><w:r><w:t>Next paragraph</w:t></w:r></w:p></w:document>");
        check(read(docx,"notes.docx").contains("Revision\nNext paragraph"),"DOCX paragraphs");
        File pptx=zip("ppt/presentation.xml","<p:presentation xmlns:p='urn:p' xmlns:r='urn:r'><p:sldId r:id='last'/><p:sldId r:id='first'/></p:presentation>","ppt/_rels/presentation.xml.rels","<Relationships><Relationship Id='first' Target='slides/slide1.xml'/><Relationship Id='last' Target='slides/slide2.xml'/></Relationships>","ppt/slides/slide1.xml","<slide><p>First</p></slide>","ppt/slides/slide2.xml","<slide><p>Second</p></slide>");
        String slides=read(pptx,"lecture.pptx");check(slides.indexOf("Second")<slides.indexOf("First"),"Presentation relationship order");
        File xlsx=zip("xl/workbook.xml","<workbook xmlns:r='urn:r'><sheet name='Results' r:id='s1'/></workbook>","xl/_rels/workbook.xml.rels","<Relationships><Relationship Id='s1' Target='worksheets/sheet1.xml'/></Relationships>","xl/sharedStrings.xml","<sst><si><t>Score</t></si></sst>","xl/worksheets/sheet1.xml","<worksheet><row><c r='A1' t='s'><v>0</v></c><c r='B1'><v>92</v></c><c r='C1' t='inlineStr'><is><t>Pass</t></is></c></row></worksheet>");
        String cells=read(xlsx,"results.xlsx");check(cells.contains("Results")&&cells.contains("A1: Score")&&cells.contains("B1: 92")&&cells.contains("C1: Pass"),"Shared strings, numeric and inline cells");
        File odt=zip("content.xml","<document><p>OpenDocument notes</p></document>");for(String ext:new String[]{"odt","ods","odp"})check(read(odt,"notes."+ext).contains("OpenDocument notes"),"OpenDocument "+ext);
        File epub=zip("META-INF/container.xml","<container><rootfile full-path='Book/content.opf'/></container>","Book/content.opf","<package><manifest><item id='a' href='a.xhtml'/><item id='b' href='b.xhtml'/></manifest><spine><itemref idref='b'/><itemref idref='a'/></spine></package>","Book/a.xhtml","<html><body><p>Alpha</p></body></html>","Book/b.xhtml","<html><body><p>Beta</p></body></html>");
        String book=read(epub,"book.epub");check(book.indexOf("Beta")<book.indexOf("Alpha"),"EPUB spine order");
        Files.writeString(text.toPath(),"<html><script>alert('bad')</script><p>Hello &amp; goodbye</p></html>");String html=read(text,"notes.html");check(!html.contains("alert")&&html.contains("Hello & goodbye"),"Inert HTML");
        File attack=zip("word/document.xml","<!DOCTYPE doc [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><doc>&x;</doc>");
        boolean rejected=false;try{read(attack,"attack.docx");}catch(Exception e){rejected=true;}check(rejected,"Reject external entities");
        rejected=false;try{DocumentText.parse("<!DOCTYPE doc [<!ENTITY x 'boom'>]><doc>&x;</doc>".getBytes(StandardCharsets.UTF_16LE),new org.xml.sax.helpers.DefaultHandler());}catch(Exception e){rejected=true;}check(rejected,"Reject UTF-16 DTD without BOM");
        File large=zip("word/document.xml","<doc>"+"A".repeat(DocumentText.LIMIT)+"</doc>");rejected=false;try{read(large,"large.docx");}catch(Exception e){rejected=true;}check(rejected,"Limit expanded ZIP content");
        File missing=zip("other.xml","<p>No document</p>");rejected=false;try{read(missing,"broken.docx");}catch(Exception e){rejected=true;}check(rejected,"Reject malformed packages");
        System.out.println("All document reader checks passed.");
    }
}
