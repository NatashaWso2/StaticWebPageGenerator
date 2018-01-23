package test;

import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.yaml.front.matter.AbstractYamlFrontMatterVisitor;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.options.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
    static final MutableDataSet OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, Arrays.asList(
                    CustomExtension.create(), AttributesExtension.create(), YamlFrontMatterExtension.create()
            ));
    static String userDir = System.getProperty("user.dir");
    static String generated_file_directory = userDir + "/src/main/resources/generated/";
    static String layout_template_directory = userDir + "/src/main/resources/layouts/";
    static String markdown_file_directory = userDir + "/src/main/resources/markdown/";
    static String default_template = "default";

    public static void main(String[] args) throws IOException {
        // Build the parser and HTML parser
        Parser parser = Parser.builder(OPTIONS).build();
        HtmlRenderer renderer = HtmlRenderer.builder(OPTIONS).build();

        List<File> fileList = listAllFilesInDirectory();

        for (File file : fileList) {

            String markdown = readMarkdownFile(file.getName());
            Node document = parser.parse(markdown);

            // Get the yaml front matter properties from markdown
            AbstractYamlFrontMatterVisitor visitor = new AbstractYamlFrontMatterVisitor();
            visitor.visit(document);
            Map<String, List<String>> frontMatterList = visitor.getData();

            String layoutHTML;
            if (frontMatterList.get("layout") != null) {
                String layoutTemplate = frontMatterList.get("layout").get(0);
                layoutHTML = getLayoutContent(layoutTemplate);
            } else {
                layoutHTML = getLayoutContent(default_template);
            }

            // Generate html content from markdown
            String generatedHtmlFromMarkdown = renderer.render(document);

            layoutHTML = replaceContent(layoutHTML, "{{content}}", generatedHtmlFromMarkdown);
            if (frontMatterList.get("title") != null) {
                layoutHTML = replaceContent(layoutHTML, "{{title}}", frontMatterList.get("title").get(0));
            }
            Document layoutHtmlDoc = Jsoup.parse(layoutHTML);
            String htmlFileName = file.getName().replace(".md", "");
            writeHtmlToFile(htmlFileName, layoutHtmlDoc.outerHtml().getBytes());
        }

    }

    public static void writeHtmlToFile(String fileName, byte[] htmlContent) throws IOException {
        String filePath = generated_file_directory + fileName + ".html";
        Files.write(Paths.get(filePath), htmlContent);
    }

    public static String getLayoutContent(String layoutTemplateFileName) throws IOException {
        File input = new File(layout_template_directory + layoutTemplateFileName + ".html");
        Document layoutFileDoc = Jsoup.parse(input, null);
        return layoutFileDoc.html();
    }

    public static String readMarkdownFile(String name) throws IOException {
        String markdownContent = Files.lines(Paths.get(markdown_file_directory + name))
                .collect(Collectors.joining("\n"));
        return markdownContent;
    }

    public static String replaceContent(String

                                                htmlContent, String target, String replacement) {
        return htmlContent.replace(target, replacement);
    }

    public static List<File> listAllFilesInDirectory() throws IOException {
        List<File> filesInFolder = Files.walk(Paths.get(markdown_file_directory))
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .collect(Collectors.toList());
        return filesInFolder;
    }
}