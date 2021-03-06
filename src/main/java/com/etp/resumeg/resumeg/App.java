package com.etp.resumeg.resumeg;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.Matrix;
import com.itextpdf.kernel.geom.Vector;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.CanvasGraphicsState;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.filespec.PdfDictionaryFS;
import com.sun.javafx.webkit.WebConsoleListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;

public class App extends Application {

    public App() {
        newContent.put("123 your street", "124 MY STREET");
        newContent.put("your city, st 12345", "MY CITY, ST 54321");
        newContent.put("(123) 456-7890", "(553) 654-0987");
        newContent.put("no_reply@example.com", "TEST@TEST.COM");
    }

    @Override
    public void start(Stage stage) {
        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();

////Change the path according to yours.
//        String url = getClass().getResource("pdfjs/web/viewer.html").toExternalForm();
////We add our stylesheet.
//        engine.setUserStyleSheetLocation(getClass().getResource("pdfjs/web.css").toExternalForm());
        engine.setJavaScriptEnabled(true);

        URL urlHello = getClass().getClassLoader().getResource("html/index.html");
        engine.load(urlHello.toExternalForm());
        engine.setOnAlert(event -> showAlert(event.getData()));

        Button btn = new Button("test btn");


        StackPane layout = new StackPane(webView);
        layout.getChildren().add(btn);

        Scene scene = new Scene(layout);
        stage.setTitle("Test javaFx");
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

    }

    private void showAlert(String message) {
        Dialog<Void> alert = new Dialog<>();
        alert.getDialogPane().setContentText(message);
        alert.getDialogPane().getButtonTypes().add(ButtonType.OK);
        alert.showAndWait();
    }

    // New content for pdf
    private static final HashMap<String, String> newContent = new HashMap<>();

    // GoogleWebFonts
    private static GoogleWebFontService webFontService = null;

    public static void main(String[] args) throws IOException {
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(EnvVariable.SRC_RESUME), new PdfWriter(EnvVariable.OUT_PDF));
        pdfDoc.getFirstPage().getPageSize().getX();

        App app = new App();

        List<MyItem> items = app.getContentItems(pdfDoc);
        Collections.sort(items);

        List<Line> lines = app.getLines(items);
//        List<Structure> structures = app.getStructures(lines);

        System.out.println("items.size():" + items.size());
        System.out.println("lines.size():" + lines.size());
//        System.out.println("structures.size():" + structures.size());

        HashMap<Double, List<Line>> multipleColumnLines = splitColumnLinesByX(lines);

        System.out.println("twoColumnLines.size(): " + multipleColumnLines.size());

        double firstItemKey = (Double) multipleColumnLines.keySet().toArray()[0];
        System.out.println("firstKey: " + firstItemKey);

        double maxYTolerance = findMaxYTolerance(multipleColumnLines, firstItemKey);
        System.out.println("maxYTolerance: " + maxYTolerance);

        HashMap<String, Structure> multipleColumnStructures = createMultipleColumnStructures(app, multipleColumnLines, maxYTolerance);

        System.out.println();
        printMultipleColumnStructures(multipleColumnStructures);
        System.out.println("multipleColumnStructures.size(): " + multipleColumnStructures.size());
        System.out.println();

        showPdfStructure(pdfDoc, items);
        showPdfStructure(pdfDoc, lines);
        showPdfStructure(pdfDoc, multipleColumnStructures);

        pdfDoc.close();

        TagContent.fillTagContentList();
        System.out.println("tagContentList.size: " + TagContent.getTagContentList().size());

        // loop over structures and create xml
        XmlService.createXmlTest(multipleColumnStructures);
        boolean valid = true;

        // validate xml
        try {
            validate(XmlService.xmlFilePath,"src/main/resources/schema/template.xsd");
        } catch (SAXException e) {
//            e.printStackTrace();
            valid = false;
        }

        System.out.println("xml file is valid: " + valid);

        // run javaFx app
//        launch(args);
        Platform.exit();
    }

    public static void validate(String xml, String xsd) throws SAXException, IOException {
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        ((schemaFactory.newSchema(new File(xsd))).newValidator()).validate(new StreamSource(new File(xml)));

    }

    private static void printMultipleColumnStructures(HashMap<String, Structure> multipleColumnStructures) {
        for (Map.Entry<String, Structure> entry :
                multipleColumnStructures.entrySet()) {

            System.out.println("structureKey: " + entry.getKey());
            System.out.println("structureTxt: " + entry.getValue().getText());
            System.out.println();
        }
    }

    private static HashMap<String, Structure> createMultipleColumnStructures(App app, HashMap<Double, List<Line>> multipleColumnLines, double maxYTolerance) {
        HashMap<String, Structure> multipleColumnStructures = new HashMap<>();
        for (Map.Entry<Double, List<Line>> entry : multipleColumnLines.entrySet()
        ) {
            List<Structure> structures = app.getStructures(entry.getValue(), maxYTolerance);

            for (Structure structure :
                    structures) {
                UUID uuid = UUID.randomUUID();
                String randomUUIDString = uuid.toString();

                if (!multipleColumnStructures.containsKey(randomUUIDString)) {
                    multipleColumnStructures.put(randomUUIDString, structure);
                }
            }
        }
        return multipleColumnStructures;
    }

    private static double findMaxYTolerance(HashMap<Double, List<Line>> multipleColumnLines, double firstItemKey) {
        double maxYTolerance = Math.abs(multipleColumnLines.get(firstItemKey).get(0).getLL().getY() - multipleColumnLines.get(firstItemKey).get(1).getLL().getY());
        System.out.println("firstMaxYtolerance: " + maxYTolerance);
        for (int i = 1; i <= multipleColumnLines.get(firstItemKey).size() - 1; i++) {
            Line linePrev = multipleColumnLines.get(firstItemKey).get(i - 1);
            Line lineNext = multipleColumnLines.get(firstItemKey).get(i);

            System.out.println("line.y: " + multipleColumnLines.get(firstItemKey).get(i).getLL().getY());
            System.out.println("maxYTolerance: " + maxYTolerance);

            maxYTolerance = Math.min(Math.abs(lineNext.getLL().getY() - linePrev.getLL().getY()), maxYTolerance);
        }
        return maxYTolerance;
    }

    public static void showPdfStructure(PdfDocument pdfDoc, HashMap<String, Structure> items) {
        for (Map.Entry<String, Structure> item : items.entrySet()) {
            PdfDrawService.drawRectangleOnPdf(pdfDoc, item.getValue().getDrawableRectangle(), ColorConstants.BLACK);
        }
    }

    public static HashMap<Double, List<Line>> splitColumnLinesByX(List<Line> lines) {
        HashMap<Double, List<Line>> multipleColumnLines = new HashMap<>();
        for (Line line :
                lines) {
            double lineX = Math.round(line.getLL().getX() * 100.0) / 100.0;
//            System.out.println("line.x: " + lineX);
//            System.out.println("line: " + line.getText());

            if (!multipleColumnLines.containsKey(lineX)) {
                List<Line> oneColumnLines = new ArrayList<>();
                multipleColumnLines.put(lineX, oneColumnLines);
            }
            multipleColumnLines.get(lineX).add(line);
        }

        return multipleColumnLines;
    }

    public static void removeEmptyLines(List<Line> lines) {
        List<Line> toRemove = new ArrayList<>();

        try {
            for (Line line :
                    lines) {
                byte[] bytes = line.getText().getBytes("US-ASCII");

                // [63] -> ascii value of empty string
                if (Arrays.toString(bytes).equals("[63]")) {
                    toRemove.add(line);
                }
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        lines.removeAll(toRemove);
    }

    public static <T extends MyItem> void showPdfStructure(PdfDocument pdfDoc, List<T> items) {
        Color rectColor = ColorConstants.MAGENTA;

        if (items.get(0).getClass().getSimpleName().equals("Line")) {
            rectColor = ColorConstants.RED;
        } else if (items.get(0).getClass().getSimpleName().equals("Structure")) {
            rectColor = ColorConstants.BLACK;
        }

        for (MyItem item : items) {
            if (!item.getText().equals(" ")) {
                PdfDrawService.drawRectangleOnPdf(pdfDoc, item.getDrawableRectangle(), rectColor);
            }
        }
    }

    public List<MyItem> getContentItems(PdfDocument pdfDocument) throws IOException {
        CustomITextExtractionStrategy customStrategy = new CustomITextExtractionStrategy(pdfDocument);
        // Extract text content by CustomITextExtractionStrategy class
        PdfTextExtractor.getTextFromPage(pdfDocument.getFirstPage(), customStrategy);
        return customStrategy.getItems();
    }

    public List<Line> getLines(List<MyItem> items) {
        List<Line> lines = new ArrayList<>();
        List<MyItem> line = new ArrayList<>();
        for (MyItem item : items) {
            if (line.isEmpty()) {
                line.add(item);
                continue;
            }
            if (areOnSameLine(line.get(line.size() - 1), item)) {
                line.add(item);
            } else {
                lines.add(new Line(line));
                line = new ArrayList<>();
                line.add(item);
            }
        }
        if (!line.isEmpty())
            lines.add(new Line(line));
        return lines;
    }

    public List<Structure> getStructures(List<Line> lines, Double maxYTolerance) {
        List<Structure> structures = new ArrayList<>();
        List<MyItem> structure = new ArrayList<>();
        for (Line line : lines) {
            if (structure.isEmpty()) {
                structure.add(line);
                continue;
            }
            if (areInSameStructure((Line) structure.get(structure.size() - 1), line, maxYTolerance)) {
                structure.add(line);
            } else {
                structures.add(new Structure(structure));
                structure = new ArrayList<>();
                structure.add(line);
            }
        }
        if (!structure.isEmpty())
            structures.add(new Structure(structure));
        return structures;
    }

    static boolean areOnSameLine(MyItem i1, MyItem i2) {

        DecimalFormat f = new DecimalFormat("##.00");

//        System.out.println("i1.text:" + i1.getText() + "; x:" + f.format(i1.getLL().getX()) + "; y:" + f.format(i1.getLL().getY()));
//        System.out.println("i2.text:" + i2.getText() + "; x:" + f.format(i2.getLL().getX()) + "; y:" + f.format(i2.getLL().getY()));

        return ((Math.abs(i1.getLL().getY() - i2.getLL().getY()) <= MyItem.itemPositionTolerance)
                && (Math.abs(i1.getLL().getX() - i2.getLL().getX()) <= 40f));


//        return (Math.abs(i1.getLL().getY() - i2.getLL().getY()) <= MyItem.itemPositionTolerance);
    }

    static boolean areInSameStructure(Line i1, Line i2, Double maxYTolerance) {
        float i2Bottom = i2.getRealRectangle().getBottom();
        float i1Bottom = i1.getRealRectangle().getBottom();

//        System.out.println("line1:" + i1.getText());
//        System.out.println("line2:" + i2.getText());
//
//        System.out.println("i1Bottom:" + i1Bottom + ", i2Bottom:" + i2Bottom);

        if ((i1Bottom - i2Bottom >= maxYTolerance)) {
            return false;
        }

        return true;
    }

    public static void downloadPdfFontsFromGoogleWebFontsApi() throws IOException {
        System.out.print("downloadPdfFontsFromGoogleWebFontsApi(): ");
        // Creating fonts from google's webfonts api
        if (webFontService == null) {
            try {
                webFontService = new GoogleWebFontService();

                // List of fontObjects that pdf contains
                List<PdfObject> fontObjects = PdfFontService.getFontsList(EnvVariable.SRC_RESUME);

                // Downloaded fonts
                List<PdfFont> downloadedFonts = new ArrayList<>();
//				int countDownloadedFonts = 0;
                // Loop through fontObjects and download every font from google web font api
                for (PdfObject fontObj : fontObjects) {
                    PdfDictionary fontDict = (PdfDictionary) fontObj;
                    // Split originial pdf font name to be able to download from google api
                    // Google font names are like: fontFamily: Roboto Condensed, fontVariant:
                    // regular
                    HashMap<String, String> font = ParseService.splitFontName(fontDict.get(PdfName.BaseFont).toString());
                    // Download font and create PdfFont object
                    PdfFont pdfFont = webFontService.downloadFontByFamily(font.get("Family"), font.get("Variant"));
                    if (pdfFont != null) {
                        downloadedFonts.add(pdfFont);
//						countDownloadedFonts++;
                    }
                }

                System.out.println(downloadedFonts.size() + " fonts were downloaded successfully.");
            } catch (Exception ex) {
//				ex.printStackTrace();
                if (ex.getMessage().contains("No subject alternative DNS name matching")) {
                    System.out.println("Check your internet connection !");
                }
            }

        }

    }
}
