package com.deltapunkt;

import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.stream.Collectors;

import static alexh.Unchecker.unchecked;
import static alexh.Unchecker.uncheckedGet;
import static com.deltapunkt.DownloadUrlTest.Link.*;
import static com.deltapunkt.DownloadUrlTest.LinkType.*;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

public class DownloadUrlTest {
    String baseUrl = "";
    String outputFolder = "";

    enum LinkType {
        FOLDER,
        OTHER, FILE
    }

    public static class Link {
        private final LinkType type;
        private final String href;

        public static Link folderLink(String href) {
            return new Link(FOLDER, href);
        }

        public static Link fileLink(String href) {
            return new Link(FILE, href);
        }

        public static Link otherLink(String href) {
            return new Link(OTHER, href);
        }

        public Link(LinkType type, String href) {
            this.type = type;
            this.href = href;
        }

        public LinkType getType() {
            return type;
        }

        public String getHref() {
            return href;
        }

        @Override
        public String toString() {
            return "Link{" +
                    "type=" + type +
                    ", href='" + href + '\'' +
                    '}';
        }
    }

    @Test
    public void downloadUrl() throws Exception {
        List<String> allFiles = new ArrayList<>();
        List<String> exploredFolders = new ArrayList<>();

        Queue<String> foldersToExplore = new ArrayDeque<>(asList(baseUrl));

        while (!foldersToExplore.isEmpty()) {
            String url = foldersToExplore.remove();
            System.out.println("--------------------------------------------");
            System.out.println("Exploring folder: " + url);

            Map<LinkType, List<Link>> links = exploreFolder(url);
            System.out.println("-----------------------------------------");
            System.out.println("Explored folder: " + url);
            System.out.println("folders = " + links.get(FOLDER));
            System.out.println("files = " + links.get(FILE));
            System.out.println("-----------------------------------------");
            List<String> files = extractHrefByType(links, FILE);
            List<String> folders = extractHrefByType(links, FOLDER);

            exploredFolders.add(url);
            foldersToExplore.addAll(folders);
            allFiles.addAll(files);
        }

        System.out.println("Files:");
        allFiles.forEach(System.out::println);
        System.out.println("Folders:");
        foldersToExplore.forEach(System.out::println);

//        downloadFile(allFiles.get(0));

        allFiles.stream().forEach(fileHref ->
            downloadFile(fileHref)
        );
        downloadFile(allFiles.get(0));
    }

    private void downloadFile(String fileHref) {
        String fileName = fileHref.substring(1 + fileHref.lastIndexOf('/'));
        System.out.println("Downloading = '" + fileName + "'");

        unchecked( () -> {
            URL url = new URL(encodeUrl(fileHref));
            ReadableByteChannel rbc = Channels.newChannel(url.openStream());
            FileOutputStream fos = new FileOutputStream(outputFolder + fileName);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        });
    }

    private static String encodeUrl(String url) {
        try {
            URL e = new URL(url);
            return encodeUrl(e).toExternalForm();
        } catch (Exception var2) {
            return url;
        }
    }

    private static URL encodeUrl(URL u) {
        try {
            URI e = new URI(u.getProtocol(), u.getUserInfo(), u.getHost(), u.getPort(), u.getPath(), u.getQuery(), u.getRef());
            return new URL(e.toASCIIString());
        } catch (Exception var2) {
            return u;
        }
    }

    private void downloadFile1(String fileHref) {
        String fileName = fileHref.substring(1 + fileHref.lastIndexOf('/'));
        System.out.println("Downloading = '" + fileName + "'");

        Response response =
            uncheckedGet(() ->
                Jsoup.connect(fileHref)
                    .ignoreContentType(true)
                    .execute()
            );

        String outputFolder = "/home/acci/kb/simonpie/3/";
        unchecked( () -> {
            FileOutputStream out = new FileOutputStream(new java.io.File(outputFolder + fileName));
            out.write(response.bodyAsBytes());  // resultImageResponse.body() is where the image's contents are.
            out.close();
        });
    }

    private List<String> extractHrefByType(
            Map<LinkType, List<Link>> links,
            LinkType type
    ) {
        return Optional
                .ofNullable(links.get(type))
                .orElse(emptyList())
                .stream()
                .map(Link::getHref)
                .collect(Collectors.toList())
                ;
    }

    private Map<LinkType, List<Link>> exploreFolder(String url) throws IOException {
        Document doc = Jsoup.connect(url).get();
        Elements newsHeadlines = doc.select("a");
        Map<LinkType, List<Link>> links = newsHeadlines.stream().map(n -> {
            String href = uncheckedGet(
                () -> new URLDecoder().decode(n.attr("href"), "UTF-8")
            );
            if (href.endsWith("/") && !href.startsWith("/")) {
                System.out.println("Found Folder = " + (url + href));
                return folderLink(url + href);
            }
            if (
                    href.endsWith(".pdf")
                            || href.endsWith(".chm")
                            || href.endsWith(".txt")
                    ) {
                System.out.println("Found File = " + (url + href));
                return fileLink(url + href);
            }
            return otherLink(url + href);
        })
//        .filter((l) -> l.getType() != OTHER)
        .collect(Collectors.groupingBy(Link::getType))
        ;

        return links;
    }
}
